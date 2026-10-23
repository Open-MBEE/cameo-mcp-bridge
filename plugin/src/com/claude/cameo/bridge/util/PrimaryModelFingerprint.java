package com.claude.cameo.bridge.util;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import org.eclipse.emf.ecore.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Primary-project model consistency, shared by exports and guarded changes. */
public final class PrimaryModelFingerprint {
    public static String capture(Project project, String phase) throws Exception {
        long started=System.nanoTime();
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        var primary=project.getPrimaryProject();
        if(primary==null) throw new IllegalArgumentException("Cannot establish primary-project ownership for native snapshot");
        PrimaryModelTraversal.Result<EObject> source;
        Map<String,Integer> types=new TreeMap<>();
        try {
            source=PrimaryModelTraversal.collect((EObject)project.getPrimaryModel(), object->{
                var owner=ProjectUtilities.getProject(object);
                if(owner==null) {
                    BridgeDiagnostics.log("model.snapshot phase="+phase+" unknownOwnerType="+object.eClass().getName());
                    return PrimaryModelTraversal.Ownership.UNKNOWN;
                }
                if(owner==primary) {
                    types.merge(object.eClass().getName(),1,Integer::sum);
                    return PrimaryModelTraversal.Ownership.PRIMARY;
                }
                return PrimaryModelTraversal.Ownership.ATTACHED;
            }, object->object.eContents().iterator(),5000,50000,5_000_000_000L);
        } catch(IllegalArgumentException e) {
            BridgeDiagnostics.log("model.snapshot phase="+phase+" refused="+e.getMessage()+" types="+types);
            throw e;
        }
        if(source.objects().isEmpty()) throw new IllegalArgumentException("Primary model is not owned by the primary project; export refused");
        int values=0;
        for(EObject object:source.objects()) {
            if(System.nanoTime()-started>5_000_000_000L)
                throw new IllegalArgumentException("Native snapshot fingerprint exceeded five seconds");
            hash.update(object.eClass().getName().getBytes(StandardCharsets.UTF_8));
            if(object instanceof Element) hash.update(((Element)object).getID().getBytes(StandardCharsets.UTF_8));
            for(EStructuralFeature f:object.eClass().getEAllStructuralFeatures()) {
                if(f.isDerived() || f.isTransient() || !object.eIsSet(f)) continue;
                hash.update(f.getName().getBytes(StandardCharsets.UTF_8));
                Object value=object.eGet(f,false);
                for(Object v:value instanceof Collection<?>?(Collection<?>)value:Collections.singletonList(value)) {
                    if(++values>100000 || System.nanoTime()-started>5_000_000_000L)
                        throw new IllegalArgumentException("Native snapshot source fingerprint exceeded value/time budget");
                    String token=v instanceof Element?((Element)v).getID():String.valueOf(v);
                    if(token.length()>1024*1024) throw new IllegalArgumentException("Native snapshot source fingerprint value exceeds 1 MiB");
                    hash.update((token.length()+":"+token).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        BridgeDiagnostics.log("model.snapshot phase="+phase+" primary="+source.objects().size()
                +" attachedBoundaries="+source.attached().size()+" encounters="+source.encounters()
                +" repeated="+source.repeated()+" values="+values+" types="+types);
        return Hex.format(hash.digest());
    }
}

package com.claude.cameo.bridge.handlers;

import com.claude.cameo.bridge.HttpBridgeServer;
import com.claude.cameo.bridge.util.*;
import com.google.gson.*;
import com.nomagic.magicdraw.core.*;
import com.nomagic.magicdraw.openapi.uml.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.*;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.sun.net.httpserver.*;
import org.eclipse.emf.ecore.EObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Additive creation only. Each approved plan owns one session; saving is separate. */
public final class CreationPlanHandler implements HttpHandler {
    private final CameoCreationPlans plans=new CameoCreationPlans(Path.of(System.getProperty("user.home"),".cameo-mcp-bridge","creation-plans"));
    @Override public void handle(HttpExchange exchange) throws java.io.IOException {
        if(!exchange.getRequestMethod().equals("POST")) { HttpBridgeServer.sendError(exchange,405,"METHOD_NOT_ALLOWED","Use POST"); return; }
        try {
            byte[] bytes=exchange.getRequestBody().readNBytes(65537);
            if(bytes.length>65536) throw new IllegalArgumentException("Creation request exceeds 64 KiB");
            JsonObject request=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            String route=exchange.getRequestURI().getPath(); JsonObject result;
            if(route.equals("/api/v1/creation/prepare")) {
                JsonObject spec=CameoCreationPlans.specification(request);
                result=EdtDispatcher.readOnEdt("Preview Cameo creation",p->plans.prepare(spec,new NativeBackend(p)),30);
            } else {
                boolean apply=route.equals("/api/v1/creation/apply");
                if(!apply && !Set.of("/api/v1/creation/read","/api/v1/creation/receipt").contains(route)) throw new IllegalArgumentException("Unknown creation route");
                if(apply) CameoCreationPlans.keys(request,"schemaVersion","planId","planHash","allowWrite");
                else CameoCreationPlans.keys(request,"schemaVersion","planId","planHash");
                if(!request.has("schemaVersion") || !request.get("schemaVersion").toString().equals("1")) throw new IllegalArgumentException("Creation schema 1 required");
                String id=CameoCreationPlans.text(request,"planId"),hash=CameoCreationPlans.text(request,"planHash");
                if(apply) {
                    if(!request.has("allowWrite") || !request.get("allowWrite").toString().equals("true")) throw new IllegalArgumentException("Explicit reviewed write authorization required");
                    result=EdtDispatcher.readOnEdt("Apply approved Cameo creation",p->plans.apply(id,hash,new NativeBackend(p)),30);
                } else result=route.endsWith("/receipt")?plans.receipt(id,hash):plans.read(id,hash);
            }
            BridgeDiagnostics.log("creation.plan route="+route+" status="+(result.has("status")?result.get("status").getAsString():"prepared"));
            HttpBridgeServer.sendJson(exchange,200,result);
        } catch(Exception error) {
            Throwable cause=error; while(cause.getCause()!=null) cause=cause.getCause();
            HttpBridgeServer.sendError(exchange,409,"CREATION_REFUSED",String.valueOf(cause.getMessage()));
        }
    }
    private static final class NativeBackend implements CameoCreationPlans.Backend {
        private final Project project;
        private Package target;
        private Stereotype block;
        NativeBackend(Project p) { project=p; }
        private Element element(String id) {
            Object result=project.getElementByID(id);
            if(!(result instanceof Element e)) throw new IllegalArgumentException("Creation reference not found: "+id);
            return e;
        }
        private void primary(Element e) {
            if(ProjectUtilities.getProject((EObject)e)!=project.getPrimaryProject()) throw new IllegalArgumentException("Creation references must belong to the primary project");
        }
        private boolean inside(Element e) {
            for(int i=0;e!=null && i<128;i++,e=e.getOwner()) if(e==target) return true;
            return false;
        }
        private Stereotype block() {
            Stereotype result=null; int count=0;
            for(Stereotype candidate:StereotypesHelper.getAllStereotypes(project)) {
                if(++count>100000) throw new IllegalArgumentException("Stereotype lookup exceeds planning work bound");
                if("SysML::Blocks::Block".equals(candidate.getQualifiedName())) {
                    if(result!=null) throw new IllegalArgumentException("Ambiguous SysML::Blocks::Block definition");
                    result=candidate;
                }
            }
            if(result==null) throw new IllegalArgumentException("SysML::Blocks::Block definition is required");
            return result;
        }
        @Override public JsonObject inspect(JsonObject spec) throws Exception {
            if(Application.getInstance().getProject()!=project || !ProjectIdentity.token(project).equals(CameoCreationPlans.text(spec,"projectId"))) throw new IllegalStateException("Active project changed");
            if(project.isRemote()) throw new IllegalArgumentException("Creation MVP supports local projects only");
            if(SessionManager.getInstance().isSessionCreated(project)) throw new IllegalStateException("Existing Cameo editing session must finish first");
            Element selected=element(CameoCreationPlans.text(spec,"targetPackageId"));
            if(!(selected instanceof Package p) || selected instanceof Profile) throw new IllegalArgumentException("Choose an existing ordinary target package");
            primary(selected); target=p;
            if(!target.isEditable()) throw new IllegalArgumentException("Target package is read-only");
            String fingerprint=PrimaryModelFingerprint.capture(project,"creation-preview");
            for(JsonElement value:spec.getAsJsonArray("operations")) {
                JsonObject op=value.getAsJsonObject(); String kind=op.get("kind").getAsString(),owner=op.get("owner").getAsString();
                if(!owner.startsWith("@")) {
                    Element parent=owner.equals("$target")?target:element(owner); primary(parent);
                    if(!inside(parent)) throw new IllegalArgumentException("Creation owner is outside the selected package");
                    if(!parent.isEditable()) throw new IllegalArgumentException("Creation owner is read-only");
                    if(kind.equals("property")) {
                        if(!(parent instanceof com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class) || !StereotypesHelper.hasStereotypeOrDerived(parent,block())) throw new IllegalArgumentException("Properties require a Block owner");
                    } else if(!(parent instanceof Package) || parent instanceof Profile) throw new IllegalArgumentException("Packages and blocks require a package owner");
                    for(Element sibling:parent.getOwnedElement()) if(sibling instanceof NamedElement n && op.get("name").getAsString().equals(n.getName())) throw new IllegalArgumentException("A sibling with this name already exists");
                }
                if(kind.equals("block") || kind.equals("property")) block=block();
                if(kind.equals("property") && !op.get("type").getAsString().startsWith("@")) {
                    Element type=element(op.get("type").getAsString()); primary(type);
                    if(!(type instanceof Type)) throw new IllegalArgumentException("Property type is not a UML Type");
                }
            }
            // Do not add/apply profiles as a hidden side effect of structural creation.
            if(block!=null) {
                Package profile=StereotypesHelper.getProfileForStereotype(block); boolean applied=false;
                for(Element e=target;e!=null;e=e.getOwner()) if(e instanceof Package ancestor && StereotypesHelper.getAppliedProfiles(ancestor).contains(profile)) applied=true;
                if(!applied) throw new IllegalArgumentException("Apply the SysML profile to the target package or its ancestor in Cameo first");
            }
            JsonObject context=new JsonObject(); context.addProperty("projectId",ProjectIdentity.token(project)); context.addProperty("projectName",project.getName());
            context.addProperty("targetPackageId",target.getID()); context.addProperty("targetQualifiedName",target.getQualifiedName());
            context.addProperty("fingerprint",fingerprint);
            context.addProperty("sourceWasDirty",project.isDirty());
            if(block!=null) context.addProperty("blockStereotypeId",block.getID());
            return context;
        }
        @Override public void begin() throws Exception {
            if(Application.getInstance().getProject()!=project || SessionManager.getInstance().isSessionCreated(project)) throw new IllegalStateException("Project/session changed before creation");
            SessionManager.getInstance().createSession(project,"Approved structural creation");
        }
        @Override public JsonObject create(JsonObject spec) throws Exception {
            var factory=project.getElementsFactory(); Map<String,Element> created=new LinkedHashMap<>(); JsonObject receipt=new JsonObject();
            for(JsonElement value:spec.getAsJsonArray("operations")) {
                JsonObject op=value.getAsJsonObject(); String kind=op.get("kind").getAsString(),owner=op.get("owner").getAsString();
                Element parent=resolve(owner,created);
                NamedElement added=switch(kind) {
                    case "package" -> factory.createPackageInstance();
                    case "block" -> factory.createClassInstance();
                    case "property" -> factory.createPropertyInstance();
                    default -> throw new IllegalArgumentException("Unknown creation kind");
                };
                added.setName(op.get("name").getAsString()); ModelElementsManager.getInstance().addElement(added,parent);
                if(kind.equals("block")) {
                    if(!StereotypesHelper.canApplyStereotype(added,block)) throw new IllegalStateException("Block stereotype cannot be applied");
                    StereotypesHelper.addStereotype(added,block);
                    if(!StereotypesHelper.hasStereotype(added,block)) throw new IllegalStateException("Block stereotype verification failed");
                }
                if(added instanceof Property property) {
                    Type type=(Type)resolve(op.get("type").getAsString(),created); property.setType(type);
                    var lower=factory.createLiteralIntegerInstance(); lower.setValue(op.get("lower").getAsInt()); property.setLowerValue(lower);
                    var upper=factory.createLiteralUnlimitedNaturalInstance(); upper.setValue(op.get("upper").getAsInt()); property.setUpperValue(upper);
                    property.setAggregation(op.get("aggregation").getAsString().equals("composite")?AggregationKindEnum.COMPOSITE:AggregationKindEnum.NONE);
                    if(property.getType()!=type || property.getLower()!=lower.getValue() || property.getUpper()!=upper.getValue()) throw new IllegalStateException("Property readback failed");
                }
                if(added.getOwner()!=parent || !added.getName().equals(op.get("name").getAsString())) throw new IllegalStateException("Creation readback failed");
                created.put(op.get("id").getAsString(),added);
                JsonObject row=new JsonObject(); row.addProperty("elementId",added.getID()); row.addProperty("qualifiedName",added.getQualifiedName()); row.addProperty("kind",kind); receipt.add(op.get("id").getAsString(),row);
            }
            return receipt;
        }
        private Element resolve(String ref,Map<String,Element> created) {
            return ref.equals("$target")?target:ref.startsWith("@")?Objects.requireNonNull(created.get(ref.substring(1))):element(ref);
        }
        @Override public void commit() { SessionManager.getInstance().closeSession(project); }
        @Override public void rollback() { SessionManager.getInstance().cancelSession(project); }
    }
}

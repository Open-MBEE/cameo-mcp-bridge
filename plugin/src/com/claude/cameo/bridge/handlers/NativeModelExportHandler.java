package com.claude.cameo.bridge.handlers;

import com.claude.cameo.bridge.HttpBridgeServer;
import com.claude.cameo.bridge.util.*;
import com.google.gson.*;
import com.nomagic.magicdraw.core.*;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.emfuml2xmi.v5.EmfUml2XmiPlugin;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.sun.net.httpserver.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Captures native vendor output and source consistency; consumers interpret it. */
public final class NativeModelExportHandler implements HttpHandler {
    private final NativeExportStore store=new NativeExportStore();
    private static void keys(JsonObject value,String... fields) {
        if(!JsonCompat.keys(value).equals(Set.of(fields))) throw new IllegalArgumentException("Unexpected native export fields");
    }
    @Override public void handle(HttpExchange exchange) throws java.io.IOException {
        if(!exchange.getRequestMethod().equals("POST")) { HttpBridgeServer.sendError(exchange,405,"METHOD_NOT_ALLOWED","Use POST"); return; }
        try {
            byte[] bytes=exchange.getRequestBody().readNBytes(65537);
            if(bytes.length>65536) throw new IllegalArgumentException("Export request exceeds 64 KiB");
            JsonObject request=new JsonParser().parse(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!request.has("schemaVersion") || !request.get("schemaVersion").toString().equals("1")) throw new IllegalArgumentException("Native export schema 1 required");
            String route=exchange.getRequestURI().getPath(); JsonObject result;
            if(route.equals("/api/v1/model-exports/open")) {
                keys(request,"schemaVersion","projectId","rootIds");
                String projectId=CameoCreationPlans.text(request,"projectId"); List<String> roots=new ArrayList<>();
                for(JsonElement e:request.getAsJsonArray("rootIds")) {
                    if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank() || e.getAsString().length()>512) throw new IllegalArgumentException("Invalid selected ID");
                    roots.add(e.getAsString());
                }
                if(roots.isEmpty() || roots.size()>20 || new HashSet<>(roots).size()!=roots.size()) throw new IllegalArgumentException("Choose 1 to 20 distinct roots");
                result=EdtDispatcher.readOnEdt("Native model export",p->capture(p,projectId,roots),30);
            } else if(route.equals("/api/v1/model-exports/read")) {
                keys(request,"schemaVersion","exportId","resourceId","offset","limit");
                for(String key:List.of("offset","limit")) if(!request.get(key).toString().matches("[0-9]+")) throw new IllegalArgumentException("Integer resource range required");
                result=store.read(CameoCreationPlans.text(request,"exportId"),CameoCreationPlans.text(request,"resourceId"),request.get("offset").getAsBigInteger().intValueExact(),request.get("limit").getAsBigInteger().intValueExact());
            } else if(route.equals("/api/v1/model-exports/release")) {
                keys(request,"schemaVersion","exportId"); result=store.release(CameoCreationPlans.text(request,"exportId"));
            } else throw new IllegalArgumentException("Unknown native export route");
            HttpBridgeServer.sendJson(exchange,200,result);
        } catch(Exception error) {
            Throwable cause=error; while(cause.getCause()!=null) cause=cause.getCause();
            HttpBridgeServer.sendError(exchange,409,"MODEL_EXPORT_REFUSED",String.valueOf(cause.getMessage()));
        }
    }
    private JsonObject capture(Project project,String projectId,List<String> roots) throws Exception {
        if(Application.getInstance().getProject()!=project || !ProjectIdentity.token(project).equals(projectId)) throw new IllegalStateException("Active project changed");
        if(SessionManager.getInstance().isSessionCreated(project)) throw new IllegalStateException("Finish the open model transaction before native export");
        for(String id:roots) {
            Object value=project.getElementByID(id);
            if(!(value instanceof com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package) && !(value instanceof com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class)) throw new IllegalArgumentException("Select primary-project semantic packages or classes");
            Element e=(Element)value;
            if(ProjectUtilities.getProject(e)!=project.getPrimaryProject()) throw new IllegalArgumentException("Selected element belongs to an attached project");
            while(e!=null && e!=project.getPrimaryModel()) e=e.getOwner();
            if(e==null) throw new IllegalArgumentException("Selected element is outside the primary model");
        }
        String before=PrimaryModelFingerprint.capture(project,"before"); boolean dirty=project.isDirty();
        var exporter=EmfUml2XmiPlugin.getInstance(); if(exporter==null) throw new IllegalStateException("Enable the native Eclipse UML2 v5 exporter");
        Path directory=Files.createTempDirectory("cameo-native-export-");
        try {
            long started=System.nanoTime();
            try { exporter.exportXMI(project,directory.resolve("source.uml").toString()); }
            finally {
                String after=PrimaryModelFingerprint.capture(project,"after");
                if(Application.getInstance().getProject()!=project || !ProjectIdentity.token(project).equals(projectId) || project.isDirty()!=dirty || !before.equals(after) || SessionManager.getInstance().isSessionCreated(project)) throw new IllegalStateException("Project changed during native export");
            }
            if(System.nanoTime()-started>20_000_000_000L) throw new IllegalStateException("Native exporter exceeded 20 second budget");
            Map<String,byte[]> resources=new TreeMap<>(); long total=0;
            try(var paths=Files.walk(directory)) {
                for(Path file:paths.collect(java.util.stream.Collectors.toList())) {
                    if(Files.isSymbolicLink(file)) throw new IllegalArgumentException("Symlink in native output");
                    if(!Files.isRegularFile(file)) continue;
                    if(resources.size()>=124) throw new IllegalArgumentException("Native output resource count exceeds bound");
                    byte[] content; try(var input=Files.newInputStream(file)) { content=input.readNBytes((int)(16*1024*1024-total)+1); }
                    total+=content.length; if(total>16*1024*1024) throw new IllegalArgumentException("Native output exceeds 16 MiB");
                    resources.put("native/"+directory.relativize(file).toString().replace('\\','/'),content);
                }
            }
            resources.putAll(NativeExporterResources.capture());
            JsonObject context=new JsonObject(); context.addProperty("projectId",projectId); context.addProperty("primaryModelId",project.getPrimaryModel().getID());
            context.addProperty("projectName",project.getName()); context.add("rootIds",new Gson().toJsonTree(roots));
            context.addProperty("sourceFingerprint",before); context.addProperty("sourceWasDirty",dirty); context.addProperty("createdAt",Instant.now().toString());
            context.addProperty("pluginVersion",BridgeCapabilities.pluginVersion()); context.addProperty("apiVersion",BridgeCapabilities.API_VERSION);
            context.addProperty("captureScope","native project export and installed exporter resources; requested roots are selection metadata, not a filtered export");
            context.addProperty("fingerprintScope","distinct primary-project contents; attached-project contents not fingerprinted");
            context.addProperty("sourceBaseUri",directory.toUri().toString());
            JsonObject result=store.open(context,resources);
            BridgeDiagnostics.log("model.export captured resources="+resources.size()+" nativeBytes="+total);
            return result;
        } finally {
            try(var paths=Files.walk(directory)) { for(Path file:paths.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) Files.deleteIfExists(file); }
        }
    }
}

package com.claude.cameo.bridge.util;

import com.google.gson.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import static org.junit.Assert.*;

public class CameoCreationPlansTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private JsonObject spec() {
        return new JsonParser().parse("{\"schemaVersion\":1,\"projectId\":\"session\",\"targetPackageId\":\"target\",\"operations\":[{\"id\":\"pump\",\"kind\":\"block\",\"name\":\"Pump\",\"owner\":\"$target\"},{\"id\":\"backup\",\"kind\":\"property\",\"name\":\"backup\",\"owner\":\"@pump\",\"type\":\"@pump\",\"upper\":-1}]}").getAsJsonObject();
    }
    private static class Model implements CameoCreationPlans.Backend {
        int elements,begins,commits,rollbacks; boolean failCreate,failCommit,failRollback,existingSession; String fingerprint="before";
        public JsonObject inspect(JsonObject spec) { if(existingSession) throw new IllegalStateException("Existing user session"); JsonObject c=new JsonObject(); c.addProperty("fingerprint",fingerprint); return c; }
        public void begin() { begins++; }
        public JsonObject create(JsonObject spec) { elements++; if(failCreate) throw new IllegalStateException("second operation failed"); elements++; JsonObject ids=new JsonObject(); ids.addProperty("pump","real-ID"); return ids; }
        public void commit() { commits++; if(failCommit) throw new IllegalStateException("commit uncertain"); }
        public void rollback() { rollbacks++; if(failRollback) throw new IllegalStateException("rollback uncertain"); elements=0; }
    }
    private String id(JsonObject p) { return p.get("planId").getAsString(); }
    private String hash(JsonObject p) { return p.get("planHash").getAsString(); }
    @Test public void previewDoesNotMutateAndCommitIsAtMostOnceAcrossRestart() throws Exception {
        Path path=folder.newFolder().toPath(); var store=new CameoCreationPlans(path); var model=new Model();
        var plan=store.prepare(spec(),model); assertEquals(0,model.elements); assertEquals(0,model.begins);
        assertEquals("prepared",store.receipt(id(plan),hash(plan)).get("status").getAsString());
        assertEquals("committed",store.apply(id(plan),hash(plan),model).get("status").getAsString());
        assertEquals("committed",new CameoCreationPlans(path).apply(id(plan),hash(plan),model).get("status").getAsString());
        assertEquals(1,model.begins); assertEquals(1,model.commits); assertEquals(2,model.elements);
    }
    @Test public void stalePlanRefusesBeforeSession() throws Exception {
        var store=new CameoCreationPlans(folder.newFolder().toPath()); var model=new Model(); var plan=store.prepare(spec(),model);
        model.fingerprint="changed";
        assertThrows(IllegalStateException.class,()->store.apply(id(plan),hash(plan),model)); assertEquals(0,model.begins);
    }
    @Test public void existingUserSessionRemainsUntouched() throws Exception {
        var store=new CameoCreationPlans(folder.newFolder().toPath()); var model=new Model(); var plan=store.prepare(spec(),model); model.existingSession=true;
        assertThrows(IllegalStateException.class,()->store.apply(id(plan),hash(plan),model)); assertEquals(0,model.begins); assertEquals(0,model.rollbacks);
    }
    @Test public void failedBatchRollsBackAndCannotBeReplayed() throws Exception {
        var store=new CameoCreationPlans(folder.newFolder().toPath()); var model=new Model(); var plan=store.prepare(spec(),model); model.failCreate=true;
        assertEquals("rolledBack",store.apply(id(plan),hash(plan),model).get("status").getAsString());
        store.apply(id(plan),hash(plan),model); assertEquals(1,model.begins); assertEquals(1,model.rollbacks); assertEquals(0,model.commits); assertEquals(0,model.elements);
    }
    @Test public void uncertainCommitOrRollbackNeverReplays() throws Exception {
        for(boolean commit:new boolean[]{true,false}) {
            Path path=folder.newFolder().toPath(); var store=new CameoCreationPlans(path); var model=new Model(); var plan=store.prepare(spec(),model);
            model.failCommit=commit; model.failCreate=!commit; model.failRollback=!commit;
            assertEquals("outcomeUnknown",store.apply(id(plan),hash(plan),model).get("status").getAsString());
            new CameoCreationPlans(path).apply(id(plan),hash(plan),model); assertEquals(1,model.begins);
        }
    }
    @Test public void crashedApplyingClaimNeverMutatesOnRestart() throws Exception {
        Path path=folder.newFolder().toPath(); var store=new CameoCreationPlans(path); var model=new Model(); var plan=store.prepare(spec(),model);
        Files.writeString(path.resolve(id(plan)+".receipt.json"),"{\"status\":\"outcomeUnknown\"}");
        assertEquals("outcomeUnknown",new CameoCreationPlans(path).apply(id(plan),hash(plan),model).get("status").getAsString()); assertEquals(0,model.begins);
    }
    @Test public void hashAndPathTamperingRefuse() throws Exception {
        Path path=folder.newFolder().toPath(); var store=new CameoCreationPlans(path); var model=new Model(); var plan=store.prepare(spec(),model);
        assertThrows(IllegalArgumentException.class,()->store.read("../escape",hash(plan)));
        assertThrows(IllegalArgumentException.class,()->store.apply(id(plan),"0".repeat(64),model));
        plan.getAsJsonObject("specification").addProperty("targetPackageId","other"); Files.writeString(path.resolve(id(plan)+".plan.json"),plan.toString());
        assertThrows(IllegalArgumentException.class,()->store.apply(id(plan),hash(plan),model)); assertEquals(0,model.begins);
    }
    @Test public void expiredPlanRefusesBeforeOpeningSession() throws Exception {
        Path path=folder.newFolder().toPath(); var store=new CameoCreationPlans(path); var model=new Model(); var plan=store.prepare(spec(),model);
        plan.remove("planHash"); plan.addProperty("expiresAt","2000-01-01T00:00:00Z"); plan.addProperty("planHash",CameoCreationPlans.hash(plan));
        Files.writeString(path.resolve(id(plan)+".plan.json"),plan.toString());
        assertThrows(IllegalStateException.class,()->store.apply(id(plan),hash(plan),model)); assertEquals(0,model.begins);
    }
    @Test public void targetAliasesCannotCreateDuplicateSiblings() {
        var raw=spec(); var duplicate=JsonCompat.copy(raw.getAsJsonArray("operations").get(0)).getAsJsonObject();
        duplicate.addProperty("id","different"); duplicate.addProperty("owner","target"); raw.getAsJsonArray("operations").add(duplicate);
        assertThrows(IllegalArgumentException.class,()->CameoCreationPlans.specification(raw));
    }
    @Test public void typedOperationsRejectForwardReferencesUnknownFieldsAndBadMultiplicity() {
        var raw=spec(); assertEquals(1,CameoCreationPlans.specification(raw).getAsJsonArray("operations").get(1).getAsJsonObject().get("lower").getAsInt());
        raw.getAsJsonArray("operations").get(0).getAsJsonObject().addProperty("script","arbitrary"); assertThrows(IllegalArgumentException.class,()->CameoCreationPlans.specification(raw));
        var forward=spec(); forward.getAsJsonArray("operations").get(0).getAsJsonObject().addProperty("owner","@later"); assertThrows(IllegalArgumentException.class,()->CameoCreationPlans.specification(forward));
        var overflow=spec(); overflow.getAsJsonArray("operations").get(1).getAsJsonObject().addProperty("lower",Long.MAX_VALUE); assertThrows(ArithmeticException.class,()->CameoCreationPlans.specification(overflow));
    }
}

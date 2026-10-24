package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import com.claude.cameo.bridge.util.BridgeCapabilities;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class BrowserBridgeTest {
    static final String ORIGIN = "https://client.example", ID = "12345678-1234-1234-1234-123456789abc", HASH = "a".repeat(64);
    static JsonObject object(String json) { return new JsonParser().parse(json).getAsJsonObject(); }
    static JsonObject reference() { JsonObject value = new JsonObject(); value.addProperty("planId", ID); value.addProperty("planHash", HASH); return value; }
    static JsonObject request(String op, JsonObject args) { JsonObject value = new JsonObject(); value.addProperty("protocol", 1); value.addProperty("operation", op); value.add("arguments", args); return value; }
    static class Fake implements BrowserBridge.Backend {
        String project = "project-one"; int writes, releases; Runnable onExport;
        public JsonObject call(String op, JsonObject args) {
            switch (op) {
                case "status": JsonObject status = new JsonObject(); status.addProperty("pluginVersion", BridgeCapabilities.pluginVersion()); return status;
                case "project": JsonObject identity = new JsonObject(); identity.addProperty("projectId", project); return identity;
                case "readCreation": JsonObject plan = reference(); JsonObject context = new JsonObject(); context.addProperty("projectId", project); plan.add("context", context); return plan;
                case "export": if (onExport != null) onExport.run(); return object("{\"exportId\":\"export-one\"}");
                case "releaseExport": releases++; return new JsonObject();
                default: return object("{\"activeDiagram\":{\"name\":\"Open diagram\"},\"selectedPresentations\":[]}");
            }
        }
        public JsonObject apply(JsonObject args) { writes++; return object("{\"status\":\"committed\",\"saved\":false}"); }
    }
    private Fake backend; private BrowserBridge bridge; private AtomicLong now;
    @Before public void setup() { backend = new Fake(); now = new AtomicLong(1); bridge = new BrowserBridge(Set.of(ORIGIN), backend, (origin, plan) -> true, now::get); }
    @After public void close() { bridge.close(); }
    String pair() throws Exception {
        JsonObject initial = bridge.pair(ORIGIN, new JsonObject()); JsonObject ref = new JsonObject(); ref.add("pairingId", initial.get("pairingId"));
        for (int i = 0; i < 100; i++) { JsonObject result = bridge.pair(ORIGIN, ref); if (result.has("token")) return result.get("token").getAsString(); Thread.sleep(10); }
        throw new AssertionError("Pairing not approved");
    }
    JsonObject call(String token, String operation, JsonObject args) throws Exception { return bridge.request(ORIGIN, token, request(operation, args)); }
    JsonObject call(String token, String operation) throws Exception { return call(token, operation, new JsonObject()); }
    JsonObject waitReview(String token, JsonObject started) throws Exception {
        JsonObject ref = new JsonObject(); ref.add("approvalId", started.get("approvalId"));
        for (int i = 0; i < 100; i++) { JsonObject value = bridge.review(ORIGIN, token, ref, false); if (!value.get("state").getAsString().equals("pending")) return value; Thread.sleep(10); }
        throw new AssertionError("Review not completed");
    }
    @Test public void originsAreCanonicalAndExplicit() {
        assertEquals("https://internal.example:8443", BrowserProtocol.origin("https://internal.example:8443"));
        assertEquals("http://localhost:5173", BrowserProtocol.origin("http://localhost:5173"));
        for (String origin : List.of("http://internal.example", "https://client.example/", "https://client.example/path", "https://user@client.example", "null", "https://CLIENT.example", "https://client.example:443")) assertThrows(IllegalArgumentException.class, () -> BrowserProtocol.origin(origin));
    }
    @Test public void foreignOriginAndUnpairedRequestsNeverReadModel() {
        assertThrows(IllegalArgumentException.class, () -> bridge.pair("https://evil.example", new JsonObject()));
        assertThrows(IllegalStateException.class, () -> call("unknown", "project"));
    }
    @Test public void pairingRequiresNativeApprovalAndClaimIsSingleUse() throws Exception {
        bridge.close(); CountDownLatch release = new CountDownLatch(1);
        bridge = new BrowserBridge(Set.of(ORIGIN), backend, (origin, plan) -> { release.await(); return true; }, now::get);
        JsonObject initial = bridge.pair(ORIGIN, new JsonObject()); JsonObject ref = new JsonObject(); ref.add("pairingId", initial.get("pairingId"));
        assertFalse(bridge.pair(ORIGIN, ref).has("token")); release.countDown();
        JsonObject result = null; for (int i = 0; i < 100; i++) { result = bridge.pair(ORIGIN, ref); if (result.has("token")) break; Thread.sleep(10); }
        assertTrue(result.has("token")); assertThrows(IllegalStateException.class, () -> bridge.pair(ORIGIN, ref));
    }
    @Test public void selectedSymbolsDoNotHideOpenDiagram() throws Exception {
        String token = pair(); call(token, "project"); JsonObject selection = call(token, "selection");
        assertEquals("Open diagram", selection.getAsJsonObject("activeDiagram").get("name").getAsString()); assertEquals(0, selection.getAsJsonArray("selectedPresentations").size());
    }
    @Test public void rejectsRawRoutesMacrosAndBrowserWriteAuthorization() throws Exception {
        String token = pair(); call(token, "project");
        assertThrows(IllegalArgumentException.class, () -> call(token, "macro"));
        assertThrows(IllegalArgumentException.class, () -> call(token, "read", object("{\"elementId\":\"../save\"}")));
        JsonObject ref = reference(); ref.addProperty("allowWrite", true); assertThrows(IllegalArgumentException.class, () -> call(token, "applyCreation", ref)); assertEquals(0, backend.writes);
    }
    @Test public void exactNativeApprovalAppliesOnceAndNeverSaves() throws Exception {
        String token = pair(); call(token, "project"); JsonObject started = call(token, "applyCreation", reference());
        JsonObject result = waitReview(token, started); assertEquals("complete", result.get("state").getAsString()); assertFalse(result.getAsJsonObject("result").get("saved").getAsBoolean());
        call(token, "applyCreation", reference()); assertEquals(1, backend.writes);
    }
    @Test public void cancelPendingNativeReviewCannotDispatchLater() throws Exception {
        bridge.close(); CountDownLatch waiting = new CountDownLatch(1), release = new CountDownLatch(1);
        bridge = new BrowserBridge(Set.of(ORIGIN), backend, (origin, plan) -> { if (plan != null) { waiting.countDown(); release.await(); } return true; }, now::get);
        String token = pair(); call(token, "project"); JsonObject started = call(token, "applyCreation", reference()); assertTrue(waiting.await(2, TimeUnit.SECONDS));
        JsonObject ref = new JsonObject(); ref.add("approvalId", started.get("approvalId")); assertEquals("declined", bridge.review(ORIGIN, token, ref, true).get("state").getAsString()); release.countDown();
        Thread.sleep(30); assertEquals(0, backend.writes);
    }
    @Test public void projectSwitchAndRevocationReleaseOnlyOwnedCaptures() throws Exception {
        String token = pair(); call(token, "project"); call(token, "export", object("{\"projectId\":\"project-one\",\"rootIds\":[\"root\"]}"));
        String other = pair(); call(other, "project"); assertThrows(IllegalArgumentException.class, () -> call(other, "releaseExport", object("{\"exportId\":\"export-one\"}")));
        backend.project = "project-two"; assertThrows(IllegalStateException.class, () -> call(token, "selection")); assertEquals(1, backend.releases);
        bridge.revoke(ORIGIN, token); assertThrows(IllegalStateException.class, () -> call(token, "project"));
    }
    @Test public void expiredCaptureIsReleasedBeforeDataReturns() throws Exception {
        String token = pair(); call(token, "project"); backend.onExport = () -> now.addAndGet(900001);
        assertThrows(IllegalStateException.class, () -> call(token, "export", object("{\"projectId\":\"project-one\",\"rootIds\":[\"root\"]}"))); assertEquals(1, backend.releases);
    }
    @Test public void staleReviewAfterProjectSwitchNeverWrites() throws Exception {
        bridge.close(); CountDownLatch waiting = new CountDownLatch(1), release = new CountDownLatch(1);
        bridge = new BrowserBridge(Set.of(ORIGIN), backend, (origin, plan) -> { if (plan != null) { waiting.countDown(); release.await(); } return true; }, now::get);
        String token = pair(); call(token, "project"); call(token, "applyCreation", reference()); assertTrue(waiting.await(2, TimeUnit.SECONDS));
        backend.project = "project-two"; call(token, "project"); release.countDown(); Thread.sleep(30); assertEquals(0, backend.writes);
    }
    @Test public void cancelledPairingCannotGrantAfterNativeApproval() throws Exception {
        bridge.close(); CountDownLatch waiting = new CountDownLatch(1), release = new CountDownLatch(1);
        bridge = new BrowserBridge(Set.of(ORIGIN), backend, (origin, plan) -> { waiting.countDown(); release.await(); return true; }, now::get);
        JsonObject initial = bridge.pair(ORIGIN, new JsonObject()); assertTrue(waiting.await(2, TimeUnit.SECONDS));
        JsonObject ref = new JsonObject(); ref.add("pairingId", initial.get("pairingId")); bridge.cancelPair(ORIGIN, ref); release.countDown();
        assertThrows(IllegalStateException.class, () -> bridge.pair(ORIGIN, ref));
    }
    @Test public void uncertainDispatchedApplyIsNotRetried() throws Exception {
        bridge.close(); Fake uncertain = new Fake() {
            @Override public JsonObject apply(JsonObject reference) { writes++; throw new IllegalStateException("Uncertain native response"); }
        };
        bridge = new BrowserBridge(Set.of(ORIGIN), uncertain, (origin, plan) -> true, now::get);
        String token = pair(); call(token, "project"); JsonObject started = call(token, "applyCreation", reference());
        assertEquals("outcomeUnknown", waitReview(token, started).get("state").getAsString());
        call(token, "applyCreation", reference()); assertEquals(1, uncertain.writes);
    }
    @Test public void nativeDialogCancellationProbeNeverWaitsOnRequestMonitor() throws Exception {
        bridge.close(); CountDownLatch ready = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicReference<java.util.function.BooleanSupplier> probe = new AtomicReference<>();
        bridge = new BrowserBridge(Set.of(ORIGIN), backend, new BrowserBridge.Approval() {
            public boolean confirm(String origin, JsonObject plan) { return true; }
            public boolean confirm(String origin, JsonObject plan, java.util.function.BooleanSupplier pending) throws Exception {
                if (plan == null) return true;
                probe.set(pending); ready.countDown(); finish.await(); return false;
            }
        }, now::get);
        String token = pair(); call(token, "project"); call(token, "applyCreation", reference()); assertTrue(ready.await(2, TimeUnit.SECONDS));
        CountDownLatch held = new CountDownLatch(1), unlock = new CountDownLatch(1);
        Thread request = new Thread(() -> { synchronized (bridge) { held.countDown(); try { unlock.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } } });
        request.setDaemon(true); request.start(); assertTrue(held.await(2, TimeUnit.SECONDS));
        try {
            assertTrue(CompletableFuture.supplyAsync(() -> probe.get().getAsBoolean()).get(1, TimeUnit.SECONDS));
        } finally { unlock.countDown(); finish.countDown(); request.join(2000); }
        assertEquals(0, backend.writes);
    }
}

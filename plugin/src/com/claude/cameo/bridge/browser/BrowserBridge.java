package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import com.claude.cameo.bridge.util.BridgeCapabilities;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Session and approval boundary shared by the HTTP host and synthetic tests. */
public final class BrowserBridge implements AutoCloseable {
    public interface Backend {
        JsonObject call(String operation, JsonObject arguments) throws Exception;
        JsonObject apply(JsonObject arguments) throws Exception;
    }
    public interface Approval {
        boolean confirm(String origin, JsonObject plan) throws Exception;
        default boolean confirm(String origin, JsonObject plan, java.util.function.BooleanSupplier pending) throws Exception { return confirm(origin, plan); }
    }
    private static final class Pairing {
        final String origin; final long expires; volatile String state = "pending"; String token;
        Pairing(String origin, long expires) { this.origin = origin; this.expires = expires; }
    }
    private static final class Session {
        final String origin; final long expires; String project;
        final Set<String> exports = new HashSet<>();
        Session(String origin, long expires) { this.origin = origin; this.expires = expires; }
    }
    private static final class Review {
        final String owner, project; final JsonObject reference, plan; final long expires;
        volatile String state = "pending"; JsonObject result;
        Review(String owner, String project, JsonObject reference, JsonObject plan, long expires) {
            this.owner = owner; this.project = project; this.reference = reference; this.plan = plan; this.expires = expires;
        }
    }
    private final Backend backend;
    private final Approval approval;
    private final LongSupplier clock;
    private final Set<String> origins;
    private final Map<String, Pairing> pairings = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, Review> reviews = new HashMap<>();
    private final ExecutorService dialogs = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "cameo-browser-approval"); t.setDaemon(true); return t; });
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cameo-browser-expiry"); t.setDaemon(true); return t; });
    private final SecureRandom random = new SecureRandom();
    private volatile boolean closed;
    public BrowserBridge(Set<String> origins, Backend backend, Approval approval) {
        this(origins, backend, approval, () -> System.nanoTime() / 1000000);
    }
    public BrowserBridge(Set<String> origins, Backend backend, Approval approval, LongSupplier clock) {
        this.origins = new HashSet<>(); for (String origin : origins) this.origins.add(BrowserProtocol.origin(origin));
        this.backend = backend; this.approval = approval; this.clock = clock;
        cleanup.scheduleWithFixedDelay(this::expire, 15, 15, TimeUnit.SECONDS);
    }
    public synchronized boolean permits(String origin) { return !closed && origins.contains(origin); }
    private String secret() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private void origin(String origin) { if (!permits(origin)) throw new IllegalArgumentException("Origin is not configured in Cameo Environment Options"); }
    public synchronized JsonObject pair(String origin, JsonObject request) {
        origin(origin); expire();
        if (!request.has("pairingId")) {
            BrowserProtocol.exact(request);
            if (pairings.size() >= 4 || sessions.size() >= 4) throw new IllegalStateException("Pairing capacity reached; finish or cancel existing requests");
            String id = secret(); Pairing pair = new Pairing(origin, clock.getAsLong() + 120000); pairings.put(id, pair);
            dialogs.submit(() -> approvePair(id, pair));
            JsonObject result = new JsonObject(); result.addProperty("pairingId", id); result.addProperty("state", "pending"); return result;
        }
        BrowserProtocol.exact(request, "pairingId"); String id = BrowserProtocol.text(request, "pairingId"); Pairing pair = pairings.get(id);
        if (pair == null || !pair.origin.equals(origin) || pair.expires <= clock.getAsLong()) throw new IllegalStateException("Pairing expired");
        JsonObject result = new JsonObject(); result.addProperty("state", pair.state);
        if (pair.token != null) { result.addProperty("protocol", 1); result.addProperty("token", pair.token); result.addProperty("expiresIn", 900); result.addProperty("reviewedCreationAvailable", true); result.addProperty("nativeApproval", true); pairings.remove(id); }
        return result;
    }
    private void approvePair(String id, Pairing pair) {
        try {
            synchronized (this) { if (closed || pairings.get(id) != pair || pair.expires <= clock.getAsLong()) return; }
            // This predicate runs on the Swing EDT. It must never wait for a
            // session monitor held by an HTTP request awaiting the SDK/EDT.
            boolean accepted = approval.confirm(pair.origin, null,
                () -> !closed && pair.state.equals("pending") && pair.expires > clock.getAsLong());
            synchronized (this) {
                if (closed || pairings.get(id) != pair || pair.expires <= clock.getAsLong()) return;
                pair.state = accepted ? "approved" : "declined";
                if (accepted && sessions.size() < 4) { pair.token = secret(); sessions.put(pair.token, new Session(pair.origin, clock.getAsLong() + 900000)); }
            }
        } catch (Exception error) { synchronized (this) { pair.state = "declined"; } }
    }
    public synchronized void cancelPair(String origin, JsonObject request) {
        origin(origin); BrowserProtocol.exact(request, "pairingId"); Pairing pair = pairings.get(BrowserProtocol.text(request, "pairingId"));
        if (pair != null && pair.origin.equals(origin)) { pair.state = "declined"; pairings.remove(BrowserProtocol.text(request, "pairingId")); if (pair.token != null) revoke(origin, pair.token); }
    }
    private synchronized Session session(String origin, String token) {
        origin(origin); Session session = sessions.get(token);
        if (session == null || !session.origin.equals(origin) || session.expires <= clock.getAsLong()) throw new IllegalStateException("Connection expired; connect to Cameo again");
        return session;
    }
    private synchronized void live(String token, Session session) {
        if (closed || sessions.get(token) != session || session.expires <= clock.getAsLong()) throw new IllegalStateException("Connection expired or revoked during request");
    }
    // SDK handlers own their EDT/transaction boundaries. Do not hold a global
    // backend lock while waiting for the EDT: shutdown itself can run on the EDT.
    private JsonObject call(String operation, JsonObject args) throws Exception { return backend.call(operation, args); }
    private String project() throws Exception { return BrowserProtocol.text(call("project", new JsonObject()), "projectId"); }
    public JsonObject request(String origin, String token, JsonObject request) throws Exception {
        BrowserProtocol.exact(request, "protocol", "operation", "arguments");
        if (!request.get("protocol").toString().equals("1")) throw new IllegalArgumentException("Unsupported protocol");
        String operation = BrowserProtocol.text(request, "operation"); JsonObject args = request.getAsJsonObject("arguments"); BrowserProtocol.validate(operation, args);
        Session session = session(origin, token);
        if (operation.equals("status")) {
            JsonObject metadata = call("status", new JsonObject()); live(token, session);
            if (!BridgeCapabilities.pluginVersion().equals(BrowserProtocol.text(metadata, "pluginVersion"))) throw new IllegalStateException("Native bridge version mismatch");
            JsonObject result = new JsonObject(); result.addProperty("protocol", 1); result.addProperty("available", true); result.add("bridge", metadata);
            result.addProperty("transport", "paired-browser-v1"); result.addProperty("nativeApproval", true);
            result.addProperty("reviewedCreationAvailable", true); result.addProperty("writesEnabled", true); result.addProperty("liveExportAvailable", true);
            JsonArray operations = new JsonArray();
            for (String operationName : java.util.List.of("status", "project", "selection", "read", "children", "export", "exportResource", "releaseExport", "prepareCreation", "readCreation", "creationReceipt", "applyCreation")) operations.add(new JsonPrimitive(operationName));
            result.add("operations", operations);
            return result;
        }
        if (operation.equals("releaseExport")) {
            synchronized (this) { if (!session.exports.contains(BrowserProtocol.text(args, "exportId"))) throw new IllegalArgumentException("Export does not belong to this session"); }
            JsonObject result = call(operation, args); synchronized (this) { session.exports.remove(BrowserProtocol.text(args, "exportId")); } live(token, session); return result;
        }
        String identity = project(); live(token, session);
        if (operation.equals("project")) {
            JsonObject result = call("project", args); identity = BrowserProtocol.text(result, "projectId");
            synchronized (this) { live(token, session); if (!Objects.equals(session.project, identity)) { release(session); invalidate(token); } session.project = identity; }
            return result;
        }
        synchronized (this) {
            if (!identity.equals(session.project)) { release(session); invalidate(token); session.project = null; throw new IllegalStateException("Read the current project and select its scope again"); }
            if (operation.equals("exportResource") && !session.exports.contains(BrowserProtocol.text(args, "exportId"))) throw new IllegalArgumentException("Export does not belong to this session");
            if ((operation.equals("export") || operation.equals("prepareCreation")) && !identity.equals(BrowserProtocol.text(args, "projectId"))) throw new IllegalArgumentException("Project scope changed");
        }
        if (operation.equals("applyCreation")) return startReview(token, session, identity, args);
        JsonObject result = call(operation, args);
        if (operation.equals("export")) {
            String export = BrowserProtocol.text(result, "exportId");
            synchronized (this) {
                try { live(token, session); session.exports.add(export); }
                catch (RuntimeException error) { JsonObject handle = new JsonObject(); handle.addProperty("exportId", export); call("releaseExport", handle); throw error; }
            }
        }
        live(token, session); return result;
    }
    private synchronized JsonObject startReview(String token, Session session, String identity, JsonObject args) throws Exception {
        live(token, session);
        for (Map.Entry<String, Review> entry : reviews.entrySet()) if (entry.getValue().owner.equals(token) && entry.getValue().reference.equals(args)) return reviewValue(entry.getKey(), entry.getValue());
        if (reviews.size() >= 128 || reviews.values().stream().filter(r -> r.state.equals("pending")).count() >= 4) throw new IllegalStateException("Review capacity reached");
        JsonObject plan = call("readCreation", args); live(token, session);
        if (!BrowserProtocol.text(plan.getAsJsonObject("context"), "projectId").equals(identity)
                || !BrowserProtocol.text(plan, "planHash").equals(BrowserProtocol.text(args, "planHash"))
                || !BrowserProtocol.text(plan, "planId").equals(BrowserProtocol.text(args, "planId"))) throw new IllegalStateException("Plan identity changed");
        Review review = new Review(token, identity, BrowserProtocol.copy(args), plan, clock.getAsLong() + 120000);
        String id = secret(); reviews.put(id, review); dialogs.submit(() -> approveReview(review, session)); return reviewValue(id, review);
    }
    private void approveReview(Review review, Session session) {
        try {
            synchronized (this) { if (!pending(review, session)) return; }
            boolean approved = approval.confirm(session.origin, BrowserProtocol.copy(review.plan),
                () -> !closed && review.state.equals("pending") && review.expires > clock.getAsLong() && session.expires > clock.getAsLong());
            synchronized (this) { if (!pending(review, session)) return; if (!approved) { review.state = "declined"; return; } }
            String identity = project(); JsonObject current = call("readCreation", review.reference);
            synchronized (this) {
                if (!pending(review, session)) return;
                if (!identity.equals(review.project) || !current.equals(review.plan)) { review.state = "declined"; return; }
                review.state = "applying";
            }
            // Once applying, cancellation reports an uncertain outcome rather than
            // promising rollback. Never hold the session lock while waiting on the EDT.
            try {
                JsonObject result = backend.apply(review.reference);
                synchronized (this) { review.result = result; review.state = "complete"; }
            } catch (Exception error) { synchronized (this) { review.state = "outcomeUnknown"; } }
        } catch (Exception error) { synchronized (this) { if (review.state.equals("pending")) review.state = "declined"; } }
    }
    private synchronized boolean pending(Review review, Session session) {
        if (!review.state.equals("pending")) return false;
        if (closed || sessions.get(review.owner) != session || review.expires <= clock.getAsLong() || session.expires <= clock.getAsLong()) { review.state = "declined"; return false; }
        return true;
    }
    private JsonObject reviewValue(String id, Review review) {
        JsonObject result = new JsonObject(); result.addProperty("approvalRequired", true); result.addProperty("nativeApproval", true);
        result.addProperty("approvalId", id); result.addProperty("state", review.state); if (review.result != null) result.add("result", review.result); return result;
    }
    public synchronized JsonObject review(String origin, String token, JsonObject request, boolean cancel) {
        session(origin, token); BrowserProtocol.exact(request, "approvalId"); String id = BrowserProtocol.text(request, "approvalId"); Review review = reviews.get(id);
        if (review == null || !review.owner.equals(token)) throw new IllegalArgumentException("Review does not belong to this session");
        if (cancel && review.state.equals("pending")) review.state = "declined";
        if (review.state.equals("pending") && review.expires <= clock.getAsLong()) review.state = "declined";
        return reviewValue(id, review);
    }
    private void invalidate(String token) { for (Review review : reviews.values()) if (review.owner.equals(token) && review.state.equals("pending")) review.state = "declined"; }
    private void release(Session session) {
        for (String id : new HashSet<>(session.exports)) {
            JsonObject request = new JsonObject(); request.addProperty("exportId", id);
            try { call("releaseExport", request); } catch (Exception ignored) { /* Native export expiry is the backstop. */ }
        }
        session.exports.clear();
    }
    public synchronized void revoke(String origin, String token) {
        origin(origin); Session session = sessions.get(token);
        if (session != null && session.origin.equals(origin)) { sessions.remove(token); invalidate(token); release(session); }
    }
    public synchronized void expire() {
        long now = clock.getAsLong();
        for (String token : new HashSet<>(sessions.keySet())) { Session session = sessions.get(token); if (session.expires <= now) revoke(session.origin, token); }
        pairings.entrySet().removeIf(e -> e.getValue().expires <= now);
        reviews.entrySet().removeIf(e -> e.getValue().expires + 900000 <= now && !e.getValue().state.equals("applying"));
    }
    @Override public synchronized void close() {
        closed = true; for (Session session : sessions.values()) release(session); sessions.clear(); pairings.clear(); reviews.clear();
        dialogs.shutdownNow(); cleanup.shutdownNow();
    }
}

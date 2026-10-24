package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Dedicated origin-bound port; never exposes the native REST route space. */
public final class BrowserHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final BrowserBridge bridge;
    public BrowserHttpServer(int port, BrowserBridge bridge) throws IOException {
        this.bridge = bridge;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        executor = new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> { Thread t = new Thread(r, "cameo-browser-http"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor); server.createContext("/", this::handle);
    }
    public int port() { return server.getAddress().getPort(); }
    public void start() { server.start(); }
    private void handle(HttpExchange exchange) throws IOException {
        try {
            String expected = "127.0.0.1:" + port();
            if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress() || !expected.equals(exchange.getRequestHeaders().getFirst("Host"))) throw new IllegalArgumentException("Loopback Host required");
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (!bridge.permits(origin)) throw new IllegalArgumentException("Origin is not configured in Cameo Environment Options");
            cors(exchange, origin);
            String path = exchange.getRequestURI().toString();
            if (!java.util.Set.of("/v1/pairNative", "/v1/cancelPair", "/v1/request", "/v1/revoke", "/v1/review", "/v1/cancelReview").contains(path)) throw new IllegalArgumentException("Unsupported browser route");
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                if (!"POST".equals(exchange.getRequestHeaders().getFirst("Access-Control-Request-Method"))) throw new IllegalArgumentException("Only POST is supported");
                String requested = exchange.getRequestHeaders().getFirst("Access-Control-Request-Headers");
                if (requested != null) for (String header : requested.split(",")) if (!java.util.Set.of("content-type", "authorization").contains(header.trim().toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("Unsupported browser header");
                cors(exchange, origin); exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST"); exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                exchange.sendResponseHeaders(204, -1); exchange.close(); return;
            }
            if (!exchange.getRequestMethod().equals("POST") || !"application/json".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) throw new IllegalArgumentException("Use JSON POST");
            byte[] bytes = exchange.getRequestBody().readNBytes(65537);
            if (bytes.length > 65536) throw new IllegalArgumentException("Request exceeds 64 KiB");
            JsonObject request = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject result;
            if (path.equals("/v1/pairNative")) result = bridge.pair(origin, request);
            else if (path.equals("/v1/cancelPair")) { bridge.cancelPair(origin, request); result = new JsonObject(); }
            else {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth == null || !auth.matches("Bearer [A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Connect to Cameo first");
                String token = auth.substring(7);
                if (path.equals("/v1/request")) result = bridge.request(origin, token, request);
                else if (path.equals("/v1/revoke")) { BrowserProtocol.exact(request); bridge.revoke(origin, token); result = new JsonObject(); }
                else result = bridge.review(origin, token, request, path.equals("/v1/cancelReview"));
            }
            cors(exchange, origin); JsonObject response = new JsonObject(); response.addProperty("protocol", 1); response.addProperty("ok", true);
            if (path.equals("/v1/pairNative")) for (java.util.Map.Entry<String, JsonElement> entry : result.entrySet()) response.add(entry.getKey(), entry.getValue());
            else response.add("value", result);
            send(exchange, 200, response);
        } catch (Exception error) {
            JsonObject response = new JsonObject(); response.addProperty("ok", false); response.addProperty("code", "BROWSER_REQUEST_REFUSED");
            response.addProperty("reason", error instanceof IllegalArgumentException || error instanceof IllegalStateException || error instanceof IOException ? error.getMessage() : "Browser request refused");
            send(exchange, 403, response);
        }
    }
    private static void cors(HttpExchange exchange, String origin) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin); exchange.getResponseHeaders().set("Vary", "Origin");
    }
    private static void send(HttpExchange exchange, int status, JsonObject value) throws IOException {
        byte[] body = value.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > 4 * 1024 * 1024) { body = "{\"ok\":false,\"reason\":\"Response exceeds 4 MiB\"}".getBytes(StandardCharsets.UTF_8); status = 413; }
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff"); exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
        exchange.close();
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); bridge.close(); }
}

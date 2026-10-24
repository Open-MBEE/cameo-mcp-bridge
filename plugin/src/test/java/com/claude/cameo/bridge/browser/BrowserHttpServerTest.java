package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import org.junit.*;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BrowserHttpServerTest {
    private BrowserHttpServer server;
    @Before public void start() throws Exception {
        server = new BrowserHttpServer(0, new BrowserBridge(Set.of(BrowserBridgeTest.ORIGIN), new BrowserBridgeTest.Fake(), (origin, plan) -> true)); server.start();
    }
    @After public void stop() { server.close(); }
    private HttpURLConnection request(String route, String method, String origin, String body) throws Exception {
        HttpURLConnection request = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + route).openConnection(Proxy.NO_PROXY);
        request.setRequestMethod(method); request.setConnectTimeout(2000); request.setReadTimeout(2000);
        if (origin != null) request.setRequestProperty("Origin", origin);
        if (method.equals("OPTIONS")) { request.setRequestProperty("Access-Control-Request-Method", "POST"); request.setRequestProperty("Access-Control-Request-Headers", "content-type,authorization"); }
        if (body != null) { request.setDoOutput(true); request.setRequestProperty("Content-Type", "application/json"); try (OutputStream stream = request.getOutputStream()) { stream.write(body.getBytes(StandardCharsets.UTF_8)); } }
        return request;
    }
    @BeforeClass public static void customOriginAllowedByJdkTestClient() { System.setProperty("sun.net.http.allowRestrictedHeaders", "true"); }
    @Test public void exactOriginPreflightHasNoAmbientCredentials() throws Exception {
        HttpURLConnection request = request("/v1/request", "OPTIONS", BrowserBridgeTest.ORIGIN, null);
        assertEquals(204, request.getResponseCode()); assertEquals(BrowserBridgeTest.ORIGIN, request.getHeaderField("Access-Control-Allow-Origin"));
        assertNull(request.getHeaderField("Access-Control-Allow-Credentials")); request.disconnect();
    }
    @Test public void foreignOriginMissingOriginAndRawRoutesAreRefused() throws Exception {
        for (String origin : Arrays.asList("https://evil.example", null)) {
            HttpURLConnection request = request("/v1/pairNative", "POST", origin, "{}"); assertEquals(403, request.getResponseCode()); assertNull(request.getHeaderField("Access-Control-Allow-Origin")); request.disconnect();
        }
        for (String route : List.of("/api/v1/elements", "/v1/request?token=secret", "/review")) {
            HttpURLConnection request = request(route, "POST", BrowserBridgeTest.ORIGIN, "{}"); assertEquals(403, request.getResponseCode()); request.disconnect();
        }
    }
    @Test public void unauthenticatedModelReadsAndOversizedBodiesAreRefused() throws Exception {
        HttpURLConnection request = request("/v1/request", "POST", BrowserBridgeTest.ORIGIN, BrowserBridgeTest.request("project", new JsonObject()).toString());
        assertEquals(403, request.getResponseCode()); request.disconnect();
        request = request("/v1/pairNative", "POST", BrowserBridgeTest.ORIGIN, "{\"ignored\":\"" + "x".repeat(65537) + "\"}"); assertEquals(403, request.getResponseCode()); request.disconnect();
    }
}

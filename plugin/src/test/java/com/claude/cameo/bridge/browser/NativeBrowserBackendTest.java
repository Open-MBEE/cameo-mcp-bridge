package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.junit.*;
import static org.junit.Assert.*;

public class NativeBrowserBackendTest {
    private HttpServer raw;
    private NativeBrowserBackend backend;
    private String route, method, origin; private JsonObject body;
    @Before public void start() throws Exception {
        raw = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        raw.createContext("/", exchange -> {
            route = exchange.getRequestURI().toString(); method = exchange.getRequestMethod(); origin = exchange.getRequestHeaders().getFirst("Origin");
            byte[] bytes = exchange.getRequestBody().readAllBytes(); body = bytes.length == 0 ? null : BrowserBridgeTest.object(new String(bytes, StandardCharsets.UTF_8));
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        raw.start(); backend = new NativeBrowserBackend(raw.getAddress().getPort());
    }
    @After public void stop() { raw.stop(0); }
    @Test public void portableAdapterMapsSelectionAndCaptureWithoutForwardingBrowserHeaders() throws Exception {
        backend.call("selection", new JsonObject()); assertEquals("/api/v1/ui/state", route); assertEquals("GET", method); assertNull(origin);
        backend.call("children", BrowserBridgeTest.object("{\"rootId\":\"root\",\"limit\":3}")); assertTrue(route.contains("rootId=root")); assertTrue(route.contains("limit=3"));
        backend.call("export", BrowserBridgeTest.object("{\"projectId\":\"project-one\",\"rootIds\":[\"root\"]}"));
        assertEquals("/api/v1/model-exports/open", route); assertEquals("POST", method); assertEquals(1, body.get("schemaVersion").getAsInt()); assertFalse(body.has("allowWrite"));
    }
    @Test public void browserCannotChooseWriteGateOrRawRoute() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> backend.call("applyCreation", BrowserBridgeTest.reference()));
        assertThrows(IllegalArgumentException.class, () -> backend.call("http://evil.example", new JsonObject()));
        backend.apply(BrowserBridgeTest.reference()); assertEquals("/api/v1/creation/apply", route); assertTrue(body.get("allowWrite").getAsBoolean()); assertEquals(1, body.get("schemaVersion").getAsInt());
    }
}

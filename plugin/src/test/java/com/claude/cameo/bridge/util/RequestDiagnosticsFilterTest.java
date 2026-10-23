package com.claude.cameo.bridge.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class RequestDiagnosticsFilterTest {
    @Test
    public void correlatesRequestsAndLogsStatusWithoutBodiesQueriesOrElementPaths() throws Exception {
        String previous = System.getProperty("cameo.mcp.diagnostics");
        String previousGui = System.getProperty("cameo.mcp.diagnostics.gui");
        System.setProperty("cameo.mcp.diagnostics", "true");
        System.setProperty("cameo.mcp.diagnostics.gui", "true");
        List<String> records = new CopyOnWriteArrayList<>();
        CountDownLatch ended = new CountDownLatch(2);
        BridgeDiagnostics.setGuiSink(record -> {
            records.add(record);
            if (record.contains(" end ")) ended.countDown();
        });
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ui", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders("GET".equals(exchange.getRequestMethod()) ? 200 : 405, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        }).getFilters().add(new RequestDiagnosticsFilter());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/v1/ui/PRIVATE_ELEMENT?token=PRIVATE_TOKEN");
            HttpResponse<String> first = client.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> second = client.send(HttpRequest.newBuilder(url)
                    .POST(HttpRequest.BodyPublishers.ofString("PRIVATE_BODY")).build(), HttpResponse.BodyHandlers.ofString());
            String firstId = first.headers().firstValue(RequestDiagnosticsFilter.REQUEST_ID_HEADER).orElseThrow();
            String secondId = second.headers().firstValue(RequestDiagnosticsFilter.REQUEST_ID_HEADER).orElseThrow();
            assertNotEquals(firstId, secondId);
            assertTrue(ended.await(5, TimeUnit.SECONDS));
            String log = String.join("\n", records);
            assertTrue(log.contains("request=" + firstId + " end GET /api/v1/ui status=200 elapsedMs="));
            assertTrue(log.contains("request=" + secondId + " end POST /api/v1/ui status=405 elapsedMs="));
            assertFalse(log.contains("PRIVATE_"));
        } finally {
            server.stop(0);
            BridgeDiagnostics.setGuiSink(null);
            restore("cameo.mcp.diagnostics", previous);
            restore("cameo.mcp.diagnostics.gui", previousGui);
        }
    }

    @Test
    public void diagnosticsCanBeDisabledAndSinkFailureDoesNotEscape() {
        String previous = System.getProperty("cameo.mcp.diagnostics");
        String previousGui = System.getProperty("cameo.mcp.diagnostics.gui");
        try {
            System.setProperty("cameo.mcp.diagnostics", "false");
            BridgeDiagnostics.setGuiSink(record -> fail("Disabled diagnostics must not reach the GUI sink"));
            BridgeDiagnostics.log("suppressed");
            System.setProperty("cameo.mcp.diagnostics", "true");
            System.setProperty("cameo.mcp.diagnostics.gui", "true");
            BridgeDiagnostics.setGuiSink(record -> { throw new IllegalStateException("sink unavailable"); });
            BridgeDiagnostics.log("sink failure must not break the caller");
        } finally {
            BridgeDiagnostics.setGuiSink(null);
            restore("cameo.mcp.diagnostics", previous);
            restore("cameo.mcp.diagnostics.gui", previousGui);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}

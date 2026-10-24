package com.claude.cameo.bridge.util;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserRequestFilterTest {
    @Test public void rejectsBrowserReadsPreflightAndSimpleWritesBeforeHandlers() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        var context = server.createContext("/", exchange -> {
            calls.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        context.getFilters().add(new BrowserRequestFilter());
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/creation/apply");
            HttpClient client = HttpClient.newHttpClient();
            for (String method : new String[]{"GET", "POST", "OPTIONS"}) {
                var request = HttpRequest.newBuilder(uri).header("Origin", "https://ide.example")
                        .method(method, HttpRequest.BodyPublishers.noBody()).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(403, response.statusCode());
                assertFalse(response.headers().firstValue("Access-Control-Allow-Origin").isPresent());
            }
            var noCors = HttpRequest.newBuilder(uri).header("Sec-Fetch-Mode", "no-cors").GET().build();
            assertEquals(403, client.send(noCors, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(0, calls.get());
            assertEquals(204, client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }
}

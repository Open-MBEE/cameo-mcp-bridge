package com.claude.cameo.bridge.util;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Browsers use the paired connector; the raw SDK API is native-client only. */
public final class BrowserRequestFilter extends Filter {
    @Override public String description() { return "Reject direct browser access to the native bridge"; }

    @Override public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        boolean browser = exchange.getRequestHeaders().containsKey("Origin");
        for (String name : exchange.getRequestHeaders().keySet()) {
            if (name.regionMatches(true, 0, "Sec-Fetch-", 0, 10)) browser = true;
        }
        if (browser) {
            byte[] body = "{\"error\":\"BROWSER_ACCESS_DENIED\",\"message\":\"Use the paired local connector\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(403, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            return;
        }
        chain.doFilter(exchange);
    }
}

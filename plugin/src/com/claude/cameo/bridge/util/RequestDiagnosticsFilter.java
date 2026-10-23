package com.claude.cameo.bridge.util;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Logs route-level timing/status for every request, without query strings or bodies. */
public final class RequestDiagnosticsFilter extends Filter {
    public static final String REQUEST_ID_HEADER = "X-Cameo-Request-Id";
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Override
    public String description() {
        return "Cameo bridge request correlation and diagnostics";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String requestId = RUN_ID + "-" + SEQUENCE.incrementAndGet();
        exchange.getResponseHeaders().set(REQUEST_ID_HEADER, requestId);
        String operation = exchange.getRequestMethod() + " " + exchange.getHttpContext().getPath();
        long started = System.nanoTime();
        BridgeDiagnostics.log("request=" + requestId + " start " + operation);
        boolean completed = false;
        try {
            chain.doFilter(exchange);
            completed = true;
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            BridgeDiagnostics.log("request=" + requestId + " end " + operation
                    + " status=" + exchange.getResponseCode() + " elapsedMs=" + elapsedMs
                    + " completed=" + completed);
        }
    }
}

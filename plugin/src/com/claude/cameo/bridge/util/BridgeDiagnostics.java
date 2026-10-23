package com.claude.cameo.bridge.util;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Small, content-free diagnostic records, optionally mirrored to Cameo's log. */
public final class BridgeDiagnostics {
    private static final Logger LOG = Logger.getLogger(BridgeDiagnostics.class.getName());
    private static volatile Consumer<String> guiSink;

    private BridgeDiagnostics() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("cameo.mcp.diagnostics", "true"));
    }

    public static void setGuiSink(Consumer<String> sink) {
        guiSink = sink;
    }

    public static void log(String message) {
        if (!enabled()) return;
        String record = "CameoMCPBridge: " + message;
        LOG.info(record);
        Consumer<String> sink = guiSink;
        if (sink != null && Boolean.parseBoolean(System.getProperty("cameo.mcp.diagnostics.gui", "true"))) {
            try {
                sink.accept(record);
            } catch (RuntimeException error) {
                // Diagnostics must never turn a successful operation into a failure.
                LOG.log(Level.FINE, "Could not mirror diagnostics to Cameo GUI log", error);
            }
        }
    }

    public static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getTypeName();
    }
}

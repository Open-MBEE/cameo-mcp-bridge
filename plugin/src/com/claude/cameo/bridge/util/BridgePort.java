package com.claude.cameo.bridge.util;

/** Port validation shared by saved options and the existing VM override. */
public final class BridgePort {
    public static final int DEFAULT = 18740;

    private BridgePort() {}

    public static int resolve(String override, int saved) {
        int port = override == null ? saved : Integer.parseInt(override.trim());
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Listening port must be between 1 and 65535");
        }
        return port;
    }
}

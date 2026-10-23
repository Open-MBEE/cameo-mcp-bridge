package com.claude.cameo.bridge.util;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

/** Build identity packaged with each target, independent of VM overrides. */
public final class BridgePlatform {
    private static final Properties BUILD = load();
    private BridgePlatform() {}
    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream stream = BridgePlatform.class.getResourceAsStream("/cameo-bridge-build.properties")) {
            if (stream == null) throw new IllegalStateException("Bridge build metadata is missing");
            properties.load(stream);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read bridge build metadata", error);
        }
        String target = properties.getProperty("cameoTarget");
        if (!"2022x".equals(target) && !"2024x".equals(target)) {
            throw new IllegalStateException("Unknown bridge build target: " + target);
        }
        return properties;
    }
    public static String target() { return BUILD.getProperty("cameoTarget"); }
    public static int javaRelease() { return Integer.parseInt(BUILD.getProperty("javaRelease")); }
    public static boolean acceptsHost(String version) {
        // The existing 2024x minimum/forward compatibility policy is unchanged.
        return !"2022x".equals(target()) || (version != null && version.matches("2022x(?: .*)?"));
    }
}

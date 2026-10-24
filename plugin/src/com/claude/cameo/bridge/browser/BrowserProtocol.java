package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import com.claude.cameo.bridge.util.*;
import java.net.*;
import java.util.*;

/** Browser-owned arguments never choose an HTTP method, destination or raw route. */
public final class BrowserProtocol {
    private BrowserProtocol() {}
    public static String origin(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException();
            boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(host);
            if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && local)) throw new IllegalArgumentException();
            String normalized = uri.getScheme() + "://" + host.toLowerCase(Locale.ROOT)
                    + ((uri.getPort() == -1 || uri.getPort() == ("https".equals(uri.getScheme()) ? 443 : 80)) ? "" : ":" + uri.getPort());
            if (!normalized.equals(value)) throw new IllegalArgumentException();
            return normalized;
        } catch (Exception error) { throw new IllegalArgumentException("Use an exact HTTPS origin or trusted HTTP localhost origin, without a path"); }
    }
    public static String text(JsonObject value, String key) { return CameoCreationPlans.text(value, key); }
    public static void exact(JsonObject value, String... keys) {
        if (!JsonCompat.keys(value).equals(Set.of(keys))) throw new IllegalArgumentException("Unexpected request fields");
    }
    public static String identifier(JsonObject value, String key) {
        String id = text(value, key);
        if (id.length() > 512 || id.matches(".*[/\\\\?#%\\p{Cntrl}].*")) throw new IllegalArgumentException("Invalid identifier");
        return id;
    }
    public static void validate(String operation, JsonObject args) {
        switch (operation) {
            case "status": case "project": case "selection": exact(args); return;
            case "read": exact(args, "elementId"); identifier(args, "elementId"); return;
            case "children":
                if (!Set.of("rootId", "offset", "limit").containsAll(JsonCompat.keys(args))) throw new IllegalArgumentException("Unexpected paging fields");
                if (args.has("rootId")) identifier(args, "rootId");
                range(args, "offset", 0, 1000000, 0); range(args, "limit", 1, 200, 100); return;
            case "export":
                exact(args, "projectId", "rootIds"); identifier(args, "projectId");
                JsonArray roots = args.getAsJsonArray("rootIds");
                if (roots == null || roots.size() < 1 || roots.size() > 20) throw new IllegalArgumentException("Choose 1 to 20 roots");
                Set<String> ids = new HashSet<>();
                for (JsonElement root : roots) { JsonObject item = new JsonObject(); item.add("id", root); if (!ids.add(identifier(item, "id"))) throw new IllegalArgumentException("Duplicate roots"); }
                return;
            case "exportResource":
                exact(args, "exportId", "resourceId", "offset", "limit"); identifier(args, "exportId");
                if (!text(args, "resourceId").matches("r[0-9]{1,3}")) throw new IllegalArgumentException("Invalid resource ID");
                range(args, "offset", 0, 24 * 1024 * 1024, 0); range(args, "limit", 1, 262144, 1); return;
            case "releaseExport": exact(args, "exportId"); identifier(args, "exportId"); return;
            case "prepareCreation":
                exact(args, "projectId", "targetPackageId", "operations");
                identifier(args, "projectId"); identifier(args, "targetPackageId");
                JsonObject specification = copy(args); specification.addProperty("schemaVersion", 1);
                CameoCreationPlans.specification(specification); return;
            case "readCreation": case "creationReceipt": case "applyCreation":
                exact(args, "planId", "planHash");
                if (!text(args, "planId").matches("[0-9a-fA-F-]{36}") || !text(args, "planHash").matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid plan reference");
                return;
            default: throw new IllegalArgumentException("Unsupported browser operation");
        }
    }
    public static int range(JsonObject value, String key, int min, int max, int fallback) {
        int number = fallback;
        if (value.has(key)) {
            JsonElement field = value.get(key);
            if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isNumber() || !field.toString().matches("[0-9]+")) throw new IllegalArgumentException("Integer paging range required");
            number = field.getAsBigInteger().intValueExact();
        }
        if (number < min || number > max) throw new IllegalArgumentException("Invalid paging range");
        return number;
    }
    public static JsonObject copy(JsonObject value) { return new JsonParser().parse(value.toString()).getAsJsonObject(); }
}

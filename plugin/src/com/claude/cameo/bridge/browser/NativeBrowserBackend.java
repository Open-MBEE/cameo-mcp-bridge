package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Fixed native loopback adapter. Proxies and redirects are never used. */
public final class NativeBrowserBackend implements BrowserBridge.Backend {
    private final int port;
    public NativeBrowserBackend(int port) { this.port = port; }
    @Override public JsonObject call(String operation, JsonObject arguments) throws Exception {
        BrowserProtocol.validate(operation, arguments);
        String route, method = "GET"; JsonObject body = null;
        switch (operation) {
            case "status": route = "/capabilities"; break;
            case "project": route = "/api/v1/project"; break;
            case "selection": route = "/api/v1/ui/state"; break;
            case "read": route = "/api/v1/elements/" + BrowserProtocol.identifier(arguments, "elementId"); break;
            case "children":
                route = "/api/v1/containment-tree/children?view=full&offset=" + BrowserProtocol.range(arguments, "offset", 0, 1000000, 0)
                    + "&limit=" + BrowserProtocol.range(arguments, "limit", 1, 200, 100);
                if (arguments.has("rootId")) route += "&rootId=" + URLEncoder.encode(BrowserProtocol.identifier(arguments, "rootId"), StandardCharsets.UTF_8);
                break;
            default:
                String action;
                switch (operation) {
                    case "export": action = "model-exports/open"; break;
                    case "exportResource": action = "model-exports/read"; break;
                    case "releaseExport": action = "model-exports/release"; break;
                    case "prepareCreation": action = "creation/prepare"; break;
                    case "readCreation": action = "creation/read"; break;
                    case "creationReceipt": action = "creation/receipt"; break;
                    default: throw new IllegalArgumentException("Apply requires native authorization");
                }
                route = "/api/v1/" + action; method = "POST"; body = BrowserProtocol.copy(arguments); body.addProperty("schemaVersion", 1);
        }
        return request(method, route, body);
    }
    @Override public JsonObject apply(JsonObject arguments) throws Exception {
        BrowserProtocol.validate("applyCreation", arguments);
        JsonObject body = BrowserProtocol.copy(arguments); body.addProperty("schemaVersion", 1); body.addProperty("allowWrite", true);
        return request("POST", "/api/v1/creation/apply", body);
    }
    private JsonObject request(String method, String route, JsonObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + route).openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(40000); connection.setRequestMethod(method);
        try {
            if (body != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream output = connection.getOutputStream()) { output.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) throw new IOException("Native bridge response missing");
            byte[] bytes;
            try (InputStream input = stream) { bytes = input.readNBytes(4 * 1024 * 1024 + 1); }
            if (bytes.length > 4 * 1024 * 1024) throw new IOException("Native bridge response exceeds 4 MiB");
            JsonObject result = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (status != 200) throw new IOException(result.has("message") ? result.get("message").getAsString() : "Native bridge refused the request");
            return result;
        } finally { connection.disconnect(); }
    }
}

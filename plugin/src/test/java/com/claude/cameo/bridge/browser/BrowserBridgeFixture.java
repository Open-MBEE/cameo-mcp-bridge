package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Synthetic HTTP fixture only. Never loads the Cameo application or a user model. */
public final class BrowserBridgeFixture {
    public static void main(String[] args) throws Exception {
        byte[] resource = "<uml:Model xmlns:uml=\"http://www.eclipse.org/uml2/5.0.0/UML\" name=\"Synthetic\"/>".getBytes(StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder(); for (byte value : MessageDigest.getInstance("SHA-256").digest(resource)) hex.append(String.format("%02x", value & 255));
        BrowserBridgeTest.Fake backend = new BrowserBridgeTest.Fake() {
            @Override public JsonObject call(String operation, JsonObject arguments) {
                if (operation.equals("export")) return BrowserBridgeTest.object("{\"schemaVersion\":1,\"exportId\":\""+BrowserBridgeTest.ID+"\",\"projectId\":\"project-one\",\"primaryModelId\":\"root\",\"rootIds\":[\"root\"],\"totalBytes\":"+resource.length+",\"files\":[{\"resourceId\":\"r0\",\"path\":\"native/model.uml\",\"bytes\":"+resource.length+",\"sha256\":\""+hex+"\"}],\"manifest\":{\"schemaVersion\":1,\"primaryResource\":\"native/model.uml\"}}");
                if (operation.equals("exportResource")) {
                    int offset = arguments.get("offset").getAsInt(), next = Math.min(resource.length, offset + arguments.get("limit").getAsInt());
                    JsonObject result = new JsonObject(); result.add("exportId", arguments.get("exportId")); result.addProperty("resourceId", "r0"); result.addProperty("offset", offset); result.addProperty("nextOffset", next); result.addProperty("contentBase64", Base64.getEncoder().encodeToString(Arrays.copyOfRange(resource, offset, next))); return result;
                }
                return super.call(operation, arguments);
            }
        };
        try (BrowserHttpServer server = new BrowserHttpServer(0, new BrowserBridge(Set.of(args[0]), backend, (origin, plan) -> true))) {
            server.start(); System.out.println("{\"port\":"+server.port()+"}"); System.out.flush();
            new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
        }
    }
}

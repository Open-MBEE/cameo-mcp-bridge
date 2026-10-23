package com.claude.cameo.bridge.util;

import com.google.gson.*;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/** Operations available on both Cameo's Gson 2.2.4 and 2.9 runtimes. */
public final class JsonCompat {
    private JsonCompat() {}

    public static JsonElement string(String value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }

    public static JsonObject copy(JsonObject value) {
        return copy((JsonElement) value).getAsJsonObject();
    }

    public static JsonElement copy(JsonElement value) {
        if (value == null || value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                result.add(entry.getKey(), copy(entry.getValue()));
            }
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement item : value.getAsJsonArray()) result.add(copy(item));
            return result;
        }
        // Gson primitives are immutable; preserve their numeric representation.
        return value;
    }

    public static Set<String> keys(JsonObject value) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : value.entrySet()) keys.add(entry.getKey());
        return keys;
    }
}

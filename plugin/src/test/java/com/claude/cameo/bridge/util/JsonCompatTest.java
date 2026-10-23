package com.claude.cameo.bridge.util;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class JsonCompatTest {
    @Test public void stringArrayEntriesKeepNullAndEscapeSemantics() {
        JsonArray array = new JsonArray();
        array.add(JsonCompat.string(null));
        array.add(JsonCompat.string("quote\"\n"));
        assertTrue(array.get(0).isJsonNull());
        assertEquals("quote\"\n", array.get(1).getAsString());
    }
    @Test public void copiesNestedContainersAndPreservesTypesOrderAndNumberBytes() {
        JsonObject original = new JsonParser().parse("{\"z\":[null,true,9007199254740993,1.00,\"hello\",{\"x\":2}],\"a\":false}").getAsJsonObject();
        JsonObject copy = JsonCompat.copy(original);
        assertEquals(original.toString(), copy.toString());
        assertEquals("9007199254740993", copy.getAsJsonArray("z").get(2).toString());
        assertEquals("1.00", copy.getAsJsonArray("z").get(3).toString());
        copy.getAsJsonArray("z").get(5).getAsJsonObject().addProperty("x", 3);
        copy.getAsJsonArray("z").add(new JsonPrimitive("new"));
        assertEquals(2, original.getAsJsonArray("z").get(5).getAsJsonObject().get("x").getAsInt());
        assertEquals(6, original.getAsJsonArray("z").size());
        assertArrayEquals(new String[]{"z", "a"}, JsonCompat.keys(original).toArray(new String[0]));
        assertTrue(JsonCompat.copy((JsonElement)null).isJsonNull());
    }

    @Test public void encodesKnownSha256WithoutChangingPlanHashFormat() throws Exception {
        assertEquals("00017f80ff", Hex.format(new byte[]{0,1,127,(byte)128,(byte)255}));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", NativeExportStore.hash(new byte[0]));
    }
}

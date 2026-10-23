package com.claude.cameo.bridge.util;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeExportStoreTest {
    private Map<String,byte[]> data() { return Map.of("native/model.uml","<model/>".getBytes()); }
    @Test public void capturesImmutableBytesAndVerifiableChunks() throws Exception {
        var store=new NativeExportStore(); var data=data(); var manifest=store.open(new JsonObject(),data); data.get("native/model.uml")[0]='x';
        String id=manifest.get("exportId").getAsString(); var chunk=store.read(id,"r0",0,256);
        byte[] bytes=Base64.getDecoder().decode(chunk.get("contentBase64").getAsString());
        assertEquals("<model/>",new String(bytes)); assertEquals(NativeExportStore.hash(bytes),manifest.getAsJsonArray("files").get(0).getAsJsonObject().get("sha256").getAsString());
        store.release(id); store.release(id); assertThrows(IllegalArgumentException.class,()->store.read(id,"r0",0,1));
    }
    @Test public void refusesPathsRangesAndOversizedCaptures() throws Exception {
        var store=new NativeExportStore();
        for(String path:List.of("native/../private","native//model","native/\\escape","/native/model","catalog/C:secret")) assertThrows(IllegalArgumentException.class,()->store.open(new JsonObject(),Map.of(path,new byte[1])));
        var m=store.open(new JsonObject(),data()); String id=m.get("exportId").getAsString();
        assertThrows(IllegalArgumentException.class,()->store.read(id,"../private",0,1));
        assertThrows(IllegalArgumentException.class,()->store.read(id,"r0",0,262145));
        assertThrows(IllegalArgumentException.class,()->store.read(id,"r0",100,1));
        assertThrows(IllegalArgumentException.class,()->store.open(new JsonObject(),Map.of("native/large",new byte[NativeExportStore.MAX_BYTES+1])));
    }
    @Test public void capacityAndExpiryAreBounded() throws Exception {
        var now=new AtomicLong(1000); var store=new NativeExportStore(now::get);
        var first=store.open(new JsonObject(),data()); store.open(new JsonObject(),data());
        assertThrows(IllegalStateException.class,()->store.open(new JsonObject(),data()));
        now.addAndGet(300001); assertThrows(IllegalArgumentException.class,()->store.read(first.get("exportId").getAsString(),"r0",0,1));
        assertNotNull(store.open(new JsonObject(),data()));
    }
}

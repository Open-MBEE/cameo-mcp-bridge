package com.claude.cameo.bridge.util;

import com.google.gson.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded immutable native export handles, unrelated to consumer processing. */
public final class NativeExportStore {
    public static final int MAX_BYTES=24*1024*1024,CHUNK_BYTES=256*1024;
    private record Export(JsonObject manifest,List<byte[]> files,long expiry) {}
    private final Map<String,Export> exports=new HashMap<>();
    private final LongSupplier clock;
    public NativeExportStore() { this(System::currentTimeMillis); }
    public NativeExportStore(LongSupplier clock) { this.clock=clock; }
    private void expire() { exports.values().removeIf(e->e.expiry()<=clock.getAsLong()); }
    public static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    public synchronized JsonObject open(JsonObject context,Map<String,byte[]> resources) throws Exception {
        expire(); if(exports.size()>=2) throw new IllegalStateException("Native export capacity reached; release earlier handles");
        if(resources.isEmpty() || resources.size()>128) throw new IllegalArgumentException("Native export resource count exceeds bound");
        JsonObject manifest=context.deepCopy(); JsonArray files=new JsonArray(); List<byte[]> captured=new ArrayList<>(); int total=0;
        for(var item:new TreeMap<>(resources).entrySet()) {
            String path=item.getKey();
            if(!path.matches("(?:native|catalog)/[^\\\\:\\x00-\\x1f]+") || Arrays.stream(path.split("/",-1)).anyMatch(p->p.isEmpty() || p.equals(".") || p.equals(".."))) throw new IllegalArgumentException("Unsafe native resource path");
            total=Math.addExact(total,item.getValue().length); if(total>MAX_BYTES) throw new IllegalArgumentException("Native export exceeds 24 MiB");
            byte[] bytes=item.getValue().clone(); JsonObject file=new JsonObject(); file.addProperty("resourceId","r"+captured.size());
            file.addProperty("path",path); file.addProperty("bytes",bytes.length); file.addProperty("sha256",hash(bytes)); files.add(file); captured.add(bytes);
        }
        String id=UUID.randomUUID().toString(); long expiry=clock.getAsLong()+300000;
        manifest.addProperty("schemaVersion",1); manifest.addProperty("exportId",id); manifest.add("files",files);
        manifest.addProperty("expiresAt",Instant.ofEpochMilli(expiry).toString()); manifest.addProperty("totalBytes",total);
        exports.put(id,new Export(manifest,captured,expiry)); return manifest.deepCopy();
    }
    public synchronized JsonObject read(String id,String resourceId,int offset,int limit) {
        expire(); Export export=exports.get(id);
        if(export==null) throw new IllegalArgumentException("Unknown or expired native export");
        if(!resourceId.matches("r[0-9]{1,3}") || offset<0 || limit<1 || limit>CHUNK_BYTES) throw new IllegalArgumentException("Invalid native resource range");
        int index=Integer.parseInt(resourceId.substring(1)); if(index>=export.files().size()) throw new IllegalArgumentException("Unknown resource ID");
        byte[] bytes=export.files().get(index); if(offset>bytes.length) throw new IllegalArgumentException("Range exceeds resource");
        int end=Math.min(bytes.length,offset+limit); JsonObject result=new JsonObject();
        result.addProperty("exportId",id); result.addProperty("resourceId",resourceId); result.addProperty("offset",offset); result.addProperty("nextOffset",end);
        result.addProperty("contentBase64",Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,offset,end))); return result;
    }
    public synchronized JsonObject release(String id) {
        expire(); exports.remove(id); JsonObject result=new JsonObject(); result.addProperty("released",true); return result;
    }
}

package com.claude.cameo.bridge.util;

import com.google.gson.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Typed additive plans and durable at-most-once receipts; no model API dependency. */
public final class CameoCreationPlans {
    public interface Backend {
        JsonObject inspect(JsonObject specification) throws Exception;
        void begin() throws Exception;
        JsonObject create(JsonObject specification) throws Exception;
        void commit() throws Exception;
        void rollback() throws Exception;
    }
    private final Path directory;
    public CameoCreationPlans(Path directory) { this.directory=directory.toAbsolutePath().normalize(); }
    public static void keys(JsonObject object, String... allowed) {
        if(!Set.of(allowed).containsAll(JsonCompat.keys(object))) throw new IllegalArgumentException("Unknown creation-plan field");
    }
    public static String text(JsonObject object,String name) {
        JsonElement value=object.get(name);
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string: "+name);
        String s=value.getAsString();
        if(s.isBlank() || s.length()>512 || s.chars().anyMatch(c->c<32)) throw new IllegalArgumentException("Invalid "+name);
        return s;
    }
    private static int integer(JsonObject o,String key,int fallback) {
        if(!o.has(key)) return fallback;
        if(!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber()
                || !o.get(key).toString().matches("-?[0-9]+")) throw new IllegalArgumentException("Expected integer: "+key);
        return o.get(key).getAsBigInteger().intValueExact();
    }
    public static JsonObject specification(JsonObject raw) {
        keys(raw,"schemaVersion","projectId","targetPackageId","operations");
        if(!raw.has("schemaVersion") || !raw.get("schemaVersion").toString().equals("1")) throw new IllegalArgumentException("Creation schema 1 required");
        JsonObject out=new JsonObject(); out.addProperty("schemaVersion",1);
        out.addProperty("projectId",text(raw,"projectId")); out.addProperty("targetPackageId",text(raw,"targetPackageId"));
        JsonArray operations=raw.getAsJsonArray("operations");
        if(operations==null || operations.size()==0 || operations.size()>100) throw new IllegalArgumentException("Choose 1 to 100 operations");
        Map<String,String> previous=new LinkedHashMap<>(); Set<String> names=new HashSet<>(); JsonArray normalized=new JsonArray();
        for(JsonElement value:operations) {
            JsonObject op=value.getAsJsonObject(); keys(op,"id","kind","name","owner","type","lower","upper","aggregation");
            String id=text(op,"id"),kind=text(op,"kind"),name=text(op,"name"),owner=text(op,"owner");
            if(owner.equals(text(out,"targetPackageId"))) owner="$target";
            if(!id.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || previous.containsKey(id)) throw new IllegalArgumentException("Duplicate or invalid temporary ID");
            if(!Set.of("package","block","property").contains(kind)) throw new IllegalArgumentException("Unsupported creation kind");
            if(name.length()>128 || !name.equals(name.strip()) || !names.add(owner+"\u0000"+name)) throw new IllegalArgumentException("Invalid or duplicate sibling name");
            if(owner.startsWith("@") && !previous.containsKey(owner.substring(1))) throw new IllegalArgumentException("Owner must precede its child");
            if(!kind.equals("property") && (op.has("type") || op.has("lower") || op.has("upper") || op.has("aggregation"))) throw new IllegalArgumentException("Only properties accept type and multiplicity");
            JsonObject n=new JsonObject(); n.addProperty("id",id); n.addProperty("kind",kind); n.addProperty("name",name); n.addProperty("owner",owner);
            if(kind.equals("property")) {
                String type=text(op,"type");
                if(type.startsWith("@") && !"block".equals(previous.get(type.substring(1)))) throw new IllegalArgumentException("Property type must be an earlier block or existing type ID");
                int lower=integer(op,"lower",1),upper=integer(op,"upper",1);
                if(lower<0 || lower>1000000 || upper < -1 || upper>1000000 || (upper!=-1 && lower>upper)) throw new IllegalArgumentException("Invalid multiplicity");
                String aggregation=op.has("aggregation")?text(op,"aggregation"):"none";
                if(!Set.of("none","composite").contains(aggregation)) throw new IllegalArgumentException("Unsupported aggregation");
                n.addProperty("type",type); n.addProperty("lower",lower); n.addProperty("upper",upper); n.addProperty("aggregation",aggregation);
            }
            if(owner.startsWith("@") && !(kind.equals("property")?"block":"package").equals(previous.get(owner.substring(1))))
                throw new IllegalArgumentException("Owner kind does not support this child");
            previous.put(id,kind); normalized.add(n);
        }
        out.add("operations",normalized);
        if(out.toString().getBytes(StandardCharsets.UTF_8).length>60000) throw new IllegalArgumentException("Creation plan exceeds byte bound");
        return out;
    }
    public static String hash(JsonObject value) throws Exception {
        return Hex.format(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));
    }
    public JsonObject prepare(JsonObject raw,Backend backend) throws Exception {
        JsonObject spec=specification(raw),context=backend.inspect(spec);
        Files.createDirectories(directory);
        try(var files=Files.list(directory)) { if(files.limit(2001).count()>=2000) throw new IllegalStateException("Plan store full; archive reviewed receipts before creating plans"); }
        JsonObject plan=new JsonObject(); plan.addProperty("schemaVersion",1); plan.addProperty("planId",UUID.randomUUID().toString());
        plan.addProperty("expiresAt",Instant.now().plusSeconds(600).toString()); plan.add("specification",spec); plan.add("context",context);
        plan.addProperty("planHash",hash(plan));
        write(path(plan.get("planId").getAsString(),"plan"),plan,true);
        return plan;
    }
    private Path path(String id,String suffix) {
        if(!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) throw new IllegalArgumentException("Invalid plan ID");
        return directory.resolve(id+"."+suffix+".json");
    }
    public JsonObject read(String id,String expectedHash) throws Exception {
        JsonObject plan=readFile(path(id,"plan")),unhashed=JsonCompat.copy(plan); unhashed.remove("planHash");
        if(!hash(unhashed).equals(expectedHash) || !text(plan,"planHash").equals(expectedHash)) throw new IllegalArgumentException("Creation plan hash mismatch");
        return plan;
    }
    public JsonObject receipt(String id,String expectedHash) throws Exception {
        read(id,expectedHash);
        Path file=path(id,"receipt");
        if(Files.exists(file)) return readFile(file);
        JsonObject result=new JsonObject(); result.addProperty("planId",id); result.addProperty("planHash",expectedHash); result.addProperty("status","prepared"); return result;
    }
    public JsonObject apply(String id,String expectedHash,Backend backend) throws Exception {
        JsonObject plan=read(id,expectedHash),receipt=receipt(id,expectedHash);
        if(!text(receipt,"status").equals("prepared")) return receipt; // Includes crash/timeout uncertainty: never retry a write.
        if(Instant.now().isAfter(Instant.parse(text(plan,"expiresAt")))) throw new IllegalStateException("Creation plan expired; prepare and review a new plan");
        JsonObject spec=plan.getAsJsonObject("specification");
        if(!plan.getAsJsonObject("context").equals(backend.inspect(spec))) throw new IllegalStateException("Cameo changed since preview; prepare and review a new plan");
        receipt.addProperty("status","outcomeUnknown"); receipt.addProperty("attemptedAt",Instant.now().toString());
        // CREATE_NEW is a durable at-most-once claim, including across plugin restarts.
        try { write(path(id,"receipt"),receipt,true); }
        catch(FileAlreadyExistsException race) { return readFile(path(id,"receipt")); }
        boolean begun=false,committing=false;
        try {
            backend.begin(); begun=true;
            JsonObject created=backend.create(spec);
            committing=true; backend.commit(); begun=false;
            receipt.addProperty("status","committed"); receipt.add("created",created); receipt.addProperty("saved",false);
        } catch(Exception error) {
            boolean rolledBack=false;
            if(begun) { try { backend.rollback(); rolledBack=true; } catch(Exception ignored) {} }
            if(!committing && (!begun || rolledBack)) receipt.addProperty("status","rolledBack");
            receipt.addProperty("error",committing?"Commit outcome uncertain; inspect Cameo and receipt, never retry automatically":String.valueOf(error.getMessage()));
        }
        write(path(id,"receipt"),receipt,false);
        return receipt;
    }
    private static JsonObject readFile(Path path) throws Exception {
        try(var in=Files.newInputStream(path)) {
            byte[] bytes=in.readNBytes(256*1024+1);
            if(bytes.length>256*1024) throw new IllegalArgumentException("Plan receipt exceeds byte bound");
            return new JsonParser().parse(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void write(Path path,JsonObject value,boolean fresh) throws Exception {
        byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);
        if(fresh) {
            try(var channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                ByteBuffer b=ByteBuffer.wrap(bytes); while(b.hasRemaining()) channel.write(b); channel.force(true);
            }
        } else {
            Path temporary=Files.createTempFile(path.getParent(),"receipt-",".tmp");
            try {
                try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                    ByteBuffer b=ByteBuffer.wrap(bytes); while(b.hasRemaining()) channel.write(b); channel.force(true);
                }
                Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        }
    }
}

package com.gwsheridan.worldarchitect.authoring;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.*;

/** Bounded local recovery for large sparse islands; ordinary draft documents remain unchanged. */
public final class PackedDraft {
    private static final int MAX=64*1024*1024;
    private PackedDraft(){}
    public static JsonElement pack(JsonElement source){
        if(JsonDraftSize.estimate(source)<=4L*1024*1024)return source.deepCopy();
        byte[] raw=source.toString().getBytes(StandardCharsets.UTF_8);if(raw.length>MAX)throw new IllegalArgumentException("Authoring recovery exceeds its working budget.");
        try{var out=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(out)){gzip.write(raw);}var result=new JsonObject();result.addProperty("packed_draft",1);result.addProperty("gzip",Base64.getEncoder().encodeToString(out.toByteArray()));return result;}catch(IOException error){throw new UncheckedIOException(error);}
    }
    public static JsonElement unpack(JsonElement source){
        if(!source.isJsonObject()||!source.getAsJsonObject().has("packed_draft"))return source.deepCopy();
        var object=source.getAsJsonObject();if(object.get("packed_draft").getAsInt()!=1||object.size()!=2)throw new IllegalArgumentException("Unknown packed authoring draft.");
        String encoded=object.get("gzip").getAsString();if(encoded.length()>8*1024*1024)throw new IllegalArgumentException("Packed authoring draft exceeds its storage budget.");
        try(var input=new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))){byte[] raw=input.readNBytes(MAX+1);if(raw.length>MAX)throw new IOException("Packed authoring draft exceeds its expansion budget.");return JsonParser.parseString(new String(raw,StandardCharsets.UTF_8));}catch(IOException error){throw new IllegalArgumentException("Cannot restore the packed authoring draft.",error);}
    }
}

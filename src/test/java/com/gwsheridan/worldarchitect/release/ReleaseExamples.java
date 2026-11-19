package com.gwsheridan.worldarchitect.release;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

/** Build tool for immutable release examples, starting from validated Studio exports. */
public final class ReleaseExamples {
    private static final String MANIFEST="worldarchitect.bundle.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]),exports=Path.of(args[1]);String version=args[2];
        if(!version.matches("[0-9][a-zA-Z0-9._-]{0,39}"))throw new IllegalArgumentException("Invalid example release");
        for(String kind:List.of("partial","skyblock")) {
            Path directory=root.resolve("examples/preset-studio");var files=read(directory.resolve("bundles/studio-"+kind+"-1.0.0.zip"));
            var manifest=json(files.get(MANIFEST));manifest.addProperty("release",version);files.put(MANIFEST,bytes(manifest));
            save(files,directory.resolve("bundles/studio-"+kind+"-"+version+".zip"),directory.resolve(kind+"-"+version+"-source"));
        }
        var titles=Map.of("single-block","Single Block","layered","Layered","skyblock","Skyblock","custom-end","Custom End");
        var descriptions=Map.of("single-block","Rolling stone terrain with independent dimension settings.",
                "layered","Dirt over andesite and stone, ready for layer and terrain editing.",
                "skyblock","A classic starting island and 64 personal multiplayer island slots.",
                "custom-end","Custom outer End terrain with the native central arena and dragon progression.");
        for(String kind:List.of("single-block","layered","skyblock","custom-end")) {
            var input=read(exports.resolve(kind+"-1-1.0.0.zip"));var manifest=json(input.get(MANIFEST));
            String previous=manifest.get("preset").getAsString(),id="worldarchitect_examples:"+kind;
            String[] old=previous.split(":",2);byte[] preset=input.remove("data/"+old[0]+"/worldgen/world_preset/"+old[1]+".json");
            if(preset==null)throw new IOException("Missing exported preset");
            var files=new TreeMap<String,byte[]>();input.forEach((path,data)->files.put(path.replace("data/wa_presets/","data/worldarchitect_examples/"),data));
            files.put("data/worldarchitect_examples/worldgen/world_preset/"+kind+".json",preset);
            manifest.addProperty("preset",id);manifest.addProperty("title",titles.get(kind)+" starter");manifest.addProperty("description",descriptions.get(kind));
            manifest.addProperty("author","GWSHERIDAN");manifest.addProperty("release",version);manifest.getAsJsonObject("options").addProperty("seed",18);
            files.put(MANIFEST,bytes(manifest));
            var pack=json(files.get("pack.mcmeta"));pack.getAsJsonObject("pack").addProperty("description",titles.get(kind)+" starter for World Architect "+version);files.put("pack.mcmeta",bytes(pack));
            Path directory=root.resolve("examples/creator-starters");save(files,directory.resolve("bundles/"+kind+"-"+version+".zip"),directory.resolve(kind+"-source"));
        }
        System.out.println("Prepared six immutable versioned example bundles and their source resources.");
    }
    private static SortedMap<String,byte[]> read(Path path)throws IOException {
        var files=new TreeMap<String,byte[]>();
        try(var zip=new ZipInputStream(Files.newInputStream(path))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(entry.isDirectory())continue;
            String name=entry.getName();if(name.startsWith("/")||name.contains("..")||name.contains("\\"))throw new IOException("Unsafe example entry");
            byte[] bytes=zip.readNBytes(8*1024*1024+1);if(bytes.length>8*1024*1024||files.putIfAbsent(name,bytes)!=null)throw new IOException("Invalid example entry");
        }}return files;
    }
    private static void save(SortedMap<String,byte[]> files,Path archive,Path source)throws IOException {
        var buffer=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(buffer)){for(var file:files.entrySet()){var entry=new ZipEntry(file.getKey());entry.setTimeLocal(LocalDateTime.of(1980,1,1,0,0));zip.putNextEntry(entry);zip.write(file.getValue());zip.closeEntry();}}
        immutable(archive,buffer.toByteArray());for(var file:files.entrySet())immutable(source.resolve(file.getKey()),file.getValue());
    }
    private static void immutable(Path path,byte[] bytes)throws IOException {
        if(Files.exists(path)){if(!Arrays.equals(bytes,Files.readAllBytes(path)))throw new IOException("Example release already exists with different bytes: "+path.getFileName());return;}
        Files.createDirectories(path.getParent());Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
    }
    private static JsonObject json(byte[] bytes){return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static byte[] bytes(JsonObject json){return (GSON.toJson(json)+"\n").getBytes(StandardCharsets.UTF_8);}
}

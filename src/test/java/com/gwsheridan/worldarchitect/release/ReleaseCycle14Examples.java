package com.gwsheridan.worldarchitect.release;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;

/** Creates new versioned examples from validated inputs; earlier example files are retained. */
public final class ReleaseCycle14Examples {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);String from=args[3],version=args[4];
        if(!from.matches("[0-9][a-zA-Z0-9._-]{0,39}")||!version.matches("[0-9][a-zA-Z0-9._-]{0,39}")||from.equals(version))throw new IllegalArgumentException("Use distinct valid example versions");
        for(String name:List.of("single-block","layered","skyblock","custom-end"))copy(root,"creator-starters",name,from,version);
        for(String name:List.of("studio-partial","studio-skyblock"))copy(root,"preset-studio",name,from,version);
        copy(root,"assembly-studio","assembly-demo",from,version);copy(root,"regional-terrain","regional-demo",from,version);copy(root,"procedural-pools","pool-demo",from,version);
        fresh(root,Path.of(args[1]),"shared-regions","shared-regions","shared_regions",version,false);
        fresh(root,Path.of(args[2]),"constrained-pools","constrained-pool","constrained_pool",version,true);
    }
    private static void copy(Path root,String directory,String name,String from,String version)throws Exception{
        var dir=root.resolve("examples/"+directory);var files=new TreeMap<>(ReleaseExamples.read(dir.resolve("bundles/"+name+"-"+from+".zip")));metadata(files,version,null,null);
        ReleaseExamples.save(files,dir.resolve("bundles/"+name+"-"+version+".zip"),dir.resolve(name+"-"+version+"-source"));
    }
    private static void metadata(Map<String,byte[]> files,String version,String title,String description){
        var manifest=ReleaseExamples.json(files.get("worldarchitect.bundle.json"));manifest.addProperty("release",version);manifest.addProperty("author","GWSHERIDAN");if(title!=null)manifest.addProperty("title",title);if(description!=null)manifest.addProperty("description",description);
        for(var mod:manifest.getAsJsonArray("required_mods"))if(mod.getAsString().startsWith("wa_"))throw new IllegalArgumentException("An example cannot require a validation provider");
        files.put("worldarchitect.bundle.json",ReleaseExamples.bytes(manifest));var pack=ReleaseExamples.json(files.get("pack.mcmeta"));pack.getAsJsonObject("pack").addProperty("description",manifest.get("title").getAsString()+" for World Architect "+version);files.put("pack.mcmeta",ReleaseExamples.bytes(pack));
    }
    private static void fresh(Path root,Path archive,String directory,String filename,String name,String version,boolean pool)throws Exception{
        var files=new TreeMap<>(ReleaseExamples.read(archive));var manifest=ReleaseExamples.json(files.get("worldarchitect.bundle.json"));String old=manifest.get("preset").getAsString(),id="worldarchitect_examples:"+name;String[] parts=old.split(":",2);
        var preset=ReleaseExamples.json(files.remove("data/"+parts[0]+"/worldgen/world_preset/"+parts[1]+".json"));String structureId;
        if(pool){var selected=preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").getAsJsonObject("dimension_profile").getAsJsonObject("content").getAsJsonArray("structures");if(selected.size()!=1)throw new IllegalArgumentException("Expected one pool structure");structureId=selected.get(0).getAsJsonObject().get("structure").getAsString();}
        else structureId="validation:regional_marker";
        String structureName=pool?"constrained_pool":"regional_marker",replacement="worldarchitect_examples:"+structureName;String[] sourceId=structureId.split(":",2);
        var structure=ReleaseExamples.json(files.remove("data/"+sourceId[0]+"/worldgen/structure/"+sourceId[1]+".json"));
        if(pool&&structure.getAsJsonObject("assembly_pool").get("format").getAsInt()!=2)throw new IllegalArgumentException("Expected constrained pool format 2");
        if(!pool&&preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").getAsJsonObject("dimension_profile").getAsJsonObject("rule_engine").get("version").getAsInt()!=2)throw new IllegalArgumentException("Expected named-region engine version 2");
        replace(preset,structureId,replacement);for(var module:structure.getAsJsonArray("modules"))module.getAsJsonObject().getAsJsonObject("template").getAsJsonObject("worldarchitect").addProperty("author","GWSHERIDAN");
        files.put("data/worldarchitect_examples/worldgen/structure/"+structureName+".json",ReleaseExamples.bytes(structure));files.put("data/worldarchitect_examples/worldgen/world_preset/"+name+".json",ReleaseExamples.bytes(preset));
        manifest.addProperty("preset",id);files.put("worldarchitect.bundle.json",ReleaseExamples.bytes(manifest));
        metadata(files,version,pool?"Constrained pool example":"Shared regions example",pool?"Required halls, bounded room counts and capped branches with safe root markers.":"Named circle and meadow masks shared by materials, ores, decorations, structures and spawn conditions.");
        Path dir=root.resolve("examples/"+directory);ReleaseExamples.save(files,dir.resolve("bundles/"+filename+"-"+version+".zip"),dir.resolve(filename+"-"+version+"-source"));
    }
    private static void replace(JsonElement value,String old,String replacement){if(value.isJsonObject()){for(var entry:value.getAsJsonObject().entrySet()){if(entry.getValue().isJsonPrimitive()&&entry.getValue().getAsJsonPrimitive().isString()&&entry.getValue().getAsString().equals(old))entry.setValue(new JsonPrimitive(replacement));else replace(entry.getValue(),old,replacement);}}else if(value.isJsonArray())for(var child:value.getAsJsonArray())replace(child,old,replacement);}
}

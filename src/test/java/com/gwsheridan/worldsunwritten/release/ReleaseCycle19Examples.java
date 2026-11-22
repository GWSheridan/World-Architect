package com.gwsheridan.worldsunwritten.release;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Prepare the six verified cell starters, retaining their expanded resources for pack authors. */
public final class ReleaseCycle19Examples {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]),exports=Path.of(args[1]);String version=args[2];
        for(String title:List.of("Hex Continents","Hex Skyblock","Expanding One Chunk","Resource Archipelago","Biome Mosaic","Underground Start")){
            String slug=title.toLowerCase(Locale.ROOT).replace(' ','_'),name=slug.replace('_','-');var files=ReleaseExamples.read(exports.resolve(slug+"-"+version+".zip"));var manifest=ReleaseExamples.json(files.get("worldsunwritten.bundle.json"));
            String previous=manifest.get("preset").getAsString(),id="worldsunwritten_examples:"+slug;String oldPath=presetPath(previous);var preset=files.remove(oldPath);if(preset==null)throw new IllegalStateException("Missing verified preset "+slug);
            manifest.addProperty("preset",id);manifest.addProperty("title",title);manifest.addProperty("author","GWSHERIDAN");manifest.addProperty("description",switch(slug){case "hex_continents"->"Hexagonal terrain with biome types, oceans and stone borders.";case "hex_skyblock"->"Template islands separated by void, with a supplied starting island.";case "expanding_one_chunk"->"Begin in one chunk and open neighbouring terrain through advancements.";case "resource_archipelago"->"Coal, iron and copper islands in a seeded hex layout.";case "biome_mosaic"->"Square cells with distinct biome and terrain types.";default->"Begin in a supplied underground chamber and expand through advancements.";});
            manifest.addProperty("release",version);manifest.addProperty("lock","unlocked");manifest.remove("editable_dimensions");manifest.remove("editable_features");manifest.remove("editable_options");manifest.remove("inheritance");files.put(presetPath(id),preset);files.put("worldsunwritten.bundle.json",ReleaseExamples.bytes(manifest));
            var metadata=ReleaseExamples.json(files.get("pack.mcmeta"));metadata.getAsJsonObject("pack").addProperty("description",title+" for Worlds Unwritten "+version);files.put("pack.mcmeta",ReleaseExamples.bytes(metadata));
            var folder=root.resolve("examples/cell-worlds");ReleaseExamples.save(files,folder.resolve("bundles/"+name+"-"+version+".zip"),folder.resolve("datapacks/"+name));
        }
        System.out.println("Prepared six cell-world example bundles and editable datapacks.");
    }
    private static String presetPath(String id){int colon=id.indexOf(':');return "data/"+id.substring(0,colon)+"/worldgen/world_preset/"+id.substring(colon+1)+".json";}
}

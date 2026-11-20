package com.gwsheridan.worldarchitect.release;

import java.nio.file.Path;
import java.util.TreeMap;

/** Repackages validated exports with release IDs while preserving their generation definitions. */
public final class ReleaseCycle13Examples {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);String version=args[3];if(!version.matches("[0-9][a-zA-Z0-9._-]{0,39}"))throw new IllegalArgumentException("Invalid release");
        for(String kind:new String[]{"regional-terrain","procedural-pools"}){
            var files=new TreeMap<>(ReleaseExamples.read(Path.of(args[kind.equals("regional-terrain")?1:2])));var manifest=ReleaseExamples.json(files.get("worldarchitect.bundle.json"));
            String old=manifest.get("preset").getAsString(),name=kind.equals("regional-terrain")?"regional_demo":"pool_demo",id="worldarchitect_examples:"+name;String[] parts=old.split(":",2);
            var preset=ReleaseExamples.json(files.remove("data/"+parts[0]+"/worldgen/world_preset/"+parts[1]+".json"));
            if(kind.equals("procedural-pools")){
                var content=preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").getAsJsonObject("dimension_profile").getAsJsonObject("content").getAsJsonArray("structures");
                if(content.size()!=1)throw new IllegalArgumentException("Unexpected structure selections");content.get(0).getAsJsonObject().addProperty("structure",id);
                var structure=ReleaseExamples.json(files.remove("data/"+parts[0]+"/worldgen/structure/"+parts[1]+".json"));if(!structure.has("assembly_pool"))throw new IllegalArgumentException("Missing pool");
                for(var value:structure.getAsJsonArray("modules")){var header=value.getAsJsonObject().getAsJsonObject("template").getAsJsonObject("worldarchitect");header.addProperty("author","GWSHERIDAN");}
                files.put("data/worldarchitect_examples/worldgen/structure/"+name+".json",ReleaseExamples.bytes(structure));
            }
            files.put("data/worldarchitect_examples/worldgen/world_preset/"+name+".json",ReleaseExamples.bytes(preset));
            manifest.addProperty("preset",id);manifest.addProperty("release",version);manifest.addProperty("author","GWSHERIDAN");manifest.addProperty("title",kind.equals("regional-terrain")?"Regional terrain example":"Procedural pool example");manifest.addProperty("description",kind.equals("regional-terrain")?"Feathered forest rectangle and a higher-priority desert circle on Layered terrain.":"Seeded halls, branches and weighted end caps with safe markers and empty named chests.");files.put("worldarchitect.bundle.json",ReleaseExamples.bytes(manifest));
            var pack=ReleaseExamples.json(files.get("pack.mcmeta"));pack.getAsJsonObject("pack").addProperty("description",manifest.get("title").getAsString()+" for World Architect "+version);files.put("pack.mcmeta",ReleaseExamples.bytes(pack));
            Path dir=root.resolve("examples/"+kind);ReleaseExamples.save(files,dir.resolve("bundles/"+name.replace('_','-')+"-"+version+".zip"),dir.resolve(name.replace('_','-')+"-"+version+"-source"));
        }
    }
}

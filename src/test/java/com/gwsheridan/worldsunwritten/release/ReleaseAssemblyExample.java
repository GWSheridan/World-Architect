package com.gwsheridan.worldsunwritten.release;

import java.nio.file.Path;
import java.util.TreeMap;

/** Keeps the geometry of the verified in-game export and gives it release metadata. */
public final class ReleaseAssemblyExample {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);String version=args[2];if(!version.matches("[0-9][a-zA-Z0-9._-]{0,39}"))throw new IllegalArgumentException("Invalid example release");
        var input=ReleaseExamples.read(Path.of(args[1]));var manifest=ReleaseExamples.json(input.get("worldsunwritten.bundle.json"));
        String previous=manifest.get("preset").getAsString();if(!previous.equals("validation:gui_pack"))throw new IllegalArgumentException("Use the verified Assembly Studio export");
        String id="worldsunwritten_examples:assembly_demo";var files=new TreeMap<>(input);
        var preset=ReleaseExamples.json(files.remove("data/validation/worldgen/world_preset/gui_pack.json"));
        var generator=preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        var selected=generator.getAsJsonObject("dimension_profile").getAsJsonObject("content").getAsJsonArray("structures");
        if(selected.size()!=1)throw new IllegalArgumentException("Unexpected assembly selections");selected.get(0).getAsJsonObject().addProperty("structure",id);
        var structure=ReleaseExamples.json(files.remove("data/validation/worldgen/structure/gui_pack.json"));
        if(structure.getAsJsonArray("modules").size()!=2)throw new IllegalArgumentException("Unexpected module count");
        for(var module:structure.getAsJsonArray("modules")){var header=module.getAsJsonObject().getAsJsonObject("template").getAsJsonObject("worldsunwritten");header.addProperty("author","GWSHERIDAN");header.addProperty("title","Assembly module");}
        files.put("data/worldsunwritten_examples/worldgen/structure/assembly_demo.json",ReleaseExamples.bytes(structure));
        files.put("data/worldsunwritten_examples/worldgen/world_preset/assembly_demo.json",ReleaseExamples.bytes(preset));
        manifest.addProperty("preset",id);manifest.addProperty("title","Assembly Studio example");manifest.addProperty("description","Two modules with transformed stairs, empty chests and safe entry markers.");manifest.addProperty("author","GWSHERIDAN");manifest.addProperty("release",version);files.put("worldsunwritten.bundle.json",ReleaseExamples.bytes(manifest));
        var pack=ReleaseExamples.json(files.get("pack.mcmeta"));pack.getAsJsonObject("pack").addProperty("description","Assembly Studio example for Worlds Unwritten "+version);files.put("pack.mcmeta",ReleaseExamples.bytes(pack));
        Path directory=root.resolve("examples/assembly-studio");ReleaseExamples.save(files,directory.resolve("bundles/assembly-demo-"+version+".zip"),directory.resolve("assembly-demo-"+version+"-source"));
        System.out.println("Prepared the verified Assembly Studio example without changing its geometry.");
    }
}

package com.gwsheridan.worldarchitect.release;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;

/** Packages reviewed example resources without overwriting an earlier version. */
public final class ReleaseCycle15Examples {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);String version=args[4];if(!version.matches("[0-9][a-zA-Z0-9._-]{0,39}"))throw new IllegalArgumentException("Invalid version");
        for(String entry:List.of("creator-starters/single-block","creator-starters/layered","creator-starters/skyblock","creator-starters/custom-end","preset-studio/studio-partial","preset-studio/studio-skyblock","assembly-studio/assembly-demo","regional-terrain/regional-demo","procedural-pools/pool-demo","shared-regions/shared-regions","constrained-pools/constrained-pool")){
            int slash=entry.indexOf('/');String directory=entry.substring(0,slash),name=entry.substring(slash+1);var files=ReleaseExamples.read(root.resolve("examples/"+directory+"/bundles/"+name+"-1.4.0.zip"));metadata(files,version,null,null);save(root,directory,name,version,files);
        }
        var polygon=fresh(Path.of(args[1]),"polygon_regions","polygon_marker",version,"Polygon regions","A concave region shared by material, ore, decoration, structure and spawn rules.");save(root,"polygon-regions","polygon-regions",version,polygon);
        var coastal=fresh(Path.of(args[2]),"coastal","coastal_ruin",version,"Coastal ruins","Seabed placement with explicit water replacement and a dry starting coast.");save(root,"terrain-anchors","coastal-ruins",version,coastal);
        var vertical=fresh(Path.of(args[3]),"vertical_tower","vertical_tower",version,"Vertical tower","Three connected floors with ladders, safe markers and a seabed-anchored entrance.");tower(vertical);save(root,"vertical-pools","vertical-tower",version,vertical);
        var combined=combined(root,polygon,vertical,version);save(root,"locked-archipelago","locked-archipelago",version,combined);
        System.out.println("Prepared fifteen versioned examples and their source resources.");
    }
    private static void save(Path root,String directory,String name,String version,SortedMap<String,byte[]> files)throws Exception{Path dir=root.resolve("examples/"+directory);ReleaseExamples.save(files,dir.resolve("bundles/"+name+"-"+version+".zip"),dir.resolve(name+"-"+version+"-source"));}
    private static void metadata(Map<String,byte[]> files,String version,String title,String description){var manifest=ReleaseExamples.json(files.get("worldarchitect.bundle.json"));manifest.addProperty("release",version);manifest.addProperty("author","GWSHERIDAN");if(title!=null)manifest.addProperty("title",title);if(description!=null)manifest.addProperty("description",description);for(var mod:manifest.getAsJsonArray("required_mods"))if(mod.getAsString().startsWith("wa_"))throw new IllegalArgumentException("Examples cannot require a validation provider");files.put("worldarchitect.bundle.json",ReleaseExamples.bytes(manifest));var pack=ReleaseExamples.json(files.get("pack.mcmeta"));pack.getAsJsonObject("pack").addProperty("description",manifest.get("title").getAsString()+" for World Architect "+version);files.put("pack.mcmeta",ReleaseExamples.bytes(pack));}
    private static SortedMap<String,byte[]> fresh(Path archive,String name,String structureName,String version,String title,String description)throws Exception{
        var files=ReleaseExamples.read(archive);var manifest=ReleaseExamples.json(files.get("worldarchitect.bundle.json"));String[] old=manifest.get("preset").getAsString().split(":",2);var preset=ReleaseExamples.json(files.remove("data/"+old[0]+"/worldgen/world_preset/"+old[1]+".json"));
        String path=files.keySet().stream().filter(p->p.contains("/worldgen/structure/")&&p.endsWith(".json")).findFirst().orElseThrow();String[] parts=path.split("/",5);String source=parts[1]+":"+parts[4].substring(0,parts[4].length()-5),id="worldarchitect_examples:"+structureName;
        var structure=ReleaseExamples.json(files.remove(path));replace(preset,source,id);for(var module:structure.getAsJsonArray("modules"))module.getAsJsonObject().getAsJsonObject("template").getAsJsonObject("worldarchitect").addProperty("author","GWSHERIDAN");
        files.put("data/worldarchitect_examples/worldgen/structure/"+structureName+".json",ReleaseExamples.bytes(structure));files.put("data/worldarchitect_examples/worldgen/world_preset/"+name+".json",ReleaseExamples.bytes(preset));manifest.addProperty("preset","worldarchitect_examples:"+name);files.put("worldarchitect.bundle.json",ReleaseExamples.bytes(manifest));metadata(files,version,title,description);return files;
    }
    private static void tower(SortedMap<String,byte[]> files){
        String path="data/worldarchitect_examples/worldgen/structure/vertical_tower.json";var structure=ReleaseExamples.json(files.get(path));var pool=structure.getAsJsonObject("assembly_pool");pool.addProperty("max_modules",3);
        var constraints=pool.getAsJsonObject("constraints");var limits=new JsonArray();for(String module:List.of("middle","top","bottom")){var limit=new JsonObject();limit.addProperty("module",module);limit.addProperty("min",module.equals("bottom")?0:1);limit.addProperty("max",module.equals("bottom")?0:1);limits.add(limit);}constraints.add("modules",limits);
        var ports=pool.getAsJsonArray("connections");ports.asList().removeIf(v->v.getAsJsonObject().get("module").getAsString().equals("junction")&&v.getAsJsonObject().get("facing").getAsString().equals("down"));for(var value:ports){var port=value.getAsJsonObject();port.add("pos",array(2,port.get("facing").getAsString().equals("up")?4:0,2));}
        var anchor=new JsonObject();anchor.addProperty("version",1);anchor.addProperty("mode","seabed");structure.add("placement",anchor);
        for(var value:structure.getAsJsonArray("modules")){var module=value.getAsJsonObject();String name=module.get("name").getAsString();var tag=module.getAsJsonObject("template");boolean up=!name.equals("top"),down=name.equals("middle")||name.equals("top");
            var palette=new JsonArray();palette.add(state("stone_bricks",null));palette.add(state("ladder","east"));palette.add(state("chest","east"));palette.add(state("oak_stairs","east"));tag.add("palette",palette);tag.add("size",array(5,5,5));var blocks=new JsonArray();
            for(int y=0;y<5;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++){
                boolean shaft=z==2&&(x==1||x==2);int index=-1;
                if(y==0&&!(down&&shaft)||y==4&&!(up&&shaft)||y>0&&y<4&&(x==0||x==4||z==0||z==4))index=0;
                if(name.equals("junction")&&z==0&&x==2&&(y==1||y==2))index=-1;
                if(x==1&&z==2&&(y<4||up))index=1;
                if(name.equals("junction")&&x==3&&z==1&&y==0)index=3;
                if(name.equals("junction")&&x==3&&z==1&&y==1)index=2;
                if(index<0)continue;var block=new JsonObject();block.add("pos",array(x,y,z));block.addProperty("state",index);if(index==2){var nbt=new JsonObject();nbt.addProperty("CustomName","Tower supplies");block.add("nbt",nbt);}blocks.add(block);
            }
            tag.add("blocks",blocks);var meta=tag.getAsJsonObject("worldarchitect");meta.addProperty("title",switch(name){case "junction"->"Ground floor";case "middle"->"Upper floor";case "top"->"Roof room";default->"Base cap";});meta.getAsJsonObject("rules").remove("terrain");meta.getAsJsonObject("markers").getAsJsonObject("entry").add("pos",array(1,1,1));
        }
        files.put(path,ReleaseExamples.bytes(structure));
    }
    private static SortedMap<String,byte[]> combined(Path root,SortedMap<String,byte[]> polygon,SortedMap<String,byte[]> vertical,String version)throws Exception{
        var files=ReleaseExamples.read(root.resolve("examples/creator-starters/bundles/skyblock-"+version+".zip"));var manifest=ReleaseExamples.json(files.get("worldarchitect.bundle.json"));String[] old=manifest.get("preset").getAsString().split(":",2);var preset=ReleaseExamples.json(files.remove("data/"+old[0]+"/worldgen/world_preset/"+old[1]+".json"));
        var regions=ReleaseExamples.json(polygon.get("data/worldarchitect_examples/worldgen/world_preset/polygon_regions.json"));var stem=regions.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").deepCopy();replace(stem.get("generator"),"minecraft:overworld","worldarchitect_examples:highlands");
        var profile=stem.getAsJsonObject("generator").getAsJsonObject("dimension_profile");if(!profile.has("content"))profile.add("content",new JsonObject());var structures=new JsonArray();var tower=new JsonObject();tower.addProperty("structure","worldarchitect_examples:vertical_tower");tower.addProperty("spacing",16);tower.addProperty("separation",6);structures.add(tower);profile.getAsJsonObject("content").add("structures",structures);
        preset.getAsJsonObject("dimensions").add("worldarchitect_examples:highlands",stem);files.put("data/worldarchitect_examples/worldgen/world_preset/locked_archipelago.json",ReleaseExamples.bytes(preset));
        for(var source:List.of(polygon,vertical))for(var entry:source.entrySet())if(entry.getKey().contains("/worldgen/structure/"))files.put(entry.getKey(),entry.getValue());
        manifest.addProperty("preset","worldarchitect_examples:locked_archipelago");manifest.addProperty("lock","full");manifest.addProperty("allow_structure_library",false);manifest.remove("editable_dimensions");manifest.remove("editable_features");manifest.remove("editable_options");files.put("worldarchitect.bundle.json",ReleaseExamples.bytes(manifest));metadata(files,version,"Locked archipelago","Personal Skyblock islands, native End progression and polygon highlands with vertical towers in a separate dimension.");return files;
    }
    private static JsonObject state(String name,String facing){var state=new JsonObject();state.addProperty("Name","minecraft:"+name);if(facing!=null){var properties=new JsonObject();properties.addProperty("facing",facing);state.add("Properties",properties);}return state;}
    private static JsonArray array(int...values){var array=new JsonArray();for(int value:values)array.add(value);return array;}
    private static void replace(JsonElement value,String old,String replacement){if(value.isJsonObject())for(var entry:value.getAsJsonObject().entrySet()){if(entry.getValue().isJsonPrimitive()&&entry.getValue().getAsJsonPrimitive().isString()&&entry.getValue().getAsString().equals(old))entry.setValue(new JsonPrimitive(replacement));else replace(entry.getValue(),old,replacement);}else if(value.isJsonArray())for(var child:value.getAsJsonArray())replace(child,old,replacement);}
}

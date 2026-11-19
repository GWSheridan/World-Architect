package com.gwsheridan.worldarchitect.worldgen;

import java.nio.file.Path;
import java.util.HashMap;
import net.minecraft.nbt.*;

/** Checks the clean partial bundle's persisted structure, allowed ore edit and locked terrain. */
public final class StudioWorldAudit {
    private record Cell(int x,int y,int z){}
    public static void main(String[] args)throws Exception{
        Path world=Path.of(args[0]);var cache=new HashMap<String,CompoundTag>();
        var snapshot=NbtIo.readCompressed(world.resolve("data/worldarchitect-generation-v1.dat"),NbtAccounter.unlimitedHeap());
        var settings=NbtIo.readCompressed(world.resolve("data/minecraft/world_gen_settings.dat"),NbtAccounter.unlimitedHeap());
        require(snapshot.getCompoundOrEmpty("data").equals(settings.getCompoundOrEmpty("data")),"Snapshot differs");
        var data=settings.getCompoundOrEmpty("data");require(data.getLongOr("seed",0)==99,"Editable seed lost");
        var generator=data.getCompoundOrEmpty("dimensions").getCompoundOrEmpty("minecraft:overworld").getCompoundOrEmpty("generator");
        require(generator.getCompoundOrEmpty("profile").getIntOr("elevation",0)==96,"Locked elevation changed");
        var profile=generator.getCompoundOrEmpty("dimension_profile");require(profile.getCompoundOrEmpty("rule_engine").getListOrEmpty("rules").size()==3,"Locked engine changed");
        require(profile.getCompoundOrEmpty("content").getListOrEmpty("ores").size()==1,"Allowed ore edit lost");
        var origin=CrossDimensionWorldAudit.chunk(world,"minecraft:overworld",0,0,cache);
        var start=origin.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").getCompoundOrEmpty("wa_studio:mini");
        require(start.getStringOr("id","").equals("wa_studio:mini")&&start.getListOrEmpty("Children").size()==1,"Bundled modular template did not generate");
        var overlays=new HashMap<Cell,String>();int cells=0,containers=0;
        var piece=start.getListOrEmpty("Children").getCompoundOrEmpty(0);int[] bounds=piece.getIntArray("BB").orElseThrow();var template=piece.getCompoundOrEmpty("template");var palette=template.getListOrEmpty("palette");
        for(var value:template.getListOrEmpty("blocks")){var cell=(CompoundTag)value;var position=cell.getListOrEmpty("pos");int x=bounds[0]+position.getIntOr(0,0),y=bounds[1]+position.getIntOr(1,0),z=bounds[2]+position.getIntOr(2,0);var expected=palette.getCompoundOrEmpty(cell.getIntOr("state",-1));var chunk=CrossDimensionWorldAudit.chunk(world,"minecraft:overworld",x,z,cache);require(expected.equals(CrossDimensionWorldAudit.block(chunk,x,y,z)),"Template state changed");overlays.put(new Cell(x,y,z),expected.getStringOr("id",""));cells++;
            if(cell.contains("nbt")){var metadata=cell.getCompoundOrEmpty("nbt");var actual=CrossDimensionWorldAudit.entity(chunk,x,y,z);for(var key:metadata.keySet())require(java.util.Objects.equals(metadata.get(key),actual.get(key)),"Template metadata changed");require(metadata.get("CustomName").toString().contains("Sky City Supplies"),"Source chest name lost");containers++;}}
        require(cells==254&&containers==1,"Wrong embedded template");require(CrossDimensionWorldAudit.block(origin,1,98,1).getStringOr("id","").equals("minecraft:air")&&CrossDimensionWorldAudit.block(origin,1,99,1).getStringOr("id","").equals("minecraft:air"),"Unsafe marker");
        int ores=0;long positions=0;
        for(int[] c:new int[][]{{0,0},{1,0},{0,1},{4,4}}){var chunk=CrossDimensionWorldAudit.chunk(world,"minecraft:overworld",c[0]*16,c[1]*16,cache);var sections=FeatureWorldAudit.sections(chunk,-64,384,"block_states",4);
            for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=-64;y<320;y++){var actual=FeatureWorldAudit.block(sections,-64,y,z*16+x);String expected=y<=96?"minecraft:stone":"minecraft:air";expected=overlays.getOrDefault(new Cell(c[0]*16+x,y,c[1]*16+z),expected);if(actual.equals("minecraft:gold_ore")){require(expected.equals("minecraft:stone")&&y>=60&&y<=70,"Allowed ore escaped bounds");ores++;}else require(actual.equals(expected),"Locked terrain changed at "+x+","+y+","+z+": "+actual);positions++;}}
        require(ores>0,"No allowed ores generated");
        System.out.printf("Verified %,d partial-lock positions, %d ore cells, %d embedded template cells, chest metadata and safe marker.%n",positions,ores,cells);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

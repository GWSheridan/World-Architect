package com.gwsheridan.worldarchitect.worldgen;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Compare an upgraded save with the published binary, including configured structures. */
public final class SavedChunkPairAudit {
    public static void main(String[] args)throws Exception{
        var left=Path.of(args[0]);var right=Path.of(args[1]);String dimension=args[2];var a=new HashMap<String,CompoundTag>();var b=new HashMap<String,CompoundTag>();int chunks=0;
        for(String pair:args[3].split(";")){
            String[] coordinates=pair.split(",");int x=Integer.parseInt(coordinates[0]),z=Integer.parseInt(coordinates[1]);
            var old=CrossDimensionWorldAudit.chunk(left,dimension,x*16,z*16,a);var current=CrossDimensionWorldAudit.chunk(right,dimension,x*16,z*16,b);
            require(Objects.equals(old.get("structures"),current.get("structures")),"Structure definitions changed at "+pair);
            require(Objects.equals(old.get("Heightmaps"),current.get("Heightmaps")),"Heightmaps changed at "+pair);
            var sections=new HashMap<Integer,CompoundTag>();for(var value:current.getListOrEmpty("sections")){var section=(CompoundTag)value;sections.put(section.getIntOr("Y",-9999),section);}
            require(old.getListOrEmpty("sections").size()==sections.size(),"Section count changed at "+pair);
            for(var value:old.getListOrEmpty("sections")){
                var section=(CompoundTag)value;var other=sections.get(section.getIntOr("Y",-9999));require(other!=null,"Missing section at "+pair);var differences=new TreeMap<String,Integer>();
                BenchmarkWorldAudit.palette(section.getCompoundOrEmpty("block_states"),other.getCompoundOrEmpty("block_states"),4096,4,"blocks",true,differences,pair);
                BenchmarkWorldAudit.palette(section.getCompoundOrEmpty("biomes"),other.getCompoundOrEmpty("biomes"),64,1,"biomes",true,differences,pair);
            }
            chunks++;
        }
        System.out.println("WA_SAVED_CHUNK_PAIR_PASS dimension="+dimension+" chunks="+chunks+" blocks_biomes_heightmaps_structures=identical");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

package com.gwsheridan.worldsunwritten.structure;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class StructureDistrictsTest {
    private StructureDistricts.Member member(String name,String family,int distance){return new StructureDistricts.Member(name,family,1,distance,Optional.empty());}
    @Test void weightedChoicesAreStableAcrossThreadsAndRespectWeights(){
        var weights=List.of(1,4);var serial=java.util.stream.IntStream.range(0,10000).map(x->StructureDistricts.weightedIndex(weights,17,"villages",new ChunkPos(x,-x))).toArray();
        var parallel=java.util.stream.IntStream.range(0,10000).parallel().map(x->StructureDistricts.weightedIndex(weights,17,"villages",new ChunkPos(x,-x))).toArray();assertArrayEquals(serial,parallel);long first=Arrays.stream(serial).filter(i->i==0).count();assertTrue(first>1750&&first<2250);
    }
    @Test void nearbyCompetingGroupsAlwaysHaveOneOrderIndependentOwner(){
        var a=member("village","settlements",12);var b=member("tower","settlements",8);
        for(int x=-128;x<128;x++){var left=new ChunkPos(x,x);var right=new ChunkPos(x+3,x-4);boolean ab=StructureDistricts.excludedBy(a,left,b,right,19),ba=StructureDistricts.excludedBy(b,right,a,left,19);assertNotEquals(ab,ba);}
    }
    @Test void distanceBoundaryAndDifferentFamiliesDoNotExcludeEachOther(){
        var a=member("village","settlements",5);var b=member("tower","settlements",5);var origin=new ChunkPos(0,0);var edge=new ChunkPos(3,4);
        assertFalse(StructureDistricts.excludedBy(a,origin,b,edge,17));assertFalse(StructureDistricts.excludedBy(b,edge,a,origin,17));assertFalse(StructureDistricts.excludedBy(a,origin,member("tower","caves",32),new ChunkPos(0,1),17));assertFalse(StructureDistricts.excludedBy(a,origin,a,origin,17));
    }
    @Test void invalidWeightsAndUnboundedMetadataAreRefused(){
        for(var weights:List.of(List.<Integer>of(),List.of(0),List.of(101)))assertThrows(IllegalArgumentException.class,()->StructureDistricts.weightedIndex(weights,1,"test",new ChunkPos(0,0)));
        assertTrue(member("bad/name","settlements",5).validate().error().isPresent());assertTrue(member("village","settlements",33).validate().error().isPresent());
    }
}

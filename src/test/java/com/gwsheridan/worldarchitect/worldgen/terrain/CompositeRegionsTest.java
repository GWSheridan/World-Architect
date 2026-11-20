package com.gwsheridan.worldarchitect.worldgen.terrain;

import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.config.RegionalTerrain.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompositeRegionsTest {
    private static final AdvancedTerrain FLAT=new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0);
    private Zone rectangle(String name,int low,int high){return new Zone(name,Mask.RECTANGLE,low,low,high,high,0,0,64,0,FLAT,Optional.empty());}
    private Zone combine(String name,Operation op,String... refs){return new Zone(name,Mask.COMPOSITE,-512,-512,512,512,10,0,120,0,FLAT,Optional.empty(),List.of(),Optional.of(new Composition(op,List.of(refs))));}
    private double distance(RegionalTerrain regions,String name,int x,int z){return regions.distances(x,z).get(name);}
    @Test void booleanMasksAgreeAtInclusiveEdgesAndExcludedHoleBoundaries(){
        var r=new RegionalTerrain(List.of(rectangle("land",-100,100),rectangle("hole",-20,20),combine("shore",Operation.SUBTRACT,"land","hole"),combine("both",Operation.INTERSECTION,"land","hole"),combine("either",Operation.UNION,"land","hole"))).normalized();assertTrue(r.validate().isSuccess());
        for(int x=-112;x<=112;x++)for(int z=-112;z<=112;z++){boolean a=x>=-100&&x<=100&&z>=-100&&z<=100,b=x>=-20&&x<=20&&z>=-20&&z<=20;assertEquals(a&&!b,distance(r,"shore",x,z)>=0);assertEquals(a&&b,distance(r,"both",x,z)>=0);assertEquals(a||b,distance(r,"either",x,z)>=0);}
        assertTrue(distance(r,"shore",20,0)<0);assertEquals(1,distance(r,"shore",21,0));
    }
    @Test void polygonSubtractionUsesFullExteriorDistanceForFeathering(){
        var p=rectangle("hole",-20,20).withVertices(List.of(new Vertex(-20,-20),new Vertex(20,-20),new Vertex(20,20),new Vertex(-20,20)));
        var r=new RegionalTerrain(List.of(rectangle("land",-100,100),p,combine("shore",Operation.SUBTRACT,"land","hole"))).normalized();
        assertEquals(-1,p.edgeDistance(60,0));assertEquals(40,distance(r,"shore",60,0));
        var shore=r.zones().getLast();var feathered=new Zone(shore.name(),shore.mask(),shore.minX(),shore.minZ(),shore.maxX(),shore.maxZ(),10,20,120,0,FLAT,Optional.empty(),List.of(),shore.composition());
        assertEquals(.5,RegionalTerrain.weight(feathered,distance(r,"shore",30,0)));assertEquals(1,RegionalTerrain.weight(feathered,distance(r,"shore",60,0)));
    }
    @Test void cyclesUnknownReferencesAndExcessDepthFailClosed(){
        assertTrue(new RegionalTerrain(List.of(rectangle("a",-100,100),combine("self",Operation.UNION,"a","self"))).validate().error().isPresent());
        assertTrue(new RegionalTerrain(List.of(rectangle("a",-100,100),combine("bad",Operation.UNION,"a","missing"))).validate().error().isPresent());
        var zones=new ArrayList<Zone>(List.of(rectangle("a",-100,100),rectangle("b",-50,50)));String ref="a";
        for(int i=0;i<7;i++){String name="node_"+i;zones.add(combine(name,Operation.UNION,ref,"b"));ref=name;}assertTrue(new RegionalTerrain(zones).validate().isSuccess());zones.add(combine("too_deep",Operation.UNION,ref,"b"));assertTrue(new RegionalTerrain(zones).validate().error().isPresent());
        assertTrue(new RegionalTerrain(List.of(rectangle("a",-100,100),combine("duplicate",Operation.UNION,"a","a"))).validate().error().isPresent());
    }
    @Test void generationFiveIsExplicitAndOlderMaskEncodingStaysUnchanged(){
        var r=new RegionalTerrain(List.of(rectangle("land",-100,100),rectangle("hole",-20,20),combine("shore",Operation.SUBTRACT,"land","hole"))).normalized();var shape=new TerrainShape(64,0,Optional.of(FLAT),Optional.of(r));
        var json=TerrainShape.CODEC.codec().encodeStart(JsonOps.INSTANCE,shape).getOrThrow().getAsJsonObject();assertEquals(5,json.get("generator_version").getAsInt());assertEquals(shape,TerrainShape.CODEC.codec().parse(JsonOps.INSTANCE,json).getOrThrow());json.addProperty("generator_version",4);assertTrue(TerrainShape.CODEC.codec().parse(JsonOps.INSTANCE,json).error().isPresent());
        var old=Zone.CODEC.encodeStart(JsonOps.INSTANCE,rectangle("old",-100,100)).getOrThrow().getAsJsonObject();assertFalse(old.has("composition"));assertEquals(3,new RegionalTerrain(List.of(rectangle("old",-100,100))).version());
    }
    @Test void sharedTerrainSamplingIsDeterministicAcrossChunkEdgesAndThreads(){
        var r=new RegionalTerrain(List.of(rectangle("land",-100,100),rectangle("hole",-20,20),combine("shore",Operation.SUBTRACT,"land","hole"))).normalized();var shape=new TerrainShape(64,0,Optional.of(FLAT),Optional.of(r));var height=LevelHeightAccessor.create(-64,384);
        assertEquals(64,HeightfieldV5.sample(shape,18,0,0,height).top());assertEquals(120,HeightfieldV5.sample(shape,18,21,0,height).top());
        var serial=java.util.stream.IntStream.range(-128,128).map(x->HeightfieldV5.sample(shape,18,x,0,height).top()).toArray();var parallel=java.util.stream.IntStream.range(-128,128).parallel().map(x->HeightfieldV5.sample(shape,18,x,0,height).top()).toArray();assertArrayEquals(serial,parallel);
    }
}

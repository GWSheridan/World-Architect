package com.gwsheridan.worldarchitect.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.config.RegionalTerrain.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;

final class HeightfieldV3Test {
    private static final LevelHeightAccessor HEIGHT=LevelHeightAccessor.create(-64,384);
    private static final AdvancedTerrain FLAT=new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0);
    private Zone zone(String name,Mask mask,int priority,int elevation){return new Zone(name,mask,-256,-256,256,256,priority,64,elevation,0,FLAT,Optional.empty());}
    private TerrainShape shape(Zone... zones){return new TerrainShape(64,0,Optional.of(FLAT),Optional.of(new RegionalTerrain(List.of(zones))));}
    private int top(TerrainShape shape,int x,int z){return HeightfieldV3.sample(shape,18,x,z,HEIGHT).top();}
    @Test void transitionsAreContinuousAtNegativeAndPositiveChunkEdges(){
        var shape=shape(zone("plateau",Mask.RECTANGLE,0,120));
        assertEquals(64,top(shape,-257,0));assertEquals(64,top(shape,-256,0));assertEquals(92,top(shape,-224,0));assertEquals(120,top(shape,-192,0));
        for(int x=-512;x<=512;x++)for(int z=-300;z<=300;z+=16){assertTrue(Math.abs(top(shape,x,z)-top(shape,x-1,z))<=2);assertEquals(top(shape,x,z),top(shape,x,z));}
    }
    @Test void circlesPriorityAndStableTiesHaveExplicitResults(){
        var circle=shape(zone("circle",Mask.CIRCLE,0,120));assertEquals(64,top(circle,250,250));assertEquals(120,top(circle,0,0));
        assertEquals(180,top(shape(zone("low",Mask.RECTANGLE,0,120),zone("high",Mask.CIRCLE,4,180)),0,0));
        assertEquals(120,top(shape(zone("earlier",Mask.RECTANGLE,4,120),zone("later",Mask.CIRCLE,4,180)),0,0));
    }
    @Test void boundsBudgetsAndFloatingLandformsAreRejected(){
        var z=zone("bad",Mask.CIRCLE,0,120);
        assertFalse(new Zone(z.name(),z.mask(),0,0,100,200,0,10,100,0,FLAT,Optional.empty()).validate().isSuccess());
        assertFalse(new RegionalTerrain(List.of(z,z)).validate().isSuccess());
        assertFalse(new RegionalTerrain(Collections.nCopies(33,z)).validate().isSuccess());
        assertFalse(new TerrainShape(64,0,Optional.empty(),Optional.of(new RegionalTerrain(List.of(z)))).validate().isSuccess());
        assertFalse(new Zone("floating",Mask.RECTANGLE,0,0,100,100,0,10,100,0,new AdvancedTerrain(AdvancedTerrain.Landform.FLOATING_ISLANDS,64,0),Optional.empty()).validate().isSuccess());
    }
    @Test void legacyEncodingsRemainUnchangedAndVersionThreeRoundTrips(){
        for(String json:List.of("{\"generator_version\":1,\"elevation\":68,\"relief\":44}","{\"generator_version\":2,\"elevation\":68,\"relief\":44,\"terrain\":{\"landform\":\"mountains\"}}")){
            var input=JsonParser.parseString(json);var decoded=TerrainShape.CODEC.codec().parse(JsonOps.INSTANCE,input).getOrThrow();var encoded=TerrainShape.CODEC.codec().encodeStart(JsonOps.INSTANCE,decoded).getOrThrow();assertEquals(input,encoded);assertTrue(decoded.regions().isEmpty());
        }
        var original=shape(zone("roundtrip",Mask.RECTANGLE,0,120));var json=TerrainShape.CODEC.codec().encodeStart(JsonOps.INSTANCE,original).getOrThrow();assertEquals(3,json.getAsJsonObject().get("generator_version").getAsInt());assertEquals(original,TerrainShape.CODEC.codec().parse(JsonOps.INSTANCE,json).getOrThrow());
    }
}

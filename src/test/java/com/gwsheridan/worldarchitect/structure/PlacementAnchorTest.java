package com.gwsheridan.worldarchitect.structure;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementAnchorTest {
    @Test void anchorsUseDifferentHeightSourcesAndApplyTheOffsetOnce(){
        assertEquals(72,new PlacementAnchor(PlacementAnchor.Mode.SURFACE,0).resolve(68,40,4));
        assertEquals(44,new PlacementAnchor(PlacementAnchor.Mode.SEABED,0).resolve(68,40,4));
        assertEquals(-16,new PlacementAnchor(PlacementAnchor.Mode.FIXED,-20).resolve(68,40,4));
    }
    @Test void versionAndModeSpecificHeightAreValidated(){
        for(String json:new String[]{"{\"version\":2,\"mode\":\"surface\"}","{\"version\":1.5,\"mode\":\"surface\"}","{\"version\":1,\"mode\":\"seabed\",\"fixed_y\":10}","{\"version\":1,\"mode\":\"fixed\",\"fixed_y\":0.5}","{\"version\":1,\"mode\":\"fixed\",\"fixed_y\":4294967296}","{\"version\":1,\"mode\":\"fixed\",\"fixed_y\":2147483647}"})assertTrue(PlacementAnchor.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).error().isPresent());
        var anchor=new PlacementAnchor(PlacementAnchor.Mode.FIXED,-64);assertEquals(anchor,PlacementAnchor.CODEC.parse(JsonOps.INSTANCE,PlacementAnchor.CODEC.encodeStart(JsonOps.INSTANCE,anchor).getOrThrow()).getOrThrow());
    }
}

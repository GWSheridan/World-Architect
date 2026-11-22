package com.gwsheridan.worldsunwritten.rules;

import com.google.gson.*;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.config.RegionalTerrain.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NamedRegionTest {
    private static final Identifier WORLD=Identifier.withDefaultNamespace("overworld"),PLAINS=Identifier.withDefaultNamespace("plains");
    private Zone zone(String name,Mask mask,int priority){return new Zone(name,mask,-64,-64,0,0,priority,0,64,0,new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0),Optional.empty());}
    private RuleSet.Context context(RegionalTerrain regions,int x,int z){return new RuleSet.Context(WORLD,PLAINS,x,64,z,18,Set.of(),Optional.of(regions));}
    private RuleSet.Condition condition(String name,String mode,boolean exclude){return RuleSet.Condition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"named_region\":{\"name\":\""+name+"\",\"mode\":\""+mode+"\",\"exclude\":"+exclude+"}}")).getOrThrow();}
    private RuleSet rules(int version){return RuleSet.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("""
        {"version":%d,"dimension":"minecraft:overworld","rules":[{"id":"home","when":{"named_region":{"name":"circle"}},"action":{"type":"spawn","spawn":{"kind":"fixed","dimension":"minecraft:overworld","position":[-32,64,-32]}}}]}
        """.formatted(version))).getOrThrow();}
    @Test void circleMasksIncludeTheirEdgeButNotBoundingBoxCorners(){
        var regions=new RegionalTerrain(List.of(zone("circle",Mask.CIRCLE,0)));var c=condition("circle","mask",false);
        assertTrue(c.matches(context(regions,-64,-32)));assertTrue(c.matches(context(regions,-32,-32)));
        assertFalse(c.matches(context(regions,-64,-64)));assertFalse(c.matches(context(regions,-65,-32)));
    }
    @Test void winnerUsesStablePriorityAndEditorOrder(){
        var first=zone("circle",Mask.CIRCLE,0);var second=zone("square",Mask.RECTANGLE,0);var c=condition("circle","winner",false);
        assertTrue(c.matches(context(new RegionalTerrain(List.of(first,second)),-32,-32)));
        assertFalse(c.matches(context(new RegionalTerrain(List.of(second,first)),-32,-32)));
        assertFalse(c.matches(context(new RegionalTerrain(List.of(first,zone("square",Mask.RECTANGLE,10))),-32,-32)));
        assertTrue(condition("circle","mask",false).matches(context(new RegionalTerrain(List.of(first,zone("square",Mask.RECTANGLE,10))),-32,-32)));
    }
    @Test void exclusionsCannotTurnMissingRegionsIntoAnUnfilteredMatch(){
        var regions=new RegionalTerrain(List.of(zone("circle",Mask.CIRCLE,0)));
        assertTrue(condition("circle","mask",true).matches(context(regions,-65,-32)));
        assertFalse(condition("missing","mask",true).matches(context(regions,-65,-32)));
        assertFalse(condition("circle","mask",true).matches(new RuleSet.Context(WORLD,PLAINS,100,64,100,18,Set.of())));
    }
    @Test void newFieldsRequireVersionTwoAndRoundTripWithoutChangingLegacyEncoding(){
        assertThrows(IllegalStateException.class,()->rules(1));var r=rules(2);assertEquals(2,r.version());
        assertEquals(r,RuleSet.CODEC.parse(JsonOps.INSTANCE,RuleSet.CODEC.encodeStart(JsonOps.INSTANCE,r).getOrThrow()).getOrThrow());
        var legacy=new RuleSet(WORLD,List.of(new RuleSet.Rule("home",0,List.of(),RuleSet.Condition.ALL,r.rules().getFirst().action())));
        var json=RuleSet.CODEC.encodeStart(JsonOps.INSTANCE,legacy).getOrThrow();assertEquals(1,json.getAsJsonObject().get("version").getAsInt());assertFalse(json.toString().contains("named_region"));
    }
    @Test void inspectorAndEvaluatorAgreeAcrossChunkBoundariesAndNegativeCoordinates(){
        var regions=new RegionalTerrain(List.of(zone("circle",Mask.CIRCLE,0)));var rules=rules(2);
        for(int x=-81;x<=17;x++)for(int z=-81;z<=17;z++){
            var c=context(regions,x,z);assertEquals((rules.matches(c)&1)!=0,RuleInspection.overlay(RuleInspection.Overlay.REGION,rules,0,c));
        }
        assertTrue(rules.regionProblem(Optional.empty()).isPresent());assertTrue(rules.regionProblem(Optional.of(regions)).isEmpty());
    }
    @Test void regionReferencesAreLocalToTheirDimension(){
        var regions=new RegionalTerrain(List.of(zone("circle",Mask.CIRCLE,0)));var r=rules(2);
        assertEquals(0,r.matches(new RuleSet.Context(Identifier.withDefaultNamespace("the_nether"),PLAINS,-32,64,-32,18,Set.of(),Optional.of(regions))));
    }
}

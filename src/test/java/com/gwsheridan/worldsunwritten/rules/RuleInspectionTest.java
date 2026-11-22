package com.gwsheridan.worldsunwritten.rules;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class RuleInspectionTest {
    private static final Identifier WORLD=Identifier.withDefaultNamespace("overworld"),PLAINS=Identifier.withDefaultNamespace("plains");
    private static RuleSet.Rule rule(String id,int priority,String conditions,String requires){
        var json=JsonParser.parseString("{\"id\":\""+id+"\",\"priority\":"+priority+",\"when\":"+conditions+",\"action\":{\"type\":\"spawn\",\"spawn\":{\"kind\":\"fixed\",\"dimension\":\"minecraft:overworld\",\"position\":[0,80,0]}}}").getAsJsonObject();
        if(!requires.isEmpty()){var list=new JsonArray();list.add(requires);json.add("requires",list);}return RuleSet.Rule.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
    }
    private static RuleSet.Context context(int x,int y,int z,long seed,Set<String> mods){return new RuleSet.Context(WORLD,PLAINS,x,y,z,seed,mods);}
    @Test void tracesAndAllConditionCoverageAgreeWithTheGenerationEvaluator(){
        var rules=new RuleSet(WORLD,List.of(rule("gate",20,"{\"mods\":[\"fixture\"],\"min_y\":-20,\"max_y\":80,\"region\":{\"min_x\":-40,\"max_x\":40,\"min_z\":-40,\"max_z\":40}}",""),rule("selected",0,"{\"seed_modulus\":7,\"seed_remainder\":3,\"seed_salt\":91}","gate")));
        int accepted=0,checked=0;
        for(long seed:new long[]{0,18,-915})for(int x=-64;x<=64;x+=8)for(int z=-64;z<=64;z+=8)for(int y:new int[]{-21,-20,40,80,81}){
            var c=context(x,y,z,seed,Set.of("fixture"));long expected=rules.matches(c);var trace=RuleInspection.trace(rules,c);
            for(int i=0;i<2;i++){assertEquals((expected&1L<<i)!=0,trace.get(i).eligible());assertEquals(trace.get(i).eligible(),RuleInspection.overlay(RuleInspection.Overlay.ALL,rules,i,c));assertEquals(trace.get(i).eligible(),trace.get(i).checks().values().stream().allMatch(Boolean::booleanValue));}
            if(trace.get(1).eligible())accepted++;checked++;
        }
        assertTrue(accepted>30);assertEquals(4335,checked);
    }
    @Test void separateFiltersRespectInclusiveNegativeAndRadialBoundaries(){
        var rules=new RuleSet(WORLD,List.of(rule("spatial",0,"{\"biomes\":[\"minecraft:plains\"],\"min_y\":10,\"max_y\":20,\"region\":{\"min_x\":-40,\"max_x\":40,\"min_z\":-40,\"max_z\":40},\"distance\":{\"min\":10,\"max\":50}}","")));
        assertTrue(RuleInspection.overlay(RuleInspection.Overlay.ALL,rules,0,context(-30,10,40,18,Set.of())));
        assertFalse(RuleInspection.overlay(RuleInspection.Overlay.ALL,rules,0,context(-31,10,40,18,Set.of())));
        assertTrue(RuleInspection.overlay(RuleInspection.Overlay.REGION,rules,0,context(40,21,0,18,Set.of())));
        assertFalse(RuleInspection.overlay(RuleInspection.Overlay.HEIGHT,rules,0,context(40,21,0,18,Set.of())));
        assertTrue(RuleInspection.overlay(RuleInspection.Overlay.DISTANCE,rules,0,context(0,20,-10,18,Set.of())));
        assertFalse(RuleInspection.overlay(RuleInspection.Overlay.DISTANCE,rules,0,context(0,20,-9,18,Set.of())));
        assertFalse(RuleInspection.overlay(RuleInspection.Overlay.BIOME,rules,0,new RuleSet.Context(WORLD,Identifier.withDefaultNamespace("forest"),0,15,20,18,Set.of())));
    }
    @Test void explanationsIdentifyMissingDependenciesWithoutClaimingPlacement(){
        var rules=new RuleSet(WORLD,List.of(rule("required",10,"{\"mods\":[\"fixture\"]}",""),rule("dependent",0,"{}","required")));
        var trace=RuleInspection.trace(rules,context(0,20,0,18,Set.of()));assertFalse(trace.get(0).checks().get("required mods"));assertFalse(trace.get(1).checks().get("dependencies"));assertFalse(trace.get(1).eligible());
        String report=String.join("\n",RuleInspection.explain(rules,context(0,20,0,18,Set.of())));assertTrue(report.contains("dependencies: fail"));assertTrue(report.contains("does not place blocks"));
    }
    @Test void invalidDependencyGraphsHaveReportsButCannotProduceAValidTrace(){
        var rules=new RuleSet(WORLD,List.of(rule("broken",0,"{}","missing")));
        assertTrue(String.join("\n",RuleInspection.report(rules,-64,319)).contains("unknown or later rule"));
        assertThrows(IllegalArgumentException.class,()->RuleInspection.trace(rules,context(0,20,0,18,Set.of())));
    }
    @Test void orderReportsFollowStablePriorityAndKeepSpawnWarningsConservative(){
        var rules=new RuleSet(WORLD,List.of(rule("late",0,"{}",""),rule("first",10,"{}",""),rule("second",10,"{\"dimensions\":[\"minecraft:the_nether\"]}","first")));
        String report=String.join("\n",RuleInspection.report(rules,-64,319));assertTrue(report.indexOf("1. first")<report.indexOf("2. second"));assertTrue(report.indexOf("2. second")<report.indexOf("3. late"));assertTrue(report.contains("requires first"));assertFalse(report.contains("excludes its generation dimension"));
    }
}

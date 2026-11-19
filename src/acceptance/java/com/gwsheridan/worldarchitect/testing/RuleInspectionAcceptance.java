package com.gwsheridan.worldarchitect.testing;

import com.google.gson.JsonParser;
import com.gwsheridan.worldarchitect.config.ContentProfile;
import com.gwsheridan.worldarchitect.rules.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

final class RuleInspectionAcceptance {
    static void check(MinecraftServer server) {
        var world=Identifier.withDefaultNamespace("overworld");var plains=Identifier.withDefaultNamespace("plains");
        var context=new RuleSet.Context(world,plains,-16,60,16,18,Set.of("minecraft","worldarchitect"));
        var first=material("first",20,"","");var second=material("second",10,"","");var distinct=material("distinct",0,",\"target\":\"minecraft:dirt\"","");
        var rules=new RuleSet(world,List.of(first,second,distinct));
        var report=String.join("\n",RuleInspection.report(rules,-64,319));
        require(report.contains("second is shadowed by first"),"Missing proven shadow warning");
        require(rules.materials(context).at(60,Blocks.DIRT.defaultBlockState()).is(Blocks.GOLD_BLOCK),"Report disagrees with actual material selection");
        var targeted=new RuleSet(world,List.of(material("stone",20,",\"target\":\"minecraft:stone\"",""),distinct));
        require(!String.join("\n",RuleInspection.report(targeted,-64,319)).contains("shadowed"),"Different targets falsely declared shadowed");
        require(targeted.materials(context).at(60,Blocks.DIRT.defaultBlockState()).is(Blocks.GOLD_BLOCK),"Targeted material evaluation differs");
        var ore=new ContentProfile.Ore(Blocks.DIAMOND_ORE.defaultBlockState(),Optional.empty(),32,64,-64,64,List.of());var busy=new ArrayList<RuleSet.Rule>();
        for(int i=0;i<17;i++)busy.add(new RuleSet.Rule("ore_"+i,0,List.of(),RuleSet.Condition.ALL,new RuleSet.Action(RuleSet.Kind.ORE,Optional.empty(),Optional.empty(),Optional.of(ore),Optional.empty(),Optional.empty(),Optional.empty(),RuleSet.SpawnEvent.INITIAL)));
        var excessive=new RuleSet(world,busy);String budget=String.join("\n",RuleInspection.report(excessive,-64,319));require(budget.contains("34816 / 32768")&&budget.contains("Validation:"),"Work budget warning missing");
        System.out.println("WA_RULE_INSPECTION_MATERIAL_TARGETS_SHADOW_BUDGET_PASS");
    }
    private static RuleSet.Rule material(String id,int priority,String target,String when){return RuleSet.Rule.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"id\":\""+id+"\",\"priority\":"+priority+",\"when\":{"+when+"},\"action\":{\"type\":\"material\",\"material\":\"minecraft:gold_block\""+target+"}}" )).getOrThrow();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

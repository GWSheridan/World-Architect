package com.gwsheridan.worldarchitect.modpack;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.junit.jupiter.api.Test;

class PresetInheritanceTest {
    private static final Identifier OVERWORLD=Identifier.parse("minecraft:overworld"),NETHER=Identifier.parse("minecraft:the_nether");
    private BundleManifest parent(BundleManifest.Lock lock,List<Identifier> dimensions,Map<Identifier,List<BundleManifest.Feature>> features,List<BundleManifest.Option> options){return new BundleManifest(Identifier.parse("wa_test:base"),"Base","Authoring base","Builder","1.0.0",lock,new WorldOptions(17,true,false),dimensions,features,options,List.of("worldarchitect"),false);}
    private PresetStudio.Draft draft(BundleManifest manifest){return new PresetStudio.Draft(manifest,JsonParser.parseString("{\"dimensions\":{}}").getAsJsonObject(),Map.of());}
    private BundleManifest child(BundleManifest parent,BundleManifest.Lock lock,List<Identifier> dimensions,Map<Identifier,List<BundleManifest.Feature>> features,List<BundleManifest.Option> options){return new BundleManifest(Identifier.parse("wa_test:child"),"Child","Derived preset","Builder","1.0.0",lock,parent.options(),dimensions,features,options,parent.requiredMods(),parent.allowStructureLibrary(),Optional.of(PresetInheritance.capture(draft(parent),"a".repeat(64))));}
    @Test void partialParentsPermitNarrowerScopesButNeverWiderOnes(){
        var p=parent(BundleManifest.Lock.PARTIAL,List.of(OVERWORLD),Map.of(NETHER,List.of(BundleManifest.Feature.ORES)),List.of(BundleManifest.Option.SEED));
        var allowed=child(p,BundleManifest.Lock.PARTIAL,List.of(),Map.of(OVERWORLD,List.of(BundleManifest.Feature.STRUCTURES),NETHER,List.of(BundleManifest.Feature.ORES)),List.of());assertTrue(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,allowed).isSuccess());
        for(var bad:List.of(child(p,BundleManifest.Lock.UNLOCKED,List.of(),Map.of(),List.of()),child(p,BundleManifest.Lock.PARTIAL,List.of(NETHER),Map.of(),List.of()),child(p,BundleManifest.Lock.PARTIAL,List.of(),Map.of(NETHER,List.of(BundleManifest.Feature.CAVES)),List.of()),child(p,BundleManifest.Lock.PARTIAL,List.of(),Map.of(),List.of(BundleManifest.Option.BONUS_CHEST))))assertTrue(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,bad).error().isPresent());
        assertTrue(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,child(p,BundleManifest.Lock.FULL,List.of(),Map.of(),List.of())).isSuccess());
    }
    @Test void fullParentsKeepDefaultsDependenciesAndLibraryRestrictions(){
        var p=parent(BundleManifest.Lock.FULL,List.of(),Map.of(),List.of());var child=child(p,BundleManifest.Lock.FULL,List.of(),Map.of(),List.of());
        var changed=new BundleManifest(child.preset(),child.title(),child.description(),child.author(),child.release(),child.lock(),new WorldOptions(18,true,false),List.of(),Map.of(),List.of(),child.requiredMods(),false,child.inheritance());assertTrue(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,changed).error().isPresent());
        var dropped=new BundleManifest(child.preset(),child.title(),child.description(),child.author(),child.release(),child.lock(),child.options(),List.of(),Map.of(),List.of(),List.of(),true,child.inheritance());assertTrue(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,dropped).error().isPresent());
    }
    @Test void inheritedBundlesUseFormatTwoAndCannotMasqueradeAsLegacyBundles(){
        var p=parent(BundleManifest.Lock.UNLOCKED,List.of(),Map.of(),List.of());var c=child(p,BundleManifest.Lock.UNLOCKED,List.of(),Map.of(),List.of());
        var json=BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,c).getOrThrow().getAsJsonObject();assertEquals(2,json.get("format").getAsInt());assertEquals(json,BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,BundleManifest.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow()).getOrThrow());json.addProperty("format",1);assertTrue(BundleManifest.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
        var legacy=BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,p).getOrThrow().getAsJsonObject();assertEquals(1,legacy.get("format").getAsInt());assertFalse(legacy.has("inheritance"));legacy.addProperty("format",2);assertTrue(BundleManifest.CODEC.parse(JsonOps.INSTANCE,legacy).error().isPresent());
    }
    @Test void cyclesAndExcessiveDepthAreRefusedWithoutLoadingParents(){
        var p=parent(BundleManifest.Lock.UNLOCKED,List.of(),Map.of(),List.of());var source=PresetInheritance.capture(draft(p),"a".repeat(64));
        var cycle=p.withInheritance(Optional.of(source));assertTrue(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,cycle).error().isPresent());
        var parents=new ArrayList<PresetInheritance.Parent>();for(int i=0;i<9;i++)parents.add(new PresetInheritance.Parent(Identifier.parse("wa_test:parent_"+i),"1.0.0","a".repeat(64)));var deep=new PresetInheritance(parents,source.policy(),source.baseline(),Map.of());assertTrue(PresetInheritance.CODEC.encodeStart(JsonOps.INSTANCE,deep).error().isPresent());
    }
    @Test void draftMetadataCopiesRetainInheritanceAndOwnTheirBaseline(){
        var p=parent(BundleManifest.Lock.FULL,List.of(),Map.of(),List.of());var c=child(p,BundleManifest.Lock.FULL,List.of(),Map.of(),List.of());var draft=draft(c);var m=new BundleManifest(c.preset(),"Renamed",c.description(),c.author(),c.release(),c.lock(),c.options(),c.editableDimensions(),c.editableFeatures(),c.editableOptions(),c.requiredMods(),c.allowStructureLibrary());assertEquals(c.inheritance(),draft.withManifest(m).manifest().inheritance());
        var source=c.inheritance().orElseThrow();source.baseline().addProperty("changed",true);assertFalse(source.baseline().has("changed"));assertThrows(UnsupportedOperationException.class,()->source.parents().clear());
    }
    @Test void overridesSupportEscapedKeysAndArraysWithoutChangingTheParent(){
        var original=JsonParser.parseString("{\"dimensions\":{\"example:sky/upper\":{\"values\":[1,2,3]}}}").getAsJsonObject();var changed=PresetInheritance.override(original,"/dimensions/example:sky~1upper/values/1",new JsonPrimitive(9),false);assertEquals(9,changed.getAsJsonObject("dimensions").getAsJsonObject("example:sky/upper").getAsJsonArray("values").get(1).getAsInt());assertEquals(2,original.getAsJsonObject("dimensions").getAsJsonObject("example:sky/upper").getAsJsonArray("values").get(1).getAsInt());
        assertThrows(IllegalArgumentException.class,()->PresetInheritance.override(original,"/dimensions/missing/field",new JsonPrimitive(1),false));assertThrows(IllegalArgumentException.class,()->PresetInheritance.override(original,"/bad~2escape",new JsonPrimitive(1),false));
    }
}

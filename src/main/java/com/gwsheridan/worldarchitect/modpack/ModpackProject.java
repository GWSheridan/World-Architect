package com.gwsheridan.worldarchitect.modpack;

import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.structure.IslandBuild;
import com.gwsheridan.worldarchitect.worldgen.*;
import com.gwsheridan.worldarchitect.spawn.*;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/** Detached project edits share the production codecs, inheritance checks and exporter. */
public final class ModpackProject {
    private ModpackProject(){}
    public static PresetStudio.Draft dimension(PresetStudio.Draft source,ResourceKey<LevelStem> key,LevelStem stem,HolderLookup.Provider registries)throws IOException{
        var checked=PresetStudio.validate(source,registries);var dimensions=new LinkedHashMap<>(checked.preset().createWorldDimensions().dimensions());dimensions.put(key,stem);
        var json=WorldPreset.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,registries),new WorldPreset(dimensions)).getOrThrow(IOException::new).getAsJsonObject();
        var m=source.manifest();if(stem.generator() instanceof SkyblockGenerator&&m.pack().isEmpty())m=m.withPack(Optional.of(PackPolicy.DEFAULT));
        var mods=new TreeSet<>(m.requiredMods());collect(json,mods);
        m=new BundleManifest(m.preset(),m.title(),m.description(),m.author(),m.release(),m.lock(),m.options(),m.editableDimensions(),m.editableFeatures(),m.editableOptions(),List.copyOf(mods),m.allowStructureLibrary(),m.inheritance(),m.pack());
        return PresetStudio.validate(new PresetStudio.Draft(m,json,source.templates()),registries).draft();
    }
    private static void collect(com.google.gson.JsonElement value,Set<String> mods){
        if(value.isJsonObject())value.getAsJsonObject().entrySet().forEach(e->collect(e.getValue(),mods));
        else if(value.isJsonArray())value.getAsJsonArray().forEach(e->collect(e,mods));
        else if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()){String text=value.getAsString();int colon=text.indexOf(':');if(colon>0){String ns=text.substring(0,colon);if(com.gwsheridan.worldarchitect.rules.RuleDependencies.loaded().contains(ns))mods.add(ns);}}
    }
    public static PresetStudio.Draft starter(PresetStudio.Draft source,String style,RegistryAccess registries)throws IOException{
        if(!Set.of("Shared Skyblock","Personal islands","Team islands","Empty void","Mixed dimensions").contains(style)&&!com.gwsheridan.worldarchitect.cells.CellRecipes.NAMES.contains(style))throw new IOException("Unknown project starter.");
        var checked=PresetStudio.validate(source,registries);var key=style.equals("Mixed dimensions")?LevelStem.NETHER:LevelStem.OVERWORLD;var old=checked.preset().createWorldDimensions().dimensions().get(key);
        if(old==null)throw new IOException("The starter dimension is missing.");
        var type=old.type().value();var profile=new DimensionProfile(type.minY(),type.height(),Optional.empty());
        var biome=registries.lookupOrThrow(Registries.BIOME).getOrThrow(style.equals("Mixed dimensions")?Biomes.NETHER_WASTES:Biomes.PLAINS);
        net.minecraft.world.level.chunk.ChunkGenerator generator;
        if(com.gwsheridan.worldarchitect.cells.CellRecipes.NAMES.contains(style))generator=new CellGenerator(profile,com.gwsheridan.worldarchitect.cells.CellRecipes.create(style,registries));
        else if(style.equals("Empty void"))generator=new VoidGenerator(biome,profile,Optional.empty(),true);
        else {
            int slots=style.equals("Personal islands")||style.equals("Team islands")?64:0;
            var catalog=new IslandCatalog(List.of(new IslandCatalog.Entry("classic","Classic island","Grass, a sapling and a safe starting platform.","minecraft:grass_block",1,IslandBuild.classic())),"classic","fixed",style.equals("Team islands"),64,128,slots);
            generator=new SkyblockGenerator(biome,profile,catalog);
        }
        return dimension(source,key,new LevelStem(old.type(),generator),registries);
    }
}

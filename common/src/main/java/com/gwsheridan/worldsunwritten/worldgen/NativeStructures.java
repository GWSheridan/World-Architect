package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.platform.Platform;

import com.gwsheridan.worldsunwritten.config.ContentProfile.StructureRule;
import com.gwsheridan.worldsunwritten.structure.ModularStructure;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.structures.EndCityStructure;
import net.minecraft.world.level.levelgen.structure.structures.JungleTempleStructure;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;

public final class NativeStructures {
    private NativeStructures() {}

    public static boolean supported(Structure structure) {
        Class<?> type = structure.getClass();
        return type == DesertPyramidStructure.class || type == JungleTempleStructure.class || type == SwampHutStructure.class
                || type == EndCityStructure.class || type == ModularStructure.class
                || com.gwsheridan.worldsunwritten.api.StructureAdapters.get(structure)
                        .filter(adapter -> adapter.version() > 0 && adapter.supports(structure)).isPresent();
    }

    static Stream<Holder<StructureSet>> placements(Stream<StructureRule> rules) {
        return rules.map(rule -> {
            int salt = rule.structure().unwrapKey().orElseThrow().identifier().toString().hashCode() & Integer.MAX_VALUE;
            return Holder.direct(new StructureSet(rule.structure(),
                    new RandomSpreadStructurePlacement(rule.spacing(), rule.separation(), RandomSpreadType.LINEAR, salt)));
        });
    }

    public static CompoundTag definition(Structure structure, HolderLookup.Provider registries) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        CompoundTag definition = new CompoundTag();
        definition.put("registered", registeredDefinition(structure, registries));
        definition.put("effective_settings", Structure.StructureSettings.CODEC.codec()
                .encodeStart(ops, Platform.get().structureSettings(structure)).getOrThrow());
        ListTag biomes = new ListTag();
        structure.biomes().stream().map(holder -> holder.unwrapKey().orElseThrow().identifier().toString())
                .sorted(Comparator.naturalOrder()).forEach(id -> biomes.add(StringTag.valueOf(id)));
        definition.put("resolved_biomes", biomes);
        return definition;
    }

    public static Tag registeredDefinition(Structure structure, HolderLookup.Provider registries) {
        return Structure.DIRECT_CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), structure).getOrThrow();
    }
}

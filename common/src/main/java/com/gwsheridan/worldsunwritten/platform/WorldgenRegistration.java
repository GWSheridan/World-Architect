package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds;
import com.gwsheridan.worldsunwritten.structure.ModularPiece;
import com.gwsheridan.worldsunwritten.structure.ModularStructure;
import com.gwsheridan.worldsunwritten.worldgen.CellBiomeSource;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import com.gwsheridan.worldsunwritten.worldgen.EndArenaBiomeSource;
import com.gwsheridan.worldsunwritten.worldgen.LayeredGenerator;
import com.gwsheridan.worldsunwritten.worldgen.RegionBiomeSource;
import com.gwsheridan.worldsunwritten.worldgen.SingleBlockGenerator;
import com.gwsheridan.worldsunwritten.worldgen.SkyblockGenerator;
import com.gwsheridan.worldsunwritten.worldgen.VoidGenerator;
import com.gwsheridan.worldsunwritten.worldgen.ZoneBiomeSource;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class WorldgenRegistration {
    public interface Registrar {
        <T> void register(ResourceKey<? extends Registry<T>> registry, String name, Supplier<? extends T> value);
    }

    private WorldgenRegistration() {}

    public static void register(Registrar registrar, boolean legacy) {
        registrar.register(Registries.CHUNK_GENERATOR, "single_block", () -> codec(SingleBlockGenerator.CODEC, legacy));
        registrar.register(Registries.CHUNK_GENERATOR, "layered", () -> codec(LayeredGenerator.CODEC, legacy));
        registrar.register(Registries.CHUNK_GENERATOR, "void", () -> codec(VoidGenerator.CODEC, legacy));
        registrar.register(Registries.CHUNK_GENERATOR, "skyblock", () -> codec(SkyblockGenerator.CODEC, legacy));
        registrar.register(Registries.CHUNK_GENERATOR, "cells", () -> codec(CellGenerator.CODEC, legacy));
        registrar.register(Registries.BIOME_SOURCE, "regions", () -> codec(RegionBiomeSource.CODEC, legacy));
        registrar.register(Registries.BIOME_SOURCE, "terrain_regions", () -> codec(ZoneBiomeSource.CODEC, legacy));
        registrar.register(Registries.BIOME_SOURCE, "end_arena", () -> codec(EndArenaBiomeSource.CODEC, legacy));
        registrar.register(Registries.BIOME_SOURCE, "cells", () -> codec(CellBiomeSource.CODEC, legacy));
        StructureType<ModularStructure> structure = () -> codec(ModularStructure.CODEC, legacy);
        registrar.register(Registries.STRUCTURE_TYPE, "modular", () -> structure);
        registrar.register(Registries.STRUCTURE_PIECE, "module", () -> legacy
                ? (context, tag) -> LegacyCodecs.during(() -> new ModularPiece(context, tag))
                : ModularPiece::new);
    }

    public static StructureType<?> modularStructure(boolean legacy) {
        return BuiltInRegistries.STRUCTURE_TYPE.getValueOrThrow(ResourceKey.create(Registries.STRUCTURE_TYPE, id("modular", legacy)));
    }

    public static StructurePieceType modularPiece(boolean legacy) {
        return BuiltInRegistries.STRUCTURE_PIECE.getValueOrThrow(ResourceKey.create(Registries.STRUCTURE_PIECE, id("module", legacy)));
    }

    private static Identifier id(String name, boolean legacy) {
        return Identifier.fromNamespaceAndPath(legacy ? LegacyIds.NAMESPACE : WorldsUnwritten.ID, name);
    }

    private static <T> MapCodec<T> codec(MapCodec<T> codec, boolean legacy) {
        return legacy ? LegacyCodecs.alias(codec) : LegacyCodecs.canonical(codec);
    }
}

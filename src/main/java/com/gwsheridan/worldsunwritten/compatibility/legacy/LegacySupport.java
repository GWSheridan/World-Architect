package com.gwsheridan.worldsunwritten.compatibility.legacy;

import com.gwsheridan.worldsunwritten.structure.ModularPiece;
import com.gwsheridan.worldsunwritten.structure.ModularStructure;
import com.gwsheridan.worldsunwritten.worldgen.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Frozen loader/registry endpoints delegate to the current implementation. */
@Mod(LegacyIds.NAMESPACE)
public final class LegacySupport {
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, LegacyIds.NAMESPACE);
    public static final DeferredHolder<StructureType<?>, StructureType<ModularStructure>> MODULAR = STRUCTURES.register("modular", () -> () -> LegacyCodecs.alias(ModularStructure.CODEC));
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, LegacyIds.NAMESPACE);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> MODULE = PIECES.register("module", () -> (context, tag) -> LegacyCodecs.during(() -> new ModularPiece(context, tag)));
    public LegacySupport(IEventBus bus) {
        STRUCTURES.register(bus); PIECES.register(bus);
        var generators = DeferredRegister.<MapCodec<? extends ChunkGenerator>>create(Registries.CHUNK_GENERATOR, LegacyIds.NAMESPACE);
        generators.register("single_block", () -> LegacyCodecs.alias(SingleBlockGenerator.CODEC));
        generators.register("layered", () -> LegacyCodecs.alias(LayeredGenerator.CODEC));
        generators.register("void", () -> LegacyCodecs.alias(VoidGenerator.CODEC));
        generators.register("skyblock", () -> LegacyCodecs.alias(SkyblockGenerator.CODEC));
        generators.register("cells", () -> LegacyCodecs.alias(CellGenerator.CODEC)); generators.register(bus);
        var biomes = DeferredRegister.<MapCodec<? extends BiomeSource>>create(Registries.BIOME_SOURCE, LegacyIds.NAMESPACE);
        biomes.register("regions", () -> LegacyCodecs.alias(RegionBiomeSource.CODEC));
        biomes.register("terrain_regions", () -> LegacyCodecs.alias(ZoneBiomeSource.CODEC));
        biomes.register("end_arena", () -> LegacyCodecs.alias(EndArenaBiomeSource.CODEC));
        biomes.register("cells", () -> LegacyCodecs.alias(CellBiomeSource.CODEC)); biomes.register(bus);
        bus.addListener(com.gwsheridan.worldarchitect.api.DimensionAdapterRegistry::registerRegistry);
        bus.addListener(com.gwsheridan.worldarchitect.api.FeatureAdapters::registerRegistry);
        bus.addListener(com.gwsheridan.worldarchitect.api.StructureAdapters::registerRegistry);
    }
}

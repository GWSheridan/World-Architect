package com.gwsheridan.worldarchitect;

import com.gwsheridan.worldarchitect.api.DimensionAdapterRegistry;
import com.gwsheridan.worldarchitect.compatibility.SaveProtection;
import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.gwsheridan.worldarchitect.modpack.ModpackCommands;
import com.gwsheridan.worldarchitect.skyblock.IslandSpawns;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import com.gwsheridan.worldarchitect.environment.EnvironmentPayload;
import com.gwsheridan.worldarchitect.environment.EnvironmentCommands;
import com.gwsheridan.worldarchitect.environment.WorldEnvironment;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicies;
import com.gwsheridan.worldarchitect.skyblock.IslandAdministration;
import com.gwsheridan.worldarchitect.skyblock.IslandProtection;
import com.gwsheridan.worldarchitect.structure.StructureCommands;
import com.gwsheridan.worldarchitect.structure.ModularStructure;
import com.gwsheridan.worldarchitect.structure.ModularPiece;
import com.gwsheridan.worldarchitect.worldgen.LayeredGenerator;
import com.gwsheridan.worldarchitect.worldgen.RegionBiomeSource;
import com.gwsheridan.worldarchitect.worldgen.SingleBlockGenerator;
import com.gwsheridan.worldarchitect.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(WorldArchitect.ID)
public final class WorldArchitect {
    public static final String ID = "worldarchitect";
    public static final Identifier GENERATOR_ID = Identifier.fromNamespaceAndPath(ID, "single_block");
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, GENERATOR_ID);
    public static final ResourceKey<WorldPreset> LAYERED_PRESET = ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath(ID, "layered"));
    public static final ResourceKey<WorldPreset> DIMENSIONS_PRESET = ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath(ID, "dimensions"));
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ID);
    public static final DeferredHolder<StructureType<?>, StructureType<ModularStructure>> MODULAR_STRUCTURE =
            STRUCTURE_TYPES.register("modular", () -> () -> ModularStructure.CODEC);
    private static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ID);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> MODULAR_PIECE =
            PIECE_TYPES.register("module", () -> ModularPiece::new);

    public WorldArchitect(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
        modBus.addListener(DimensionAdapterRegistry::registerRegistry);
        modBus.addListener(com.gwsheridan.worldarchitect.api.FeatureAdapters::registerRegistry);
        modBus.addListener(com.gwsheridan.worldarchitect.api.StructureAdapters::registerRegistry);
        modBus.addListener(ModpackRuntime::addPack);
        modBus.addListener(EnvironmentPayload::register);
        modBus.addListener(com.gwsheridan.worldarchitect.structure.PlacementPreviewPayload::register);
        modBus.addListener(com.gwsheridan.worldarchitect.studio.StudioViewPayload::register);
        DeferredRegister<MapCodec<? extends ChunkGenerator>> generators = DeferredRegister.create(Registries.CHUNK_GENERATOR, ID);
        generators.register(GENERATOR_ID.getPath(), () -> SingleBlockGenerator.CODEC);
        generators.register(LAYERED_PRESET.identifier().getPath(), () -> LayeredGenerator.CODEC);
        generators.register("void", () -> VoidGenerator.CODEC);
        generators.register(modBus);
        DeferredRegister<MapCodec<? extends BiomeSource>> biomeSources = DeferredRegister.create(Registries.BIOME_SOURCE, ID);
        biomeSources.register("regions", () -> RegionBiomeSource.CODEC);
        biomeSources.register("end_arena", () -> com.gwsheridan.worldarchitect.worldgen.EndArenaBiomeSource.CODEC);
        biomeSources.register(modBus);
        NeoForge.EVENT_BUS.addListener(WorldArchitect::createSpawn);
        NeoForge.EVENT_BUS.addListener(SaveProtection::recordNewWorld);
        NeoForge.EVENT_BUS.addListener(WorldAdministration::load);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::validateSaved);
        NeoForge.EVENT_BUS.addListener(EnvironmentCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldarchitect.diagnostics.DiagnosticCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldarchitect.rules.RuleCommands::register);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::spawnPlacement);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::spawnPosition);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::finalizeSpawn);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::login);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::dimensionChanged);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::respawned);
        NeoForge.EVENT_BUS.addListener(SpawnPolicies::respawn);
        NeoForge.EVENT_BUS.addListener(IslandAdministration::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldarchitect.skyblock.AdministrationPanel::register);
        NeoForge.EVENT_BUS.addListener(IslandProtection::breaking);
        NeoForge.EVENT_BUS.addListener(IslandProtection::placing);
        NeoForge.EVENT_BUS.addListener(IslandProtection::interacting);
        NeoForge.EVENT_BUS.addListener(IslandProtection::opened);
        NeoForge.EVENT_BUS.addListener(IslandProtection::closed);
        NeoForge.EVENT_BUS.addListener(IslandProtection::logout);
        NeoForge.EVENT_BUS.addListener(StructureCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldarchitect.structure.AssemblyStudio::register);
        NeoForge.EVENT_BUS.addListener(ModpackCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldarchitect.modpack.PresetStudioCommands::register);
        NeoForge.EVENT_BUS.addListener(StructureCommands::onLogout);
    }

    private static void createSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getChunkSource().getGenerator() instanceof SolidColumnGenerator generator) {
            BlockPos spawn = generator.initialSpawn(level, level.getChunkSource().randomState().seed())
                    .orElseThrow(() -> new IllegalStateException("World Architect could not find a safe starting island near the origin. "
                            + "Choose a different seed, sea level, or island material."));
            event.getSettings().setSpawn(LevelData.RespawnData.of(level.dimension(), spawn, 0.0F, 0.0F));
            event.setCanceled(true);
        } else if (level.getChunkSource().getGenerator() instanceof VoidGenerator generator) {
            BlockPos spawn = generator.island().map(island -> island.spawn(0)).orElseGet(() -> new BlockPos(8, level.getMinY() + 4, 8));
            event.getSettings().setSpawn(LevelData.RespawnData.of(level.dimension(), spawn, 0.0F, 0.0F));
            event.setCanceled(true);
        }
    }
}

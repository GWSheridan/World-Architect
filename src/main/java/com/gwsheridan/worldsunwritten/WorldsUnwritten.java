package com.gwsheridan.worldsunwritten;

import com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry;
import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.gwsheridan.worldsunwritten.modpack.ModpackCommands;
import com.gwsheridan.worldsunwritten.skyblock.IslandSpawns;
import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import com.gwsheridan.worldsunwritten.environment.EnvironmentPayload;
import com.gwsheridan.worldsunwritten.environment.EnvironmentCommands;
import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import com.gwsheridan.worldsunwritten.spawn.SpawnPolicies;
import com.gwsheridan.worldsunwritten.skyblock.IslandAdministration;
import com.gwsheridan.worldsunwritten.skyblock.IslandProtection;
import com.gwsheridan.worldsunwritten.structure.StructureCommands;
import com.gwsheridan.worldsunwritten.structure.ModularStructure;
import com.gwsheridan.worldsunwritten.structure.ModularPiece;
import com.gwsheridan.worldsunwritten.worldgen.LayeredGenerator;
import com.gwsheridan.worldsunwritten.worldgen.RegionBiomeSource;
import com.gwsheridan.worldsunwritten.worldgen.SingleBlockGenerator;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldsunwritten.worldgen.VoidGenerator;
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

@Mod(WorldsUnwritten.ID)
public final class WorldsUnwritten {
    public static final String ID = "worldsunwritten";
    public static final Identifier GENERATOR_ID = Identifier.fromNamespaceAndPath(ID, "single_block");
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, GENERATOR_ID);
    public static final ResourceKey<WorldPreset> LAYERED_PRESET = ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath(ID, "layered"));
    public static final ResourceKey<WorldPreset> DIMENSIONS_PRESET = ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath(ID, "dimensions"));
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ID);
    public static final DeferredHolder<StructureType<?>, StructureType<ModularStructure>> MODULAR_STRUCTURE =
            STRUCTURE_TYPES.register("modular", () -> () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(ModularStructure.CODEC));
    private static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ID);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> MODULAR_PIECE =
            PIECE_TYPES.register("module", () -> ModularPiece::new);

    public WorldsUnwritten(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
        modBus.addListener(DimensionAdapterRegistry::registerRegistry);
        modBus.addListener(com.gwsheridan.worldsunwritten.api.FeatureAdapters::registerRegistry);
        modBus.addListener(com.gwsheridan.worldsunwritten.api.StructureAdapters::registerRegistry);
        modBus.addListener(ModpackRuntime::addPack);
        modBus.addListener(EnvironmentPayload::register);
        modBus.addListener(com.gwsheridan.worldsunwritten.structure.PlacementPreviewPayload::register);
        modBus.addListener(com.gwsheridan.worldsunwritten.studio.StudioViewPayload::register);
        modBus.addListener(com.gwsheridan.worldsunwritten.workbench.WorkbenchEditPayload::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.workbench.WorkbenchStudio::logout);
        DeferredRegister<MapCodec<? extends ChunkGenerator>> generators = DeferredRegister.create(Registries.CHUNK_GENERATOR, ID);
        generators.register(GENERATOR_ID.getPath(), () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(SingleBlockGenerator.CODEC));
        generators.register(LAYERED_PRESET.identifier().getPath(), () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(LayeredGenerator.CODEC));
        generators.register("void", () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(VoidGenerator.CODEC));
        generators.register("skyblock", () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(com.gwsheridan.worldsunwritten.worldgen.SkyblockGenerator.CODEC));
        generators.register("cells", () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(com.gwsheridan.worldsunwritten.worldgen.CellGenerator.CODEC));
        generators.register(modBus);
        DeferredRegister<MapCodec<? extends BiomeSource>> biomeSources = DeferredRegister.create(Registries.BIOME_SOURCE, ID);
        biomeSources.register("regions", () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(RegionBiomeSource.CODEC));
        biomeSources.register("terrain_regions", () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(com.gwsheridan.worldsunwritten.worldgen.ZoneBiomeSource.CODEC));
        biomeSources.register("end_arena", () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(com.gwsheridan.worldsunwritten.worldgen.EndArenaBiomeSource.CODEC));
        biomeSources.register("cells", () -> com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.canonical(com.gwsheridan.worldsunwritten.worldgen.CellBiomeSource.CODEC));
        biomeSources.register(modBus);
        NeoForge.EVENT_BUS.addListener(WorldsUnwritten::createSpawn);
        NeoForge.EVENT_BUS.addListener(SaveProtection::recordNewWorld);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::tick);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::stopped);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::earned);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::breaking);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::placing);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::interacting);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::piston);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::spawning);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellWorlds::logout);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.cells.CellCommands::register);
        NeoForge.EVENT_BUS.addListener(WorldAdministration::load);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::validateSaved);
        NeoForge.EVENT_BUS.addListener(EnvironmentCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.diagnostics.DiagnosticCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.inspection.InspectorCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.workbench.WorkbenchCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.structure.SiteStudio::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.structure.SiteStudio::logout);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.rules.RuleCommands::register);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::spawnPlacement);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::spawnPosition);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::finalizeSpawn);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::login);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::dimensionChanged);
        NeoForge.EVENT_BUS.addListener(WorldEnvironment::respawned);
        NeoForge.EVENT_BUS.addListener(SpawnPolicies::respawn);
        NeoForge.EVENT_BUS.addListener(IslandAdministration::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.skyblock.IslandSelections::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.skyblock.IslandSelections::login);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.skyblock.IslandSelections::logout);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.skyblock.IslandSelections::tick);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.skyblock.IslandCaptures::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.skyblock.IslandCaptures::logout);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.skyblock.AdministrationPanel::register);
        NeoForge.EVENT_BUS.addListener(IslandProtection::breaking);
        NeoForge.EVENT_BUS.addListener(IslandProtection::placing);
        NeoForge.EVENT_BUS.addListener(IslandProtection::interacting);
        NeoForge.EVENT_BUS.addListener(IslandProtection::opened);
        NeoForge.EVENT_BUS.addListener(IslandProtection::closed);
        NeoForge.EVENT_BUS.addListener(IslandProtection::logout);
        NeoForge.EVENT_BUS.addListener(StructureCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.structure.AssemblyStudio::register);
        NeoForge.EVENT_BUS.addListener(ModpackCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gwsheridan.worldsunwritten.modpack.PresetStudioCommands::register);
        NeoForge.EVENT_BUS.addListener(StructureCommands::onLogout);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCommands::register);
    }

    private static void createSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getChunkSource().getGenerator() instanceof com.gwsheridan.worldsunwritten.worldgen.CellGenerator generator) {
            var planned=generator.spawn(new com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key(0,com.gwsheridan.worldsunwritten.api.CellLayout.Cell.ORIGIN),level);
            var spawn=com.gwsheridan.worldsunwritten.spawn.SafeSpawns.nearby(level,planned).orElseThrow(()->new IllegalStateException("The starting cell needs a safe floor and two blocks of headroom. Add or repair its spawn marker."));
            event.getSettings().setSpawn(LevelData.RespawnData.of(level.dimension(),spawn,0.0F,0.0F));
            event.setCanceled(true);
        } else if (level.getChunkSource().getGenerator() instanceof SolidColumnGenerator generator) {
            BlockPos spawn = generator.initialSpawn(level, level.getChunkSource().randomState().seed())
                    .orElseThrow(() -> new IllegalStateException("Worlds Unwritten could not find a safe starting island near the origin. "
                            + "Choose a different seed, sea level, or island material."));
            event.getSettings().setSpawn(LevelData.RespawnData.of(level.dimension(), spawn, 0.0F, 0.0F));
            event.setCanceled(true);
        } else if (level.getChunkSource().getGenerator() instanceof VoidGenerator generator) {
            BlockPos spawn = generator.layout().map(island -> island.spawn(0)).orElseGet(() -> new BlockPos(8, level.getMinY() + 4, 8));
            event.getSettings().setSpawn(LevelData.RespawnData.of(level.dimension(), spawn, 0.0F, 0.0F));
            event.setCanceled(true);
        }
    }
}

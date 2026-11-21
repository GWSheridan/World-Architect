package com.gwsheridan.worldarchitect.fixture;

import com.gwsheridan.worldarchitect.api.*;
import com.gwsheridan.worldarchitect.environment.EnvironmentProfile;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicy;
import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Built separately against the shipped JAR. Never included in World Architect's source set. */
@Mod(ApiFixture.ID)
public final class ApiFixture {
    public static final String ID = "wa_api_fixture";
    private static final Identifier LAB = Identifier.fromNamespaceAndPath(ID, "lab");
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<Block> SLATE = BLOCKS.registerSimpleBlock("slate", properties -> properties.strength(2));
    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ID);
    private static final net.neoforged.neoforge.registries.DeferredHolder<StructureType<?>, StructureType<Pyramid>> PYRAMID = STRUCTURES.register("pyramid", () -> () -> Pyramid.CODEC);
    public ApiFixture(IEventBus bus) {
        WorldArchitectApi.requireVersion(Integer.getInteger("wa.fixture.apiVersion", 1));
        BLOCKS.register(bus); FEATURES.register("marker", () -> MarkerFeature.CODEC); FEATURES.register(bus); STRUCTURES.register(bus);
        if (!Boolean.getBoolean("wa.fixture.skipDimension")) {
            var adapters = DeferredRegister.create(DimensionAdapterRegistry.KEY, ID);
            adapters.register("lab", () -> new DimensionAdapter() {
                public int version() { return Integer.getInteger("wa.fixture.dimensionVersion", 1); }
                public boolean supportsPortalPolicies() { return true; }
                public boolean supportsCellularTerrain() { return Boolean.getBoolean("wa.fixture.cells"); }
                public Support inspect(ResourceKey<net.minecraft.world.level.dimension.LevelStem> key, net.minecraft.world.level.dimension.LevelStem original) {
                    return new Support(EnumSet.allOf(TerrainMode.class), Optional.of(Biomes.PLAINS), Component.literal("Fixture dimension contract"));
                }
            }); adapters.register(bus);
        }
        if (!Boolean.getBoolean("wa.fixture.skipFeature")) {
            var adapters = DeferredRegister.create(FeatureAdapters.KEY, ID);
            adapters.register("marker", () -> new FeatureAdapter() {
                public int version() { return Integer.getInteger("wa.fixture.featureVersion", 1); }
                public int workLimit(Feature feature) { return feature instanceof MarkerFeature marker && BlockCompatibility.feature(marker.state()).isSuccess() ? 1 : 0; }
            }); adapters.register(bus);
        }
        if (!Boolean.getBoolean("wa.fixture.skipStructure")) {
            var adapters = DeferredRegister.create(StructureAdapters.KEY, ID);
            adapters.register("pyramid", () -> new StructureAdapter() {
                public int version() { return Integer.getInteger("wa.fixture.structureVersion", 1); }
                public boolean supports(net.minecraft.world.level.levelgen.structure.Structure structure) { return structure.getClass() == Pyramid.class; }
            }); adapters.register(bus);
        }
        NeoForge.EVENT_BUS.addListener(ApiFixture::commands);
    }
    public record MarkerFeature(BlockState state) implements Feature {
        public static final MapCodec<MarkerFeature> CODEC = BlockCompatibility.TERRAIN_CODEC.fieldOf("state").xmap(MarkerFeature::new, MarkerFeature::state);
        public MapCodec<? extends Feature> codec() { return CODEC; }
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) { return level.setBlock(pos, state, 2); }
    }
    public static final class Pyramid extends DesertPyramidStructure {
        public static final MapCodec<Pyramid> CODEC = simpleCodec(Pyramid::new);
        public Pyramid(StructureSettings settings) { super(settings); }
        public StructureType<?> type() { return PYRAMID.get(); }
    }
    private static void commands(RegisterCommandsEvent event) {
        // Intentionally public command: the API, rather than this command tree, must reject low permissions.
        event.getDispatcher().register(Commands.literal("wa_fixture").then(Commands.literal("verify").executes(c -> {
            var source = c.getSource(); var server = source.getServer();
            try {
                var draft = PresetApi.capture(source, "fixture", "1");
                var report = PresetApi.validate(draft, server.registryAccess());
                if (report.report().isEmpty()) throw new IllegalStateException("No dependency report");
                try (var input = ApiFixture.class.getResourceAsStream("/data/wa_api_fixture/fixture-module.json")) {
                    var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    var modular = StructureTemplates.modular(json, server.registryAccess());
                    if (!modular.hasMarker("main", "entry") || modular.blockCount() == 0) throw new IllegalStateException("Modular template/marker API failed");
                    var nativeTag = net.minecraft.nbt.CompoundTag.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json.getAsJsonArray("modules").get(0).getAsJsonObject().get("template")).getOrThrow();
                    if (StructureTemplates.read(nativeTag, server.registryAccess()).blocks().isEmpty()) throw new IllegalStateException("Template API failed");
                }
                var denied = source.withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS);
                try { EnvironmentApi.set(denied, LAB, EnvironmentProfile.DEFAULT); throw new AssertionError("Permission bypass"); } catch (SecurityException expected) {}
                try { SpawnApi.clear(denied, SpawnApi.Phase.INITIAL, LAB); throw new AssertionError("Spawn permission bypass"); } catch (SecurityException expected) {}
                try { PresetApi.capture(denied, "denied", "1"); throw new AssertionError("Preset permission bypass"); } catch (SecurityException expected) {}
                var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, LAB));
                if (level == null || BlockCompatibility.feature(SLATE.get().defaultBlockState()).isError()) throw new IllegalStateException("Fixture dimension/block missing");
                var env = new EnvironmentProfile(new EnvironmentProfile.Time(EnvironmentProfile.TimeMode.FIXED, 6000), EnvironmentProfile.Weather.VANILLA, false, true, false, EnvironmentProfile.Portal.NONE, EnvironmentProfile.Visual.DEFAULT);
                EnvironmentApi.set(source, LAB, env);
                try {
                    EnvironmentApi.set(source, LAB, new EnvironmentProfile(new EnvironmentProfile.Time(EnvironmentProfile.TimeMode.FIXED, 24000), env.weather(), false, true, false, env.portal(), env.visual()));
                    throw new AssertionError("Invalid environment accepted");
                } catch (IllegalArgumentException expected) {}
                SpawnApi.set(source, SpawnApi.Phase.RESPAWN, SpawnPolicy.simple(SpawnPolicy.Kind.FIXED, LAB, new BlockPos(8, 40, 8)));
                var threadRejected = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try { EnvironmentApi.profile(level); return false; } catch (IllegalStateException expected) { return true; }
                }).join();
                if (!threadRejected) throw new AssertionError("Thread guard bypass");
                source.sendSuccess(() -> Component.literal("WA_API_FIXTURE_PASS api=1 adapters=dimension,structure,feature blocks,preset,spawn,environment permissions,thread"), false);
                return 1;
            } catch (Exception e) { throw new IllegalStateException("External API fixture failed", e); }
        })).then(Commands.literal("persisted").executes(c -> {
            var server = c.getSource().getServer();var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, LAB));
            if (EnvironmentApi.profile(level).time().ticks() != 6000 || SpawnApi.policy(server, SpawnApi.Phase.RESPAWN, LAB).isEmpty()) throw new IllegalStateException("API administration did not persist");
            c.getSource().sendSuccess(() -> Component.literal("WA_API_PERSISTENCE_PASS"), false);return 1;
        })));
    }
}

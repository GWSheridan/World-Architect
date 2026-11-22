package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldsunwritten.config.ContentProfile;
import com.gwsheridan.worldsunwritten.config.DimensionProfile;
import com.gwsheridan.worldsunwritten.config.IslandProfile;
import com.gwsheridan.worldsunwritten.config.IslandTemplate.Cell;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

/** Empty chunks except for explicitly configured, immutable starter structures. */
public class VoidGenerator extends ChunkGenerator implements ManagedGenerator {
    protected final boolean legacyEncoding = com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing();
    public final boolean legacyEncoding() { return legacyEncoding; }
    private static final int VERSION = 1;
    public static final MapCodec<VoidGenerator> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Codec.intRange(VERSION, VERSION).optionalFieldOf("generator_version", VERSION).forGetter(generator -> VERSION),
            Biome.CODEC.fieldOf("biome").forGetter(VoidGenerator::biome),
            DimensionProfile.CODEC.fieldOf("dimension").forGetter(generator -> generator.dimension),
            IslandProfile.CODEC.optionalFieldOf("island").forGetter(VoidGenerator::island),
            Codec.BOOL.optionalFieldOf("allow_unsafe_spawn", false).forGetter(VoidGenerator::allowUnsafeSpawn)
    ).apply(builder, (version, biome, dimension, island, unsafe) -> new VoidGenerator(biome, dimension, island, unsafe)));

    private final Holder<Biome> biome;
    private final DimensionProfile dimension;
    private final Optional<IslandProfile> island;
    private final boolean allowUnsafeSpawn;
    private final Map<ChunkPos, List<Cell>> islandChunks;

    public VoidGenerator(Holder<Biome> biome, DimensionProfile dimension, Optional<IslandProfile> island, boolean allowUnsafeSpawn) {
        super(dimension.endProgression().<net.minecraft.world.level.biome.BiomeSource>map(
                arena -> new EndArenaBiomeSource(new FixedBiomeSource(biome), arena.biome())).orElseGet(() -> new FixedBiomeSource(biome)), ignored -> BiomeGenerationSettings.EMPTY);
        this.biome = biome;
        this.dimension = dimension;
        this.island = island;
        this.allowUnsafeSpawn = allowUnsafeSpawn;
        islandChunks = island.map(config -> config.template().blocks().stream().collect(Collectors.groupingBy(
                cell -> ChunkPos.containing(cell.pos())))).orElseGet(Map::of);
    }

    public Holder<Biome> biome() {
        return biome;
    }

    public Optional<IslandProfile> island() {
        return island;
    }

    public Optional<com.gwsheridan.worldsunwritten.api.IslandLayout> layout() { return island.map(value -> value); }

    public VoidGenerator withDimensionProfile(DimensionProfile profile) { return new VoidGenerator(biome, profile, island, allowUnsafeSpawn); }

    public boolean allowUnsafeSpawn() {
        return allowUnsafeSpawn;
    }

    @Override
    public Optional<DimensionProfile> dimensionProfile() {
        return Optional.of(dimension);
    }

    @Override
    public TerrainMode terrainMode() {
        return island.isPresent() ? TerrainMode.SKYBLOCK : TerrainMode.VOID;
    }

    @Override
    public Optional<String> profileProblem() {
        var regionProblem=dimension.rules().regionProblem(Optional.empty());if(regionProblem.isPresent())return regionProblem;
        if (dimension.rules().ordered().stream().anyMatch(rule -> rule.action().type() != com.gwsheridan.worldsunwritten.rules.RuleSet.Kind.SPAWN)) {
            return Optional.of("Void dimensions support spawn rules; material, feature and template rules require managed solid terrain.");
        }
        if (!dimension.content().equals(ContentProfile.EMPTY)) {
            return Optional.of("Void and Skyblock require empty natural generation rules.");
        }
        return island.flatMap(config -> config.originY() < getMinY()
                || config.originY() + config.template().size().getY() > getMinY() + getGenDepth()
                ? Optional.of("The entire island template must fit inside the dimension's vertical range.") : Optional.empty());
    }

    @Override
    protected MapCodec<? extends VoidGenerator> codec() {
        return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.serialized(CODEC, legacyEncoding);
    }

    @Override
    public int getMinY() {
        return dimension.minY();
    }

    @Override
    public int getGenDepth() {
        return dimension.height();
    }

    @Override
    public int getSeaLevel() {
        return getMinY();
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState state, long seed) {
        return ChunkGeneratorStructureState.createForFlat(state, seed, getOrigin(state), biomeSource, Stream.empty());
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (endProgression().isPresent() && com.gwsheridan.worldsunwritten.config.EndProgression.contains(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ())) {
            endProgression().orElseThrow().generator().applyBiomeDecoration(level, chunk, structures);
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState state,
            StructureManager structures, BiomeManager biomes, @Nullable WorldGenRegion region, Set<Holder<Biome>> possibleBiomes) {
        if (endProgression().isPresent() && com.gwsheridan.worldsunwritten.config.EndProgression.contains(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ())) {
            var arena = endProgression().orElseThrow();
            return arena.generator().buildTerrain(chunk, blender, arena.state(state.seed()), structures, biomes, region, Set.of(arena.biome()));
        }
        if (island.isPresent()) {
            IslandProfile config = island.orElseThrow();
            int x = chunk.getPos().getMinBlockX();
            int z = chunk.getPos().getMinBlockZ();
            int slot = config.slotAt(x, z);
            if (slot >= 0) {
                BlockPos origin = config.origin(slot);
                var floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
                var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
                for (Cell cell : cells(x - origin.getX(), z - origin.getZ())) {
                    BlockPos pos = origin.offset(cell.pos());
                    if (chunk.isInsideBuildHeight(pos)) {
                        chunk.setBlockState(pos, cell.state(), Block.UPDATE_ALL);
                        floor.update(pos.getX() & 15, pos.getY(), pos.getZ() & 15, cell.state());
                        surface.update(pos.getX() & 15, pos.getY(), pos.getZ() & 15, cell.state());
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state) {
        if (endProgression().isPresent() && com.gwsheridan.worldsunwritten.config.EndProgression.contains(x, z)) {
            var arena = endProgression().orElseThrow();
            return arena.generator().getBaseHeight(x, z, type, height, arena.state(state.seed()));
        }
        return baseHeight(x, z, type, height);
    }

    int baseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height) {
        int top = height.getMinY();
        if (island.isPresent()) {
            IslandProfile config = island.orElseThrow();
            int slot = config.slotAt(x, z);
            if (slot >= 0) {
                BlockPos origin = config.origin(slot);
                int localX = x - origin.getX();
                int localZ = z - origin.getZ();
                for (Cell cell : cells(localX, localZ)) {
                    int y = origin.getY() + cell.pos().getY();
                    if (cell.pos().getX() == localX && cell.pos().getZ() == localZ
                            && height.isInsideBuildHeight(y) && type.isOpaque().test(cell.state())) {
                        top = Math.max(top, y + 1);
                    }
                }
            }
        }
        return top;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
        return baseColumn(x, z, height, state.seed());
    }

    public NoiseColumn baseColumn(int x, int z, LevelHeightAccessor height, long seed) {
        if (endProgression().isPresent() && com.gwsheridan.worldsunwritten.config.EndProgression.contains(x, z)) {
            var arena = endProgression().orElseThrow();
            return arena.generator().getBaseColumn(x, z, height, arena.state(seed));
        }
        BlockState[] column = new BlockState[height.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        if (island.isPresent()) {
            IslandProfile config = island.orElseThrow();
            int slot = config.slotAt(x, z);
            if (slot >= 0) {
                BlockPos origin = config.origin(slot);
                int localX = x - origin.getX();
                int localZ = z - origin.getZ();
                for (Cell cell : cells(localX, localZ)) {
                    int depth = origin.getY() + cell.pos().getY() - height.getMinY();
                    if (cell.pos().getX() == localX && cell.pos().getZ() == localZ && depth >= 0 && depth < column.length) {
                        column[depth] = cell.state();
                    }
                }
            }
        }
        return new NoiseColumn(height.getMinY(), column);
    }

    private List<Cell> cells(int localX, int localZ) {
        return islandChunks.getOrDefault(new ChunkPos(localX >> 4, localZ >> 4), List.of());
    }

    @Override
    public void addDebugScreenInfo(List<String> output, RandomState state, BlockPos pos, SamplerContext sampler) {
        output.add(island.isPresent() ? "Worlds Unwritten · Skyblock" : "Worlds Unwritten · Void");
    }
}

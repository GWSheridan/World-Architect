package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.config.ContentProfile;
import com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldarchitect.config.ContentProfile.Sea;
import com.gwsheridan.worldarchitect.config.DimensionProfile;
import com.gwsheridan.worldarchitect.config.SingleBlockProfile;
import com.gwsheridan.worldarchitect.worldgen.terrain.HeightfieldV1;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
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

public abstract class SolidColumnGenerator extends ChunkGenerator implements ManagedGenerator {
    protected final Holder<Biome> biome;
    private final Optional<DimensionProfile> dimensionProfile;
    private final ContentProfile content;
    private final int elevation;
    private final int relief;

    protected SolidColumnGenerator(Holder<Biome> biome, int elevation, int relief, Optional<DimensionProfile> dimensionProfile) {
        super(RegionBiomeSource.forContent(biome, dimensionProfile.map(DimensionProfile::content).orElse(ContentProfile.EMPTY)),
                ignored -> BiomeGenerationSettings.EMPTY);
        this.biome = biome;
        this.elevation = elevation;
        this.relief = relief;
        this.dimensionProfile = dimensionProfile;
        this.content = dimensionProfile.map(DimensionProfile::content).orElse(ContentProfile.EMPTY);
    }

    @Override
    public final Optional<DimensionProfile> dimensionProfile() {
        return dimensionProfile;
    }

    @Override
    public final TerrainMode terrainMode() {
        return this instanceof LayeredGenerator ? TerrainMode.LAYERED : TerrainMode.SINGLE_BLOCK;
    }

    @Override
    public final Optional<String> profileProblem() {
        return contentProblem();
    }

    public final Holder<Biome> fallbackBiome() {
        return biome;
    }

    public final ContentProfile content() {
        return content;
    }

    public abstract SolidColumnGenerator withDimensionProfile(DimensionProfile profile);

    public Optional<String> contentProblem() {
        int min = getMinY();
        int max = min + getGenDepth() - 1;
        if (content.sea().isPresent()) {
            var sea = content.sea().orElseThrow();
            if (sea.level() < min || sea.level() > max - 3) {
                return Optional.of("The sea level must be inside the dimension with three blocks of headroom.");
            }
            if (sea.floodCaves() && content.caves().isEmpty()) {
                return Optional.of("Flooding caves requires an enabled cave rule.");
            }
        }
        if (content.caves().isPresent()) {
            var cave = content.caves().orElseThrow();
            if (cave.minY() < min || cave.maxY() > max) {
                return Optional.of("The cave height range must fit inside the dimension.");
            }
            for (int depth = 0; depth < getGenDepth(); depth++) {
                if (materialAtDepth(depth).getBlock() instanceof FallingBlock) {
                    return Optional.of("Caves require terrain blocks that cannot fall.");
                }
            }
        }
        if (content.ores().stream().anyMatch(rule -> rule.minY() < min || rule.maxY() > max)) {
            return Optional.of("Every ore height range must fit inside the dimension.");
        }
        if (content.decorations().stream().anyMatch(rule -> {
            int lower = rule.minY().orElse(min);
            int upper = rule.maxY().orElse(max);
            return lower < min || upper > max || lower > upper;
        })) {
            return Optional.of("Every decoration height range must fit inside the dimension.");
        }
        for (var rule : content.structures()) {
            var structure = rule.structure();
            if (!structure.isBound() || !NativeStructures.supported(structure.value())) {
                String id = structure.unwrapKey().map(key -> key.identifier().toString()).orElse("(unregistered)");
                return Optional.of("Structure " + id + " has no compatible placement implementation.");
            }
        }
        return Optional.empty();
    }

    protected abstract BlockState materialAtDepth(int depth);

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState state, long seed) {
        return ChunkGeneratorStructureState.createForFlat(state, seed, getOrigin(state), biomeSource, NativeStructures.placements(content.structures().stream()));
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (!content.structures().isEmpty()) {
            super.applyBiomeDecoration(level, chunk, structures);
        }
        ContentPlacement.decorate(level, chunk, this, content.decorations());
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState state,
            StructureManager structures, BiomeManager biomes, @Nullable WorldGenRegion region, Set<Holder<Biome>> possibleBiomes) {
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockState[] materials = new BlockState[chunk.getHeight()];
        int[] surfaces = new int[256];
        long seed = state.seed();
        ContentProfile.Sea sea = content.sea().orElse(null);
        BlockState fluid = sea == null ? Blocks.AIR.defaultBlockState() : sea.fluid().state();
        for (int depth = 0; depth < materials.length; depth++) {
            materials[depth] = materialAtDepth(depth);
        }

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int x = startX + localX;
                int z = startZ + localZ;
                int top = surfaceY(x, z, chunk, seed);
                surfaces[localZ * 16 + localX] = top;
                int fillTop = sea == null ? top : Math.max(top, sea.level());
                CaveSpan cave = cave(x, z, seed, chunk.getMinY(), top);
                for (int y = chunk.getMinY(); y <= fillTop; y++) {
                    BlockState material;
                    if (y > top || cave.contains(y)) {
                        if (sea == null || y > sea.level() || (y <= top && !sea.floodCaves())) {
                            continue;
                        }
                        material = fluid;
                    } else {
                        material = materials[top - y];
                    }
                    chunk.setBlockState(position.set(x, y, z), material, Block.UPDATE_ALL);
                }
                surface.update(localX, fillTop, localZ, fillTop > top ? fluid : materials[0]);
                int floorY = firstFree(top, chunk.getMinY(), fillTop, cave, sea, Heightmap.Types.OCEAN_FLOOR_WG) - 1;
                if (floorY >= chunk.getMinY()) {
                    floor.update(localX, floorY, localZ, blockAt(floorY, top, cave, sea));
                }
            }
        }
        ContentPlacement.placeOres(chunk, seed, content.ores(), surfaces, materials);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state) {
        int top = surfaceY(x, z, height, state.seed());
        var sea = content.sea().orElse(null);
        CaveSpan cave = cave(x, z, state.seed(), height.getMinY(), top);
        int upper = sea == null ? top : Math.max(top, sea.level());
        return firstFree(top, height.getMinY(), upper, cave, sea, type);
    }

    private int firstFree(int top, int minY, int upper, CaveSpan cave, @Nullable Sea sea, Heightmap.Types type) {
        for (int y = upper; y >= minY; y--) {
            if (type.isOpaque().test(blockAt(y, top, cave, sea))) {
                return y + 1;
            }
        }
        return minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
        BlockState[] blocks = new BlockState[height.getHeight()];
        int top = surfaceY(x, z, height, state.seed());
        CaveSpan cave = cave(x, z, state.seed(), height.getMinY(), top);
        var sea = content.sea().orElse(null);
        for (int index = 0; index < blocks.length; index++) {
            blocks[index] = blockAt(height.getMinY() + index, top, cave, sea);
        }
        return new NoiseColumn(height.getMinY(), blocks);
    }

    private BlockState blockAt(int y, int top, CaveSpan cave, @Nullable Sea sea) {
        if (y <= top && !cave.contains(y)) {
            return materialAtDepth(top - y);
        }
        return sea != null && y <= sea.level() && (y > top || sea.floodCaves()) ? sea.fluid().state() : Blocks.AIR.defaultBlockState();
    }

    private CaveSpan cave(int x, int z, long seed, int minY, int top) {
        return content.caves().map(rule -> CaveSpan.sample(rule, seed, x, z, minY, top)).orElse(CaveSpan.NONE);
    }

    final int surfaceY(int x, int z, LevelHeightAccessor height, long seed) {
        int surface = HeightfieldV1.surface(seed, x, z, elevation, relief);
        return Math.clamp(surface, height.getMinY(), height.getMaxY() - 3);
    }

    @Override
    public int getMinY() {
        return dimensionProfile.map(DimensionProfile::minY).orElse(SingleBlockProfile.MIN_Y);
    }

    @Override
    public int getGenDepth() {
        return dimensionProfile.map(DimensionProfile::height).orElse(SingleBlockProfile.HEIGHT);
    }

    @Override
    public int getSeaLevel() {
        return content.sea().map(ContentProfile.Sea::level).orElse(getMinY() - 1);
    }

    @Override
    public void addDebugScreenInfo(List<String> output, RandomState state, BlockPos pos, SamplerContext sampler) {
        output.add("World Architect · " + getTypeNameForDataFixer().map(Object::toString).orElse("solid columns"));
    }
}

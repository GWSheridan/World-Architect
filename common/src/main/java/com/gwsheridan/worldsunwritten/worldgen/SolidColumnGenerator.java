package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.config.AdvancedTerrain.Landform;
import com.gwsheridan.worldsunwritten.config.ContentProfile;
import com.gwsheridan.worldsunwritten.rules.RuleSet;
import com.gwsheridan.worldsunwritten.rules.RuleDependencies;
import com.gwsheridan.worldsunwritten.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldsunwritten.config.ContentProfile.Sea;
import com.gwsheridan.worldsunwritten.config.DimensionProfile;
import com.gwsheridan.worldsunwritten.config.IslandTemplate;
import com.gwsheridan.worldsunwritten.config.LayeredProfile.Anchor;
import com.gwsheridan.worldsunwritten.config.LayeredProfile.Stratum;
import com.gwsheridan.worldsunwritten.config.SingleBlockProfile;
import com.gwsheridan.worldsunwritten.config.TerrainShape;
import com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV1;
import com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV2;
import com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV2.Column;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.EmptyBlockGetter;
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
    protected final boolean legacyEncoding = com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing();
    public final boolean legacyEncoding() { return legacyEncoding; }
    protected final Holder<Biome> biome;
    private final Optional<DimensionProfile> dimensionProfile;
    private final ContentProfile content;
    private final TerrainShape shape;
    private final List<Stratum> strata;
    private final boolean seededTransitions;
    private final RuleSet rules;
    private final List<ContentProfile.StructureRule> structureRules;
    private final boolean hasMaterialRules;
    private final boolean hasDistricts;

    protected SolidColumnGenerator(Holder<Biome> biome, int elevation, int relief, Optional<DimensionProfile> dimensionProfile) {
        this(biome, new TerrainShape(elevation, relief), List.of(), dimensionProfile);
    }

    protected SolidColumnGenerator(Holder<Biome> biome, TerrainShape shape, List<Stratum> strata, Optional<DimensionProfile> dimensionProfile) {
        super(arenaBiomes(biome, dimensionProfile, shape),
                ignored -> BiomeGenerationSettings.EMPTY);
        this.biome = biome;
        this.shape = shape;
        this.strata = strata;
        this.seededTransitions = strata.stream().anyMatch(rule -> rule.transition() != 0);
        this.dimensionProfile = dimensionProfile;
        this.content = dimensionProfile.map(DimensionProfile::content).orElse(ContentProfile.EMPTY);
        this.rules = dimensionProfile.map(DimensionProfile::rules).orElse(RuleSet.EMPTY);
        this.structureRules = RuleExecution.structures(content, rules);
        this.hasDistricts = structureRules.stream().anyMatch(rule->rule.district().isPresent());
        this.hasMaterialRules = rules.ordered().stream().anyMatch(rule -> rule.action().type() == RuleSet.Kind.MATERIAL);
    }

    private static net.minecraft.world.level.biome.BiomeSource arenaBiomes(Holder<Biome> biome, Optional<DimensionProfile> profile, TerrainShape shape) {
        var base = RegionBiomeSource.forContent(biome, profile.map(DimensionProfile::content).orElse(ContentProfile.EMPTY));
        var outer = shape.regions().<net.minecraft.world.level.biome.BiomeSource>map(regions -> new ZoneBiomeSource(base, regions)).orElse(base);
        return profile.flatMap(DimensionProfile::endProgression).<net.minecraft.world.level.biome.BiomeSource>map(
                arena -> new EndArenaBiomeSource(outer, arena.biome())).orElse(outer);
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

    public final RuleSet rules() { return rules; }
    public final Optional<com.gwsheridan.worldsunwritten.config.RegionalTerrain> terrainRegions(){return shape.regions();}
    public final List<ContentProfile.StructureRule> structureRules() { return structureRules; }
    public final boolean hasStructureDistricts(){return hasDistricts;}

    public final RuleSet.Context ruleContext(long seed, int x, int y, int z) {
        Holder<Biome> selected = biomeSource instanceof EndArenaBiomeSource arena ? arena.sample(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)) : biomeSource instanceof RegionBiomeSource regions ? regions.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)) : biomeSource instanceof ZoneBiomeSource zones ? zones.sample(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)) : biome;
        return new RuleSet.Context(rules.dimension(), selected.unwrapKey().orElseThrow().identifier(), x, y, z, seed, RuleDependencies.loaded(),shape.regions());
    }

    public Optional<String> contentProblem() {
        if (shape.validate().error().isPresent()) return Optional.of(shape.validate().error().orElseThrow().message());
        var districtProblem=com.gwsheridan.worldsunwritten.structure.StructureDistricts.problem(structureRules,shape.regions(),true);if(districtProblem.isPresent())return districtProblem;
        if(rules.ordered().stream().flatMap(r->r.action().template().stream()).anyMatch(r->r.district().isPresent()))return Optional.of("District membership belongs in Features → Structures, not a template action.");
        if(rules.ordered().stream().flatMap(r->r.action().template().stream()).anyMatch(r->content.structures().stream().anyMatch(c->c.district().isPresent()&&c.structure().equals(r.structure()))))return Optional.of("A district assembly cannot also be selected by a template rule.");
        var regionProblem=rules.regionProblem(shape.regions());if(regionProblem.isPresent())return regionProblem;
        int min = getMinY();
        int max = min + getGenDepth() - 1;
        boolean floating = shape.advanced().filter(settings -> settings.landform() == Landform.FLOATING_ISLANDS).isPresent();
        if (floating && shape.elevation() <= min) {
            return Optional.of("Floating island elevation must be above the dimension floor.");
        }
        if (strata.stream().anyMatch(rule -> rule.anchor() == Anchor.HEIGHT ? rule.from() < min || rule.to() > max : rule.to() >= getGenDepth())) {
            return Optional.of("Every stratum range must fit inside the dimension.");
        }
        if (strata.stream().anyMatch(rule -> !getBiomeSource().possibleBiomes().containsAll(rule.biomes()))) {
            return Optional.of("Every stratum biome must be enabled in the dimension.");
        }
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
        }
        if (floating || content.caves().isPresent()) {
            for (int depth = 0; depth < getGenDepth(); depth++) {
                if (materialAtDepth(depth).getBlock() instanceof FallingBlock) {
                    return Optional.of(floating ? "Floating islands require terrain blocks that cannot fall." : "Caves require terrain blocks that cannot fall.");
                }
            }
            if (strata.stream().anyMatch(rule -> rule.material().getBlock() instanceof FallingBlock)) {
                return Optional.of("A stratum used with caves or floating islands must use a block that cannot fall.");
            }
            if (rules.ordered().stream().anyMatch(rule -> rule.action().material().filter(material -> material.getBlock() instanceof FallingBlock).isPresent())) {
                return Optional.of("Material rules used with caves or floating islands cannot select falling blocks.");
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
        for (var rule : structureRules) {
            var structure = rule.structure();
            if (!structure.isBound() || !NativeStructures.supported(structure.value())) {
                String id = structure.unwrapKey().map(key -> key.identifier().toString()).orElse("(unregistered)");
                return Optional.of("Structure " + id + " has no compatible placement implementation.");
            }
            if (structure.value() instanceof com.gwsheridan.worldsunwritten.structure.ModularStructure modular
                    && modular.horizontalSpan() > (rule.separation() + 1) * 16) {
                return Optional.of("Modular structure separation must leave enough space for its complete assembly.");
            }
        }
        var extensionProblem = GenerationExtensions.problem(this);
        return extensionProblem.isPresent() ? extensionProblem : RuleExecution.problem(this);
    }

    protected abstract BlockState materialAtDepth(int depth);

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState state, long seed) {
        if(structureRules.stream().anyMatch(r->r.district().isPresent()))return ChunkGeneratorStructureState.createForFlat(state,seed,getOrigin(state),biomeSource,com.gwsheridan.worldsunwritten.structure.StructureDistricts.placements(structureRules));
        return ChunkGeneratorStructureState.createForFlat(state, seed, getOrigin(state), biomeSource, NativeStructures.placements(structureRules.stream()));
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (endProgression().isPresent() && com.gwsheridan.worldsunwritten.config.EndProgression.contains(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ())) {
            endProgression().orElseThrow().generator().applyBiomeDecoration(level, chunk, structures);
            return;
        }
        if (!rules.isEmpty() || !content.nativeFeatures().isEmpty()) {
            ContentPlacement.decorate(level, chunk, this, content.decorations());
            RuleExecution.decorations(this, level, chunk);
            if (!content.nativeFeatures().isEmpty()) GenerationExtensions.place(this, level, chunk);
            if (!structureRules.isEmpty()) super.applyBiomeDecoration(level, chunk, structures);
            return;
        }
        if (!structureRules.isEmpty()) {
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
        if (endProgression().isPresent() && com.gwsheridan.worldsunwritten.config.EndProgression.contains(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ())) {
            var arena = endProgression().orElseThrow();
            return arena.generator().buildTerrain(chunk, blender, arena.state(state.seed()), structures, biomes, region, Set.of(arena.biome()));
        }
        if (shape.advanced().isEmpty() && strata.isEmpty() && !hasMaterialRules && content.caves().isEmpty() && content.sea().isEmpty()) {
            return buildSimpleTerrain(chunk, state.seed());
        }
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockState[] materials = new BlockState[chunk.getHeight()];
        int[] surfaces = new int[256];
        LayerSelection[] selections = strata.isEmpty() && !hasMaterialRules ? null : new LayerSelection[256];
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
                Column column = sampleColumn(x, z, chunk, seed);
                int index = localZ * 16 + localX;
                int top = column.top();
                surfaces[index] = top;
                LayerSelection selection = selectLayers(x, z, top, seed);
                if (selections != null) {
                    selections[index] = selection;
                }
                int fillTop = sea == null ? top : Math.max(top, sea.level());
                CaveSpan cave = cave(x, z, seed, column);
                for (int y = chunk.getMinY(); y <= fillTop; y++) {
                    BlockState material;
                    if (y < column.bottom() || y > top || cave.contains(y)) {
                        if (sea == null || y > sea.level() || (cave.contains(y) && !sea.floodCaves())) {
                            continue;
                        }
                        material = fluid;
                    } else {
                        material = selection == null ? materials[top - y] : selection.material(strata, y, materials[top - y]);
                    }
                    chunk.setBlockState(position.set(x, y, z), material, Block.UPDATE_ALL);
                }
                if (fillTop >= chunk.getMinY()) {
                    surface.update(localX, fillTop, localZ, blockAt(fillTop, column, cave, sea, selection));
                }
                int floorY = firstFree(column, chunk.getMinY(), fillTop, cave, sea, selection, Heightmap.Types.OCEAN_FLOOR_WG) - 1;
                if (floorY >= chunk.getMinY()) {
                    floor.update(localX, floorY, localZ, blockAt(floorY, column, cave, sea, selection));
                }
            }
        }
        ContentPlacement.BaseMaterial baseMaterial = (index, y) -> {
            int top = surfaces[index];
            BlockState base = materials[top - y];
            var selection = selections == null ? null : selections[index];
            return selection == null ? base : selection.material(strata, y, base);
        };
        ContentPlacement.placeOres(chunk, seed, content.ores(), surfaces, baseMaterial);
        if (!rules.isEmpty()) RuleExecution.ores(this, chunk, seed, surfaces, baseMaterial);
        return CompletableFuture.completedFuture(chunk);
    }

    /** The original solid-column case needs no per-block cave/fluid/stratum dispatch. */
    private CompletableFuture<ChunkAccess> buildSimpleTerrain(ChunkAccess chunk, long seed) {
        int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ(), minY = chunk.getMinY();
        var position = new BlockPos.MutableBlockPos();
        var floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        var materials = new BlockState[chunk.getHeight()];
        var surfaces = new int[256];
        for (int depth = 0; depth < materials.length; depth++) materials[depth] = materialAtDepth(depth);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int worldX = startX + x, worldZ = startZ + z;
            var column = sampleColumn(worldX, worldZ, chunk, seed);
            int top = column.top(); surfaces[z * 16 + x] = top;
            for (int y = minY; y <= top; y++) chunk.setBlockState(position.set(worldX, y, worldZ), materials[top - y], Block.UPDATE_ALL);
            surface.update(x, top, z, materials[0]);
            int floorY = firstFree(column, minY, top, CaveSpan.NONE, null, null, Heightmap.Types.OCEAN_FLOOR_WG) - 1;
            if (floorY >= minY) floor.update(x, floorY, z, materials[top - floorY]);
        }
        ContentPlacement.BaseMaterial baseMaterial = (index, y) -> materials[surfaces[index] - y];
        ContentPlacement.placeOres(chunk, seed, content.ores(), surfaces, baseMaterial);
        if (!rules.isEmpty()) RuleExecution.ores(this, chunk, seed, surfaces, baseMaterial);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state) {
        return baseHeight(x, z, type, height, state.seed());
    }

    final int baseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, long seed) {
        if (endProgression().isPresent() && com.gwsheridan.worldsunwritten.config.EndProgression.contains(x, z)) {
            var arena = endProgression().orElseThrow();
            return arena.generator().getBaseHeight(x, z, type, height, arena.state(seed));
        }
        Column column = sampleColumn(x, z, height, seed);
        var sea = content.sea().orElse(null);
        CaveSpan cave = cave(x, z, seed, column);
        int upper = sea == null ? column.top() : Math.max(column.top(), sea.level());
        return firstFree(column, height.getMinY(), upper, cave, sea, selectLayers(x, z, column.top(), seed), type);
    }

    private int firstFree(Column column, int minY, int upper, CaveSpan cave, @Nullable Sea sea,
            @Nullable LayerSelection selection, Heightmap.Types type) {
        for (int y = upper; y >= minY; y--) {
            if (type.isOpaque().test(blockAt(y, column, cave, sea, selection))) {
                return y + 1;
            }
        }
        return minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
        return baseColumn(x, z, height, state.seed());
    }

    public final NoiseColumn baseColumn(int x, int z, LevelHeightAccessor height, long seed) {
        if (endProgression().isPresent() && com.gwsheridan.worldsunwritten.config.EndProgression.contains(x, z)) {
            var arena = endProgression().orElseThrow();
            return arena.generator().getBaseColumn(x, z, height, arena.state(seed));
        }
        BlockState[] blocks = new BlockState[height.getHeight()];
        Column column = sampleColumn(x, z, height, seed);
        CaveSpan cave = cave(x, z, seed, column);
        var sea = content.sea().orElse(null);
        LayerSelection selection = selectLayers(x, z, column.top(), seed);
        for (int index = 0; index < blocks.length; index++) {
            blocks[index] = blockAt(height.getMinY() + index, column, cave, sea, selection);
        }
        return new NoiseColumn(height.getMinY(), blocks);
    }

    private BlockState blockAt(int y, Column column, CaveSpan cave, @Nullable Sea sea, @Nullable LayerSelection selection) {
        if (y >= column.bottom() && y <= column.top() && !cave.contains(y)) {
            BlockState base = materialAtDepth(column.top() - y);
            return selection == null ? base : selection.material(strata, y, base);
        }
        return sea != null && y <= sea.level() && (!cave.contains(y) || sea.floodCaves()) ? sea.fluid().state() : Blocks.AIR.defaultBlockState();
    }

    private CaveSpan cave(int x, int z, long seed, Column column) {
        return content.caves().map(rule -> CaveSpan.sample(rule, seed, x, z, column.bottom(), column.top())).orElse(CaveSpan.NONE);
    }

    final int surfaceY(int x, int z, LevelHeightAccessor height, long seed) {
        return sampleColumn(x, z, height, seed).top();
    }

    public final Optional<BlockPos> initialSpawn(LevelHeightAccessor height, long seed) {
        var settings = shape.advanced().orElse(null);
        if (settings == null || settings.landform() != Landform.FLOATING_ISLANDS) {
            return Optional.of(new BlockPos(8, baseHeight(8, 8, Heightmap.Types.WORLD_SURFACE, height, seed), 8));
        }
        int step = Math.clamp(settings.scale() / 4, 8, 96);
        int limit = 2 * settings.scale() / step;
        int seaLevel = getSeaLevel();
        for (int ring = 0; ring <= limit; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = 8 + dx * step;
                    int z = 8 + dz * step;
                    Column column = sampleColumn(x, z, height, seed);
                    int top = column.top();
                    if (top < column.bottom() || top < seaLevel) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, top, z);
                    BlockState floor = blockAt(top, column, CaveSpan.NONE, null, selectLayers(x, z, top, seed));
                    if (IslandTemplate.safeFloor(floor, EmptyBlockGetter.INSTANCE, pos)) {
                        return Optional.of(pos.above());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Column sampleColumn(int x, int z, LevelHeightAccessor height, long seed) {
        if (shape.version() == com.gwsheridan.worldsunwritten.config.RegionalTerrain.CORRIDOR_VERSION) return com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV6.sample(shape, seed, x, z, height);
        if (shape.version() == com.gwsheridan.worldsunwritten.config.RegionalTerrain.COMPOSITE_VERSION) return com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV5.sample(shape, seed, x, z, height);
        if (shape.version() == com.gwsheridan.worldsunwritten.config.RegionalTerrain.POLYGON_VERSION) return com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV4.sample(shape, seed, x, z, height);
        if (shape.regions().isPresent()) return com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV3.sample(shape, seed, x, z, height);
        var advanced = shape.advanced().orElse(null);
        if (advanced != null) {
            return HeightfieldV2.sample(shape, advanced, seed, x, z, height);
        }
        int surface = HeightfieldV1.surface(seed, x, z, shape.elevation(), shape.relief());
        return new Column(height.getMinY(), Math.clamp(surface, height.getMinY(), height.getMaxY() - 3));
    }

    private @Nullable LayerSelection selectLayers(int x, int z, int top, long seed) {
        if (strata.isEmpty() && !hasMaterialRules) {
            return null;
        }
        int quartX = QuartPos.fromBlock(x);
        int quartZ = QuartPos.fromBlock(z);
        Holder<Biome> columnBiome = shape.regions().isPresent() ? (biomeSource instanceof EndArenaBiomeSource arena ? arena.sample(quartX, 0, quartZ) : ((ZoneBiomeSource)biomeSource).sample(quartX,0,quartZ)) : biomeSource instanceof RegionBiomeSource regions
                ? regions.getNoiseBiome(quartX, 0, quartZ) : biomeSource.possibleBiomes().iterator().next();
        double variation = 0.0;
        if (seededTransitions) {
            var settings = shape.advanced().orElseThrow();
            variation = HeightfieldV2.transition(seed ^ settings.seedSalt(), x, z, settings.scale());
        }
        int[] bounds = new int[2 * strata.size()];
        int mask = 0;
        for (int i = 0; i < strata.size(); i++) {
            Stratum rule = strata.get(i);
            List<Holder<Biome>> filter = rule.biomes();
            if (filter.isEmpty() || filter.contains(columnBiome)) {
                int offset = (int) Math.round(variation * rule.transition());
                if (rule.anchor() == Anchor.HEIGHT) {
                    bounds[2 * i] = rule.from() + offset;
                    bounds[2 * i + 1] = rule.to() + offset;
                } else {
                    bounds[2 * i] = top - rule.to() - offset;
                    bounds[2 * i + 1] = top - rule.from() - offset;
                }
                mask |= 1 << i;
            }
        }
        RuleSet.MaterialColumn materials = rules.isEmpty() ? null : rules.materials(ruleContext(seed, x, top, z));
        return mask == 0 && materials == null ? null : new LayerSelection(mask, bounds, materials);
    }

    private record LayerSelection(int mask, int[] bounds, RuleSet.MaterialColumn materials) {
        private BlockState material(List<Stratum> rules, int y, BlockState fallback) {
            BlockState selected = fallback;
            for (int remaining = mask; remaining != 0; remaining &= remaining - 1) {
                int index = Integer.numberOfTrailingZeros(remaining);
                if (y >= bounds[2 * index] && y <= bounds[2 * index + 1]) {
                    selected = rules.get(index).material();
                    break;
                }
            }
            return materials == null ? selected : materials.at(y, selected);
        }
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
        output.add("Worlds Unwritten · " + getTypeNameForDataFixer().map(Object::toString).orElse("solid columns"));
    }
}

package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldsunwritten.cells.*;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.structure.IslandBuild;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Original cellular terrain. Existing generators and their versioned algorithms are independent.
 */
public final class CellGenerator extends ChunkGenerator implements ManagedGenerator {
    protected final boolean legacyEncoding =
            com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing();

    public final boolean legacyEncoding() {
        return legacyEncoding;
    }

    public static final MapCodec<CellGenerator> CODEC =
            RecordCodecBuilder.mapCodec(
                    b ->
                            b.group(
                                            Codec.intRange(1, 2)
                                                    .optionalFieldOf("generator_version", 1)
                                                    .forGetter(g -> g.cells().version()),
                                            DimensionProfile.CODEC
                                                    .fieldOf("dimension")
                                                    .forGetter(CellGenerator::dimension),
                                            CellWorldProfile.CODEC
                                                    .fieldOf("cells")
                                                    .forGetter(CellGenerator::cells))
                                    .apply(
                                            b,
                                            (v, d, c) -> {
                                                if (v != c.version())
                                                    throw new IllegalArgumentException(
                                                            "Cell generator version must match its"
                                                                    + " campaign definition.");
                                                return new CellGenerator(d, c);
                                            }));
    private final DimensionProfile dimension;
    private final CellWorldProfile cells;
    private final CellBiomeSource cellBiomes;
    private final int seaLevel;
    private final Map<String, LayeredGenerator> terrain = new HashMap<>();
    private final Map<String, Map<Long, List<IslandBuild.Cell>>> templateColumns = new HashMap<>();

    private record BuildPlan(
            Optional<IslandBuild> build,
            BlockPos origin,
            Map<Long, List<IslandBuild.Cell>> columns) {}

    private record PlanKey(Key cell, String type, long seed) {}

    private final Map<PlanKey, BuildPlan> sitePlans =
            Collections.synchronizedMap(new LinkedHashMap<>(32, .75f, true));
    private volatile Set<Key> active = Set.of(new Key(0, Cell.ORIGIN));
    private volatile long seed;
    private volatile java.util.function.Function<ChunkPos, Map<BlockPos, BlockState>>
            connectionBlocks = ignored -> Map.of();

    public void connectionBlocks(
            java.util.function.Function<ChunkPos, Map<BlockPos, BlockState>> source) {
        connectionBlocks = source;
    }

    public CellGenerator(DimensionProfile dimension, CellWorldProfile cells) {
        this(dimension, cells, new CellBiomeSource(cells));
    }

    private CellGenerator(
            DimensionProfile dimension, CellWorldProfile cells, CellBiomeSource source) {
        super(
                dimension
                        .endProgression()
                        .<BiomeSource>map(a -> new EndArenaBiomeSource(source, a.biome()))
                        .orElse(source),
                ignored -> BiomeGenerationSettings.EMPTY);
        this.dimension = dimension;
        this.cells = cells.validate().getOrThrow();
        cellBiomes = source;
        // Registry references can still be unbound while the preset codec constructs this
        // generator.
        seaLevel =
                Math.clamp(
                        cells.tiles().stream()
                                .flatMap(t -> t.content().sea().stream())
                                .mapToInt(ContentProfile.Sea::level)
                                .max()
                                .orElse(63),
                        dimension.minY(),
                        dimension.minY() + dimension.height() - 1);
        for (var tile : cells.tiles())
            terrain.put(
                    tile.id(),
                    new LayeredGenerator(
                            tile.biome(),
                            tile.terrain(),
                            Optional.of(
                                    new DimensionProfile(
                                            dimension.minY(),
                                            dimension.height(),
                                            Optional.empty(),
                                            tile.content(),
                                            com.gwsheridan.worldsunwritten.rules.RuleSet.EMPTY,
                                            Optional.empty()))));
        for (var tile : cells.tiles())
            templateColumns.put(
                    tile.id(),
                    tile.template()
                            .map(
                                    build ->
                                            build.cells().stream()
                                                    .collect(
                                                            java.util.stream.Collectors.groupingBy(
                                                                    c ->
                                                                            columnKey(
                                                                                    c.pos().getX(),
                                                                                    c.pos()
                                                                                            .getZ()))))
                            .orElseGet(Map::of));
    }

    public DimensionProfile dimension() {
        return dimension;
    }

    public CellWorldProfile cells() {
        return cells;
    }

    public void validateAssets(HolderLookup.Provider registries) throws java.io.IOException {
        for (var tile : cells.tiles())
            if (tile.template().isPresent()) {
                var build = tile.template().orElseThrow();
                var checked = build.checked(registries);
                if (!IslandBuild.CODEC
                        .encodeStart(JsonOps.INSTANCE, build)
                        .getOrThrow()
                        .equals(
                                IslandBuild.CODEC
                                        .encodeStart(JsonOps.INSTANCE, checked)
                                        .getOrThrow()))
                    throw new java.io.IOException(
                            "Cell template "
                                    + tile.id()
                                    + " must be sanitized in Island Studio before use.");
            }
        for (var site :
                cells.campaign()
                        .orElse(com.gwsheridan.worldsunwritten.cells.CellCampaign.EMPTY)
                        .sites())
            com.gwsheridan.worldsunwritten.structure.CellSites.validate(
                    site.assembly(), registries);
    }

    public long seed() {
        return seed;
    }

    public CellGenerator withDimensionProfile(DimensionProfile value) {
        return new CellGenerator(value, cells);
    }

    public void active(Set<Key> value) {
        active = Set.copyOf(value);
    }

    public boolean active(Key key) {
        return !cells.progressive() || active.contains(key);
    }

    public boolean contains(Key key, int x, int z) {
        if (campaignEnd() && nativeReservation(x, z)) return false;
        var a = cells.address(x, z);
        return a != null
                && a.territory() == key.territory()
                && a.cell().equals(key.cell())
                && cells.layout().contains(a.cell(), a.localX(), a.localZ());
    }

    public boolean open(int x, int z) {
        if (campaignEnd() && nativeReservation(x, z)) return true;
        var a = cells.address(x, z);
        return a != null
                && active(new Key(a.territory(), a.cell()))
                && cells.layout().contains(a.cell(), a.localX(), a.localZ());
    }

    private boolean arena(int x, int z) {
        return dimension.endProgression().isPresent() && EndProgression.contains(x, z);
    }

    public boolean campaignEnd() {
        return cells.version() == 2
                && cells.progressive()
                && cells.growth().scope() == GrowthRules.Scope.SHARED
                && endProgression().isPresent();
    }

    public Set<Cell> alwaysOpenCells() {
        return campaignEnd() ? CellEndReservations.arenaCells(cells) : Set.of();
    }

    public boolean nativeReservation(int x, int z) {
        return arena(x, z) || campaignEnd() && CellEndReservations.landing(x, z);
    }

    @Override
    protected MapCodec<CellGenerator> codec() {
        return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.serialized(
                CODEC, legacyEncoding);
    }

    @Override
    public Optional<DimensionProfile> dimensionProfile() {
        return Optional.of(dimension);
    }

    @Override
    public TerrainMode terrainMode() {
        return TerrainMode.CELLULAR;
    }

    @Override
    public Optional<String> profileProblem() {
        if (cells.progressive() && getGenDepth() > 512)
            return Optional.of("Progressive cell dimensions support heights up to 512 blocks.");
        if (cells.progressive() && endProgression().isPresent() && !campaignEnd())
            return Optional.of(
                    "Native End expansion requires a version-two campaign with shared"
                            + " progression.");
        if (campaignEnd()
                && alwaysOpenCells().size()
                        == cells.layout().within(cells.growth().radius()).size())
            return Optional.of(
                    "Increase the cell size or territory radius so outer End cells extend beyond"
                            + " the native arena.");
        if (!dimension.content().equals(ContentProfile.EMPTY) || !dimension.rules().isEmpty())
            return Optional.of("Cell worlds keep generation controls inside their cell types.");
        for (var tile : cells.tiles()) {
            if (!tile.content().biomes().isEmpty()
                    || tile.terrain().shape().regions().stream()
                            .flatMap(r -> r.zones().stream())
                            .anyMatch(z -> z.biome().isPresent()))
                return Optional.of(
                        "Select each cell type's biome directly; nested biome-region overrides are"
                                + " not supported.");
            if (!tile.content().structures().isEmpty()
                    || !tile.content().nativeFeatures().isEmpty())
                return Optional.of(
                        "Cell structures use embedded templates so their full footprint stays"
                                + " inside one cell.");
            if (tile.template().isPresent()
                    && (origin(tile, new Key(0, Cell.ORIGIN)).getY() < getMinY()
                            || origin(tile, new Key(0, Cell.ORIGIN)).getY()
                                            + tile.template().orElseThrow().size().getY()
                                    > getMinY() + getGenDepth()))
                return Optional.of("The complete cell template must fit the dimension height.");
        }
        return Optional.empty();
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
        return dimension.endProgression().map(a -> a.generator().getSeaLevel()).orElse(seaLevel);
    }

    @Override
    public ChunkGeneratorStructureState createState(
            HolderLookup<StructureSet> sets, RandomState state, long worldSeed) {
        seed = worldSeed;
        cellBiomes.seed(worldSeed);
        return ChunkGeneratorStructureState.createForFlat(
                state, worldSeed, getOrigin(state), biomeSource, Stream.empty());
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public void addDebugScreenInfo(
            List<String> lines, RandomState state, BlockPos pos, SamplerContext context) {
        var a = cells.address(pos.getX(), pos.getZ());
        lines.add(
                "Cell: "
                        + (a == null
                                ? "outside territory"
                                : a.territory()
                                        + " / "
                                        + a.cell().key()
                                        + " / "
                                        + (open(pos.getX(), pos.getZ()) ? "open" : "locked")));
    }

    @Override
    public void applyBiomeDecoration(
            WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (arena(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ()))
            endProgression()
                    .orElseThrow()
                    .generator()
                    .applyBiomeDecoration(level, chunk, structures);
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(
            ChunkAccess chunk,
            Blender blender,
            RandomState state,
            StructureManager structures,
            BiomeManager biomes,
            @Nullable WorldGenRegion region,
            Set<Holder<Biome>> possible) {
        if (arena(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ())) {
            var a = endProgression().orElseThrow();
            return a.generator()
                    .buildTerrain(
                            chunk,
                            blender,
                            a.state(state.seed()),
                            structures,
                            biomes,
                            region,
                            Set.of(a.biome()));
        }
        fill(chunk, state.seed(), null);
        return CompletableFuture.completedFuture(chunk);
    }

    /** Also used to build a deterministic activation plan without mutating the live chunk. */
    public void fill(ChunkAccess chunk, long worldSeed, @Nullable Key requested) {
        var selectedTypes = new HashMap<Key, CellWorldProfile.Tile>();
        var columns = new NoiseColumn[256];
        var addresses = new CellWorldProfile.Address[256];
        var types = new CellWorldProfile.Tile[256];
        var surfaces = new int[256];
        int sx = chunk.getPos().getMinBlockX(), sz = chunk.getPos().getMinBlockZ();
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                int i = z * 16 + x, wx = sx + x, wz = sz + z;
                var a = cells.address(wx, wz);
                addresses[i] = a;
                if (campaignEnd() && CellEndReservations.landing(wx, wz)) {
                    if (CellEndReservations.floor(wx, wz)) {
                        chunk.setBlockState(
                                pos.set(wx, CellEndReservations.FLOOR, wz),
                                Blocks.END_STONE.defaultBlockState(),
                                Block.UPDATE_CLIENTS);
                        surfaces[i] = CellEndReservations.FLOOR;
                    }
                    continue;
                }
                if (a == null || !cells.layout().contains(a.cell(), a.localX(), a.localZ()))
                    continue;
                var key = new Key(a.territory(), a.cell());
                if (requested == null ? !active(key) : !requested.equals(key)) continue;
                var tile =
                        selectedTypes.computeIfAbsent(
                                key, k -> cells.tile(worldSeed, k.territory(), k.cell()));
                types[i] = tile;
                var column = column(wx, wz, chunk, worldSeed, a, tile);
                columns[i] = column;
                surfaces[i] = getMinY() - 1;
                for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y++) {
                    var block = column.getBlock(y);
                    if (!block.isAir()) {
                        chunk.setBlockState(pos.set(wx, y, wz), block, Block.UPDATE_CLIENTS);
                        if (!block.getFluidState().isEmpty()
                                || block.getBlock() instanceof FallingBlock)
                            chunk.markPosForPostProcessing(pos);
                        surfaces[i] = y;
                    }
                }
                var origin = origin(tile, key, worldSeed);
                for (var cell : templateColumn(tile, key, origin, wx, wz, worldSeed)) {
                    var p = origin.offset(cell.pos());
                    cell.data()
                            .ifPresent(
                                    data -> {
                                        data.putInt("x", p.getX());
                                        data.putInt("y", p.getY());
                                        data.putInt("z", p.getZ());
                                        chunk.setBlockEntityNbt(data);
                                    });
                }
            }
        for (var tile : cells.tiles()) {
            var ores =
                    tile.content().ores().stream()
                            .filter(o -> o.biomes().isEmpty() || o.biomes().contains(tile.biome()))
                            .map(
                                    o ->
                                            new ContentProfile.Ore(
                                                    o.material(),
                                                    o.target(),
                                                    o.attempts(),
                                                    o.size(),
                                                    o.minY(),
                                                    o.maxY(),
                                                    List.of()))
                            .toList();
            ContentPlacement.placeOres(
                    chunk,
                    worldSeed ^ cells.salt(),
                    ores,
                    surfaces,
                    (i, y) ->
                            columns[i] == null
                                    ? Blocks.AIR.defaultBlockState()
                                    : columns[i].getBlock(y),
                    tile.id().hashCode(),
                    p -> types[(p.getZ() & 15) * 16 + (p.getX() & 15)] == tile);
            for (int index = 0; index < tile.content().decorations().size(); index++) {
                var rule = tile.content().decorations().get(index);
                if (!rule.biomes().isEmpty() && !rule.biomes().contains(tile.biome())) continue;
                var random =
                        new SplittableRandom(
                                ContentRandom.seed(
                                        worldSeed ^ cells.salt(),
                                        chunk.getPos().x(),
                                        chunk.getPos().z(),
                                        300L + index + tile.id().hashCode()));
                for (int attempt = 0; attempt < rule.attempts(); attempt++) {
                    int x = random.nextInt(16),
                            z = random.nextInt(16),
                            i = z * 16 + x,
                            y = surfaces[i] + 1;
                    if (random.nextInt(100) >= rule.chance()
                            || types[i] != tile
                            || y < rule.minY().orElse(getMinY())
                            || y > rule.maxY().orElse(chunk.getMaxY())
                            || y > chunk.getMaxY()) continue;
                    pos.set(sx + x, y, sz + z);
                    if (chunk.getBlockState(pos).isAir()
                            && chunk.getBlockState(pos.below()).isSolid())
                        chunk.setBlockState(pos, rule.material(), Block.UPDATE_CLIENTS);
                }
            }
        }
        if (requested == null)
            connectionBlocks
                    .apply(chunk.getPos())
                    .forEach((p, s) -> chunk.setBlockState(p, s, Block.UPDATE_CLIENTS));
        Heightmap.primeHeightmaps(
                chunk, Set.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    private NoiseColumn column(
            int x,
            int z,
            LevelHeightAccessor height,
            long worldSeed,
            CellWorldProfile.Address a,
            CellWorldProfile.Tile tile) {
        var states = new BlockState[height.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        if (tile.kind() != CellWorldProfile.Kind.ISLAND) {
            var base =
                    terrain.get(tile.id())
                            .baseColumn(a.localX(), a.localZ(), height, worldSeed ^ cells.salt());
            for (int i = 0; i < states.length; i++) states[i] = base.getBlock(height.getMinY() + i);
        }
        var centre = cells.layout().centre(a.cell());
        int cx = (int) StrictMath.floor(centre.x()), cz = (int) StrictMath.floor(centre.z());
        if (tile.kind() == CellWorldProfile.Kind.CAVERN
                && Math.abs(a.localX() - cx) <= 4
                && Math.abs(a.localZ() - cz) <= 4) {
            int floor = tile.terrain().shape().elevation() - 20;
            for (int y = floor; y <= floor + 4; y++)
                if (height.isInsideBuildHeight(y))
                    states[y - height.getMinY()] =
                            y == floor
                                    ? Blocks.STONE_BRICKS.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState();
        }
        if (cells.layout().rim(a.cell(), a.localX(), a.localZ())) {
            int surface = height.getMinY();
            for (int i = states.length - 1; i >= 0; i--)
                if (!states[i].isAir()) {
                    surface = height.getMinY() + i;
                    break;
                }
            for (int y = cells.borderToFloor() ? height.getMinY() : surface; y <= surface; y++)
                if (height.isInsideBuildHeight(y)) states[y - height.getMinY()] = cells.border();
        }
        var key = new Key(a.territory(), a.cell());
        var o = origin(tile, key, worldSeed);
        for (var cell : templateColumn(tile, key, o, x, z, worldSeed)) {
            int y = o.getY() + cell.pos().getY();
            if (height.isInsideBuildHeight(y)) states[y - height.getMinY()] = cell.state();
        }
        build(tile, key, worldSeed)
                .ifPresent(
                        build -> {
                            for (var marker : build.markers().values())
                                if (o.getX() + marker.getX() == x && o.getZ() + marker.getZ() == z)
                                    for (int dy = 0; dy < 2; dy++) {
                                        int y = o.getY() + marker.getY() + dy;
                                        if (height.isInsideBuildHeight(y))
                                            states[y - height.getMinY()] =
                                                    Blocks.AIR.defaultBlockState();
                                    }
                        });
        return new NoiseColumn(height.getMinY(), states);
    }

    private boolean sites() {
        return cells.campaign().map(c -> !c.sites().isEmpty()).orElse(false);
    }

    public Optional<IslandBuild> build(CellWorldProfile.Tile tile, Key key) {
        return build(tile, key, seed);
    }

    public Optional<IslandBuild> build(CellWorldProfile.Tile tile, Key key, long worldSeed) {
        return sites() ? sitePlan(tile, key, worldSeed).build() : tile.template();
    }

    private BuildPlan sitePlan(CellWorldProfile.Tile tile, Key key, long worldSeed) {
        synchronized (sitePlans) {
            var cacheKey = new PlanKey(key, tile.id(), worldSeed);
            var cached = sitePlans.get(cacheKey);
            if (cached != null) return cached;
            Optional<IslandBuild> chosen = tile.template();
            boolean procedural = false;
            long sample =
                    CellWorldProfile.mix(
                            worldSeed
                                    ^ cells.salt()
                                    ^ ((long) key.territory() * 0x632be59bd9b4e019L)
                                    ^ ((long) key.cell().q() * 0x9e3779b97f4a7c15L)
                                    ^ ((long) key.cell().r() * 0xc2b2ae3d27d4eb4fL));
            for (var site : cells.campaign().orElseThrow().sites())
                if (site.type().equals(tile.id())
                        && Long.remainderUnsigned(sample, 100) < site.chance())
                    try {
                        var result =
                                com.gwsheridan.worldsunwritten.structure.CellSites.plan(
                                        site.assembly(), sample);
                        if (result.isPresent()) {
                            var size = result.get().size();
                            var center = cells.layout().centre(key.cell());
                            boolean fits = true;
                            for (int dx : new int[] {-size.getX() / 2, (size.getX() - 1) / 2})
                                for (int dz : new int[] {-size.getZ() / 2, (size.getZ() - 1) / 2})
                                    fits &=
                                            cells.layout()
                                                    .contains(
                                                            key.cell(),
                                                            (int) StrictMath.floor(center.x()) + dx,
                                                            (int) StrictMath.floor(center.z())
                                                                    + dz);
                            int y =
                                    tile.terrain().shape().elevation()
                                            + (tile.kind() == CellWorldProfile.Kind.ISLAND ? 0 : 1);
                            if (fits
                                    && y >= getMinY()
                                    && y + size.getY() <= getMinY() + getGenDepth()) {
                                chosen = result;
                                procedural = true;
                            }
                        }
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(
                                "Cannot plan saved cell site " + site.type(), e);
                    }
            var c = cells.layout().centre(key.cell());
            var size = chosen.map(IslandBuild::size).orElse(BlockPos.ZERO);
            int y =
                    tile.terrain().shape().elevation()
                            - (tile.kind() == CellWorldProfile.Kind.CAVERN ? 23 : 0);
            if (procedural)
                y =
                        tile.terrain().shape().elevation()
                                + (tile.kind() == CellWorldProfile.Kind.ISLAND ? 0 : 1);
            var origin =
                    cells.offset(key.territory())
                            .offset(
                                    (int) StrictMath.floor(c.x()) - size.getX() / 2,
                                    y,
                                    (int) StrictMath.floor(c.z()) - size.getZ() / 2);
            var columns =
                    chosen.map(
                                    b ->
                                            b.cells().stream()
                                                    .collect(
                                                            java.util.stream.Collectors.groupingBy(
                                                                    v ->
                                                                            columnKey(
                                                                                    v.pos().getX(),
                                                                                    v.pos()
                                                                                            .getZ()))))
                            .orElseGet(Map::of);
            var plan = new BuildPlan(chosen, origin, columns);
            sitePlans.put(cacheKey, plan);
            int cachedCells =
                    sitePlans.values().stream()
                            .mapToInt(value -> value.build().map(b -> b.cells().size()).orElse(0))
                            .sum();
            while (sitePlans.size() > 32 || cachedCells > 262144) {
                var oldest = sitePlans.keySet().iterator().next();
                var removed = sitePlans.remove(oldest);
                cachedCells -= removed.build().map(b -> b.cells().size()).orElse(0);
            }
            return plan;
        }
    }

    public BlockPos origin(CellWorldProfile.Tile tile, Key key) {
        return origin(tile, key, seed);
    }

    public BlockPos origin(CellWorldProfile.Tile tile, Key key, long worldSeed) {
        if (sites()) return sitePlan(tile, key, worldSeed).origin();
        var c = cells.layout().centre(key.cell());
        var size = tile.template().map(IslandBuild::size).orElse(BlockPos.ZERO);
        return cells.offset(key.territory())
                .offset(
                        (int) StrictMath.floor(c.x()) - size.getX() / 2,
                        tile.terrain().shape().elevation()
                                - (tile.kind() == CellWorldProfile.Kind.CAVERN ? 23 : 0),
                        (int) StrictMath.floor(c.z()) - size.getZ() / 2);
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private List<IslandBuild.Cell> templateColumn(
            CellWorldProfile.Tile tile, Key key, BlockPos origin, int x, int z, long worldSeed) {
        return (sites() ? sitePlan(tile, key, worldSeed).columns() : templateColumns.get(tile.id()))
                .getOrDefault(columnKey(x - origin.getX(), z - origin.getZ()), List.of());
    }

    public BlockPos spawn(Key key, LevelHeightAccessor height) {
        var point = cells.layout().centre(key.cell());
        var offset = cells.offset(key.territory());
        int arenaX = (int) StrictMath.floor(point.x()) + offset.getX(),
                arenaZ = (int) StrictMath.floor(point.z()) + offset.getZ();
        if (arena(arenaX, arenaZ)) {
            var arena = endProgression().orElseThrow();
            return new BlockPos(
                    arenaX,
                    arena.generator()
                            .getBaseHeight(
                                    arenaX,
                                    arenaZ,
                                    Heightmap.Types.MOTION_BLOCKING,
                                    height,
                                    arena.state(seed)),
                    arenaZ);
        }
        var tile = cells.tile(seed, key.territory(), key.cell());
        var build = build(tile, key);
        if (build.isPresent()) return origin(tile, key).offset(build.orElseThrow().spawn());
        var c = cells.layout().centre(key.cell());
        int x = (int) StrictMath.floor(c.x()) + cells.offset(key.territory()).getX(),
                z = (int) StrictMath.floor(c.z()) + cells.offset(key.territory()).getZ();
        if (tile.kind() == CellWorldProfile.Kind.CAVERN)
            return new BlockPos(x, tile.terrain().shape().elevation() - 19, z);
        return new BlockPos(
                x, previewHeight(x, z, Heightmap.Types.MOTION_BLOCKING, height, seed), z);
    }

    public int previewHeight(
            int x, int z, Heightmap.Types type, LevelHeightAccessor height, long seed) {
        var c = previewColumn(x, z, height, seed);
        for (int y = height.getMaxY(); y >= height.getMinY(); y--)
            if (type.isOpaque().test(c.getBlock(y))) return y + 1;
        return height.getMinY();
    }

    public NoiseColumn previewColumn(int x, int z, LevelHeightAccessor height, long seed) {
        if (arena(x, z)) {
            var arena = endProgression().orElseThrow();
            return arena.generator().getBaseColumn(x, z, height, arena.state(seed));
        }
        if (campaignEnd() && CellEndReservations.landing(x, z)) {
            var values = new BlockState[height.getHeight()];
            Arrays.fill(values, Blocks.AIR.defaultBlockState());
            if (CellEndReservations.floor(x, z))
                values[CellEndReservations.FLOOR - height.getMinY()] =
                        Blocks.END_STONE.defaultBlockState();
            return new NoiseColumn(height.getMinY(), values);
        }
        var a = cells.address(x, z);
        if (a == null || !cells.layout().contains(a.cell(), a.localX(), a.localZ())) {
            var empty = new BlockState[height.getHeight()];
            Arrays.fill(empty, Blocks.AIR.defaultBlockState());
            return new NoiseColumn(height.getMinY(), empty);
        }
        return column(x, z, height, seed, a, cells.tile(seed, a.territory(), a.cell()));
    }

    @Override
    public int getBaseHeight(
            int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state) {
        var c = getBaseColumn(x, z, height, state);
        for (int y = height.getMaxY(); y >= height.getMinY(); y--)
            if (type.isOpaque().test(c.getBlock(y))) return y + 1;
        return height.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
        if (arena(x, z)) {
            var a = endProgression().orElseThrow();
            return a.generator().getBaseColumn(x, z, height, a.state(state.seed()));
        }
        if (open(x, z)) return previewColumn(x, z, height, state.seed());
        var empty = new BlockState[height.getHeight()];
        Arrays.fill(empty, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(height.getMinY(), empty);
    }

    public record Key(int territory, Cell cell) {
        public String id() {
            return territory + ":" + cell.key();
        }

        public static Key parse(String text) {
            int colon = text.indexOf(':');
            return new Key(
                    Integer.parseInt(text.substring(0, colon)),
                    Cell.parse(text.substring(colon + 1)));
        }
    }
}

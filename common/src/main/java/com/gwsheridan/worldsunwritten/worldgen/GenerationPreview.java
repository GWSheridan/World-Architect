package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.config.IslandProfile;
import java.util.Optional;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;

/** Samples managed generation without creating chunks or a level. */
public final class GenerationPreview {
    private final int minY;
    private final int maxY;
    private final Surface surface;
    private final BiomeResolver biomes;
    private final Optional<IslandProfile> islands;
    private Optional<com.gwsheridan.worldsunwritten.api.IslandLayout> layout;
    private final Columns columns;
    private final boolean endArena;
    private final Optional<com.gwsheridan.worldsunwritten.config.RegionalTerrain> regions;

    private GenerationPreview(LevelHeightAccessor height, Surface surface, BiomeResolver biomes, Optional<IslandProfile> islands, Columns columns, boolean endArena,Optional<com.gwsheridan.worldsunwritten.config.RegionalTerrain> regions) {
        this.minY = height.getMinY();
        this.maxY = height.getMaxY();
        this.surface = surface;
        this.biomes = biomes;
        this.islands = islands;
        this.layout = islands.map(value -> value);
        this.columns = columns;
        this.endArena = endArena;
        this.regions=regions;
    }

    public static Optional<GenerationPreview> forStem(LevelStem stem, long seed) {
        var type = stem.type().value();
        var height = LevelHeightAccessor.create(type.minY(), type.height());
        var generator = stem.generator();
        if(generator instanceof CellGenerator cellular){
            var source=new CellBiomeSource(cellular.cells());source.seed(seed);
            return Optional.of(new GenerationPreview(height,(x,z)->cellular.previewHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,height,seed),
                    (x,y,z)->cellular.endProgression().filter(a->com.gwsheridan.worldsunwritten.config.EndProgression.contains(x*4,z*4)).map(a->a.biome()).orElseGet(()->source.sample(x*4,z*4)),Optional.empty(),
                    (x,z)->cellular.previewColumn(x,z,height,seed),cellular.endProgression().isPresent(),Optional.empty()));
        }
        if (generator instanceof SolidColumnGenerator solid) {
            BiomeResolver biomes = switch (solid.getBiomeSource()) {
                case FixedBiomeSource fixed -> fixed::getNoiseBiome;
                case EndArenaBiomeSource arena -> arena::sample;
                case RegionBiomeSource regions -> regions::getNoiseBiome;
                case ZoneBiomeSource zones -> zones::sample;
                default -> throw new IllegalStateException("Managed terrain has an unsupported biome source for preview");
            };
            return Optional.of(new GenerationPreview(height,
                    (x, z) -> solid.baseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, seed), biomes, Optional.empty(),
                    (x, z) -> solid.baseColumn(x, z, height, seed), solid.endProgression().isPresent(),solid.terrainRegions()));
        }
        if (generator instanceof VoidGenerator sky) {
            var result = new GenerationPreview(height,
                    (x, z) -> sky.endProgression().filter(ignored -> com.gwsheridan.worldsunwritten.config.EndProgression.contains(x, z))
                            .map(arena -> arena.generator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, arena.state(seed)))
                            .orElseGet(() -> sky instanceof SkyblockGenerator rich ? rich.previewHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,height,seed) : sky.baseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height)),
                    (x, y, z) -> sky.getBiomeSource() instanceof EndArenaBiomeSource arena ? arena.sample(x, y, z) : sky.biome(), sky.island(),
                    (x, z) -> sky.baseColumn(x, z, height, seed), sky.endProgression().isPresent(),Optional.empty());
            result.layout = sky instanceof SkyblockGenerator rich ? Optional.of(rich.layout(seed)) : sky.layout();
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public Optional<IslandProfile> islands() {
        return islands;
    }

    public Optional<com.gwsheridan.worldsunwritten.api.IslandLayout> layout() { return layout; }

    public boolean endArena() { return endArena; }
    public Optional<com.gwsheridan.worldsunwritten.config.RegionalTerrain> regions(){return regions;}

    /** Registered biome at the requested height, without loading a chunk. */
    public Optional<Identifier> biomeAt(int x, int y, int z) {
        return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)).unwrapKey().map(key -> key.identifier());
    }

    /** Base terrain only; this never runs decoration or loads a chunk. */
    public net.minecraft.world.level.NoiseColumn column(int x, int z) { return columns.at(x, z); }

    @FunctionalInterface private interface Columns { net.minecraft.world.level.NoiseColumn at(int x, int z); }

    public Sample sample(int x, int z) {
        int firstAir = surface.firstFree(x, z);
        int quartY = QuartPos.fromBlock(Math.max(minY, firstAir - 1));
        var biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z));
        return new Sample(firstAir - 1, firstAir > minY, biome.unwrapKey().map(key -> key.identifier()));
    }

    public record Sample(int surfaceY, boolean occupied, Optional<Identifier> biome) {}

    @FunctionalInterface
    private interface Surface {
        int firstFree(int x, int z);
    }
}

package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.config.IslandProfile;
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
    private final Columns columns;
    private final boolean endArena;

    private GenerationPreview(LevelHeightAccessor height, Surface surface, BiomeResolver biomes, Optional<IslandProfile> islands, Columns columns, boolean endArena) {
        this.minY = height.getMinY();
        this.maxY = height.getMaxY();
        this.surface = surface;
        this.biomes = biomes;
        this.islands = islands;
        this.columns = columns;
        this.endArena = endArena;
    }

    public static Optional<GenerationPreview> forStem(LevelStem stem, long seed) {
        var type = stem.type().value();
        var height = LevelHeightAccessor.create(type.minY(), type.height());
        var generator = stem.generator();
        if (generator instanceof SolidColumnGenerator solid) {
            BiomeResolver biomes = switch (solid.getBiomeSource()) {
                case FixedBiomeSource fixed -> fixed::getNoiseBiome;
                case EndArenaBiomeSource arena -> arena::sample;
                case RegionBiomeSource regions -> regions::getNoiseBiome;
                default -> throw new IllegalStateException("Managed terrain has an unsupported biome source for preview");
            };
            return Optional.of(new GenerationPreview(height,
                    (x, z) -> solid.baseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, seed), biomes, Optional.empty(),
                    (x, z) -> solid.baseColumn(x, z, height, seed), solid.endProgression().isPresent()));
        }
        if (generator instanceof VoidGenerator sky) {
            return Optional.of(new GenerationPreview(height,
                    (x, z) -> sky.endProgression().filter(ignored -> com.gwsheridan.worldarchitect.config.EndProgression.contains(x, z))
                            .map(arena -> arena.generator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, arena.state(seed)))
                            .orElseGet(() -> sky.baseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height)),
                    (x, y, z) -> sky.getBiomeSource() instanceof EndArenaBiomeSource arena ? arena.sample(x, y, z) : sky.biome(), sky.island(),
                    (x, z) -> sky.baseColumn(x, z, height, seed), sky.endProgression().isPresent()));
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

    public boolean endArena() { return endArena; }

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

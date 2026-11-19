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

    private GenerationPreview(LevelHeightAccessor height, Surface surface, BiomeResolver biomes, Optional<IslandProfile> islands) {
        this.minY = height.getMinY();
        this.maxY = height.getMaxY();
        this.surface = surface;
        this.biomes = biomes;
        this.islands = islands;
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
                    (x, z) -> solid.baseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, seed), biomes, Optional.empty()));
        }
        if (generator instanceof VoidGenerator sky) {
            return Optional.of(new GenerationPreview(height,
                    (x, z) -> sky.endProgression().filter(ignored -> com.gwsheridan.worldarchitect.config.EndProgression.contains(x, z))
                            .map(arena -> arena.generator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, arena.state(seed)))
                            .orElseGet(() -> sky.baseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height)),
                    (x, y, z) -> sky.getBiomeSource() instanceof EndArenaBiomeSource arena ? arena.sample(x, y, z) : sky.biome(), sky.island()));
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

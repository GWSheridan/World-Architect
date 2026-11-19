package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.config.AdvancedTerrain;
import com.gwsheridan.worldarchitect.config.AdvancedTerrain.Landform;
import com.gwsheridan.worldarchitect.config.TerrainShape;
import com.gwsheridan.worldarchitect.worldgen.terrain.HeightfieldV2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.LevelHeightAccessor;

/** Checks the actual region files produced from the advanced terrain example. */
public final class AdvancedTerrainWorldAudit {
    private static final String AIR = "minecraft:air";
    private static final int[][] CHUNKS = {{-4, -4}, {-3, -4}, {-1, 0}, {0, 0}, {1, 0}, {0, -1}, {5, 5}, {6, 5}};

    private AdvancedTerrainWorldAudit() {}

    public static void main(String[] args) throws IOException {
        Path world = Path.of(args[0]);
        int origin = Integer.parseInt(args[1]);
        CompoundTag settings = NbtIo.readCompressed(world.resolve("data/minecraft/world_gen_settings.dat"), NbtAccounter.unlimitedHeap())
                .getCompoundOrEmpty("data");
        CompoundTag snapshot = NbtIo.readCompressed(world.resolve("data/worldarchitect-generation-v1.dat"), NbtAccounter.unlimitedHeap())
                .getCompoundOrEmpty("data");
        require(settings.equals(snapshot), "Generation settings differ from the snapshot");
        long seed = settings.getLong("seed").orElseThrow();
        long checked = 0;
        for (Fixture fixture : Fixture.values()) {
            checked += audit(world, settings, origin, seed, fixture);
        }
        System.out.printf("Verified %,d version 2 blocks, both biome-specific surfaces, seeded strata, and suspended islands.%n", checked);
    }

    private static long audit(Path world, CompoundTag settings, int origin, long seed, Fixture fixture) throws IOException {
        int min = fixture.min();
        int height = fixture.height();
        var profile = settings.getCompoundOrEmpty("dimensions").getCompoundOrEmpty("minecraft:" + fixture.dimension)
                .getCompoundOrEmpty("generator").getCompoundOrEmpty("profile");
        require(profile.getIntOr("generator_version", 0) == 2, "Missing version 2 profile in " + fixture.dimension);
        Path regions = world.resolve("dimensions/minecraft").resolve(fixture.dimension).resolve("region");
        var bounds = LevelHeightAccessor.create(min, height);
        var settings2 = fixture.shape.advanced().orElseThrow();
        Set<String> seen = new HashSet<>();
        Set<Integer> transitions = new HashSet<>();
        for (int[] offset : CHUNKS) {
            int cx = origin + offset[0];
            int cz = origin + offset[1];
            var chunk = TerrainWorldAudit.readChunk(regions, cx, cz);
            require(chunk.getStringOr("Status", "").equals("minecraft:full"), "Incomplete chunk " + cx + "," + cz + " in " + fixture.dimension);
            var blocks = FeatureWorldAudit.sections(chunk, min, height, "block_states", 4);
            var biomes = FeatureWorldAudit.sections(chunk, min, height, "biomes", 1);
            for (int index = 0; index < 256; index++) {
                int x = cx * 16 + (index & 15);
                int z = cz * 16 + (index >> 4);
                var column = HeightfieldV2.sample(fixture.shape, settings2, seed, x, z, bounds);
                int offsetY = (int) Math.round(6 * HeightfieldV2.transition(seed ^ settings2.seedSalt(), x, z, settings2.scale()));
                transitions.add(offsetY);
                int yQuart = (Math.max(min, column.top()) & 15) / 4;
                String biome = biomes[(Math.max(min, column.top()) - min) / 16].blockAt(yQuart * 16 + ((index >> 6) * 4) + (index & 15) / 4);
                for (int y = min; y < min + height; y++) {
                    String actual = FeatureWorldAudit.block(blocks, min, y, index);
                    String expected = y < column.bottom() || y > column.top() ? AIR : fixture.material(y, column.top(), biome, offsetY);
                    if (!expected.equals(actual)) {
                        throw new AssertionError(fixture.dimension + " " + x + "," + y + "," + z + " expected " + expected + " but got " + actual);
                    }
                    seen.add(actual);
                }
            }
        }
        if (fixture == Fixture.OVERWORLD) {
            require(seen.containsAll(Set.of("minecraft:moss_block", "minecraft:green_terracotta", "minecraft:deepslate", "minecraft:calcite")),
                    "The Overworld sample needs both biomes and both absolute strata");
            require(transitions.size() > 1, "Missing varying layer transition");
        } else {
            require(seen.containsAll(Set.of(AIR, fixture.base)), "The sample needs both terrain and air in " + fixture.dimension);
        }
        return (long) CHUNKS.length * 256 * height;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private enum Fixture {
        OVERWORLD("overworld", "minecraft:stone", Landform.MOUNTAINS, 112, 52, -70711),
        NETHER("the_nether", "minecraft:netherrack", Landform.PLATEAUS, 71, 39, 0),
        END("the_end", "minecraft:end_stone", Landform.FLOATING_ISLANDS, 118, 60, 0);

        private final String dimension;
        private final String base;
        private final TerrainShape shape;

        Fixture(String dimension, String base, Landform landform, int elevation, int relief, long salt) {
            this.dimension = dimension;
            this.base = base;
            this.shape = new TerrainShape(elevation, relief, Optional.of(new AdvancedTerrain(landform, 192, salt)));
        }

        int min() {
            return this == OVERWORLD ? -64 : 0;
        }

        int height() {
            return this == OVERWORLD ? 384 : 256;
        }

        String material(int y, int top, String biome, int offset) {
            if (this != OVERWORLD) {
                return base;
            }
            if (y == top) {
                return biome.equals("minecraft:forest") ? "minecraft:moss_block" : "minecraft:green_terracotta";
            }
            if (y >= 5 + offset && y <= 16 + offset) {
                return "minecraft:calcite";
            }
            if (y <= 0) {
                return "minecraft:deepslate";
            }
            return top - y <= 3 ? "minecraft:dirt" : base;
        }
    }
}

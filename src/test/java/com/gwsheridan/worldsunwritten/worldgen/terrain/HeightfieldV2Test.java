package com.gwsheridan.worldsunwritten.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.gwsheridan.worldsunwritten.config.AdvancedTerrain;
import com.gwsheridan.worldsunwritten.config.AdvancedTerrain.Landform;
import com.gwsheridan.worldsunwritten.config.TerrainShape;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class HeightfieldV2Test {
    private static final long SEED = 194723;
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);

    @ParameterizedTest
    @EnumSource(Landform.class)
    void landformsRespondToSeedsAndRemainContinuousAcrossChunkBorders(Landform landform) {
        var shape = shape(landform, 192);
        var settings = shape.advanced().orElseThrow();
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        boolean changedWithSeed = false;
        boolean hasVoid = false;
        boolean hasLand = false;
        for (int z = -512; z <= 512; z += 13) {
            for (int boundary = -512; boundary <= 512; boundary += 16) {
                var west = HeightfieldV2.sample(shape, settings, SEED, boundary - 1, z, HEIGHT);
                var east = HeightfieldV2.sample(shape, settings, SEED, boundary, z, HEIGHT);
                var north = HeightfieldV2.sample(shape, settings, SEED, z, boundary - 1, HEIGHT);
                var south = HeightfieldV2.sample(shape, settings, SEED, z, boundary, HEIGHT);
                assertEquals(east, HeightfieldV2.sample(shape, settings, SEED, boundary, z, HEIGHT));
                changedWithSeed |= !east.equals(HeightfieldV2.sample(shape, settings, SEED + 1, boundary, z, HEIGHT));
                assertContinuous(west, east);
                assertContinuous(north, south);
                if (east.bottom() > east.top()) {
                    hasVoid = true;
                } else {
                    hasLand = true;
                    assertTrue(east.bottom() >= HEIGHT.getMinY() && east.top() <= HEIGHT.getMaxY() - 3);
                    assertEquals(landform == Landform.FLOATING_ISLANDS, east.bottom() > HEIGHT.getMinY());
                    minimum = Math.min(minimum, east.top());
                    maximum = Math.max(maximum, east.top());
                }
            }
        }
        assertTrue(changedWithSeed);
        assertTrue(hasLand);
        assertEquals(landform == Landform.FLOATING_ISLANDS, hasVoid);
        assertTrue(maximum - minimum > 30);
    }

    @Test
    void terrainVersionMustMatchItsSettingsAndLegacySchemaRoundTrips() {
        var codec = TerrainShape.CODEC.codec();
        var legacy = JsonParser.parseString("""
                {"generator_version": 1, "elevation": 68, "relief": 44}
                """);
        assertEquals(legacy, codec.encodeStart(JsonOps.INSTANCE, codec.parse(JsonOps.INSTANCE, legacy).getOrThrow()).getOrThrow());
        var advanced = codec.encodeStart(JsonOps.INSTANCE, shape(Landform.CANYONS, 512)).getOrThrow();
        assertEquals(shape(Landform.CANYONS, 512), codec.parse(JsonOps.INSTANCE, advanced).getOrThrow());
        advanced.getAsJsonObject().addProperty("generator_version", 1);
        assertFalse(codec.parse(JsonOps.INSTANCE, advanced).isSuccess());
        legacy.getAsJsonObject().addProperty("generator_version", 2);
        assertFalse(codec.parse(JsonOps.INSTANCE, legacy).isSuccess());
    }

    private static TerrainShape shape(Landform landform, int scale) {
        return new TerrainShape(108, 64, Optional.of(new AdvancedTerrain(landform, scale, -9321)));
    }

    private static void assertContinuous(HeightfieldV2.Column first, HeightfieldV2.Column second) {
        if (first.bottom() <= first.top() && second.bottom() <= second.top()) {
            assertTrue(Math.abs(first.top() - second.top()) <= 8, () -> first + " vs " + second);
            assertTrue(Math.abs(first.bottom() - second.bottom()) <= 8, () -> first + " vs " + second);
        }
    }
}

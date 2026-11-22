package com.gwsheridan.worldsunwritten.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeightfieldV1Test {
    @Test
    void flatTerrainStaysAtTheRequestedElevation() {
        assertEquals(71, HeightfieldV1.surface(498, -510, 101, 71, 0));
        assertEquals(71, HeightfieldV1.surface(-76, 990, -20, 71, 0));
    }

    @Test
    void terrainIsSeededAndHasContinuousSlopesAcrossNegativeAndPositiveChunkBorders() {
        long seed = 194723L;
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        boolean changedWithSeed = false;
        for (int z = -220; z < 220; z += 7) {
            for (int x = -220; x < 220; x++) {
                int surface = HeightfieldV1.surface(seed, x, z, 68, 44);
                assertEquals(surface, HeightfieldV1.surface(seed, x, z, 68, 44));
                int east = HeightfieldV1.surface(seed, x + 1, z, 68, 44);
                int south = HeightfieldV1.surface(seed, x, z + 1, 68, 44);
                assertTrue(Math.abs(surface - east) <= 6, "East-west slope at " + x + ", " + z);
                assertTrue(Math.abs(surface - south) <= 6, "North-south slope at " + x + ", " + z);
                changedWithSeed |= surface != HeightfieldV1.surface(seed + 1, x, z, 68, 44);
                minimum = Math.min(minimum, surface);
                maximum = Math.max(maximum, surface);
            }
        }
        assertTrue(changedWithSeed);
        assertTrue(maximum - minimum >= 40);
    }
}

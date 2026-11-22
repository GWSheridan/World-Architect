package com.gwsheridan.worldsunwritten.worldgen.terrain;

import com.gwsheridan.worldsunwritten.config.AdvancedTerrain;
import com.gwsheridan.worldsunwritten.config.AdvancedTerrain.Landform;
import com.gwsheridan.worldsunwritten.config.TerrainShape;
import net.minecraft.world.level.LevelHeightAccessor;

/** Version 2 terrain. All its inputs are pinned by the saved generator profile. */
public final class HeightfieldV2 {
    private HeightfieldV2() {}

    public static Column sample(TerrainShape shape, AdvancedTerrain settings, long seed, int x, int z,
            LevelHeightAccessor height) {
        seed ^= settings.seedSalt();
        int scale = settings.scale();
        int floor = height.getMinY();
        int ceiling = height.getMaxY() - 3;
        double broad = HeightfieldV1.noise(seed, x, z, scale);
        double detail = HeightfieldV1.noise(seed ^ 0x1617A189C0E001A9L, x, z, scale / 4);
        double ridge = 1.0 - Math.abs(broad);
        Landform landform = settings.landform();
        if (landform == Landform.FLOATING_ISLANDS) {
            double mass = broad + detail * 0.22 - 0.19;
            if (mass <= 0) {
                return new Column(floor, floor - 1);
            }
            int top = bounded(shape.elevation(), shape.relief(), mass * 0.70 + detail * 0.22, floor + 1, ceiling);
            int thickness = Math.max(1, (int) Math.round(mass * (14 + shape.relief())));
            return new Column(Math.max(floor + 1, top - thickness + 1), top);
        }
        double variation = switch (landform) {
            case MOUNTAINS -> 1.8 * ridge * ridge * ridge + broad * 0.65 + detail * 0.22 - 0.55;
            case VALLEYS -> 0.78 + broad * 0.45 - 1.74 * ridge * ridge + detail * 0.12;
            case PLATEAUS -> Math.clamp(broad * 3.1, -0.72, 0.72) + detail * 0.12;
            case CANYONS -> 0.38 - 1.70 * smoothstep(0.65, 0.93, ridge) + broad * 0.26 + detail * 0.09;
            case BASINS -> 1.25 * (broad + 1.0) * (broad + 1.0) / 4.0 - 0.85 + detail * 0.12;
            case CRATERS -> crater(seed, x, z, scale) + detail * 0.10;
            case CONTINENTS -> 1.74 * smoothstep(-0.34, 0.29, HeightfieldV1.noise(seed, x, z, scale * 4))
                    - 0.9 + broad * 0.31 + detail * 0.12;
            case ARCHIPELAGOS -> 1.75 * smoothstep(0.10, 0.65, broad) - 0.87 + detail * 0.20;
            case FLOATING_ISLANDS -> throw new AssertionError(landform);
        };
        return new Column(floor, bounded(shape.elevation(), shape.relief(), variation, floor, ceiling));
    }

    private static int bounded(int elevation, int relief, double variation, int floor, int ceiling) {
        return Math.clamp(elevation + (int) Math.round(relief * variation), floor, ceiling);
    }

    public static double transition(long seed, int x, int z, int scale) {
        return HeightfieldV1.noise(seed ^ 0xD9DE2883582664D3L, x, z, scale / 2);
    }

    private static double crater(long seed, int x, int z, int scale) {
        int cellX = Math.floorDiv(x, scale);
        int cellZ = Math.floorDiv(z, scale);
        double smallest = Double.POSITIVE_INFINITY;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int col = cellX + dx;
                int row = cellZ + dz;
                double centerX = (col + 0.5 + HeightfieldV1.sample(seed, col, row) * 0.2) * scale;
                double centerZ = (row + 0.5 + HeightfieldV1.sample(seed ^ 0xB59BEE120DA62CABL, col, row) * 0.2) * scale;
                double deltaX = x - centerX;
                double deltaZ = z - centerZ;
                smallest = Math.min(smallest, deltaX * deltaX + deltaZ * deltaZ);
            }
        }
        double radius = StrictMath.sqrt(smallest) / scale;
        return -1.30 * (1.0 - smoothstep(0.1, 0.31, radius))
                + 0.68 * smoothstep(0.2, 0.31, radius) * (1.0 - smoothstep(0.31, 0.48, radius));
    }

    private static double smoothstep(double from, double to, double value) {
        double progress = Math.clamp((value - from) / (to - from), 0.0, 1.0);
        return progress * progress * (3.0 - 2.0 * progress);
    }

    public record Column(int bottom, int top) {}
}

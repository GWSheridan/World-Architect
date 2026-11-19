package com.gwsheridan.worldarchitect.worldgen.terrain;

/** Version 1 terrain. Changing these calculations changes unexplored chunks in existing saves. */
public final class HeightfieldV1 {
    private HeightfieldV1() {}

    public static int surface(long seed, int x, int z, int elevation, int relief) {
        double broad = noise(seed, x, z, 128);
        double hills = noise(seed ^ 0x2C9277B5D14BEFA9L, x, z, 48);
        double detail = noise(seed ^ 0x5AA9D6D2B7D73933L, x, z, 16);
        double ridge = 1.0 - Math.abs(noise(seed ^ 0x8CB92BA72F3D8DD7L, x, z, 96));
        return elevation + (int) Math.round(relief * (0.65 * broad + 0.25 * hills + 0.10 * detail + 0.55 * ridge * ridge * ridge));
    }

    static double noise(long seed, int x, int z, int scale) {
        int left = Math.floorDiv(x, scale);
        int near = Math.floorDiv(z, scale);
        double tx = smooth((double) Math.floorMod(x, scale) / scale);
        double tz = smooth((double) Math.floorMod(z, scale) / scale);
        double front = lerp(sample(seed, left, near), sample(seed, left + 1, near), tx);
        double back = lerp(sample(seed, left, near + 1), sample(seed, left + 1, near + 1), tx);
        return lerp(front, back, tz);
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double sample(long seed, int x, int z) {
        long bits = seed ^ (0x9E3779B97F4A7C15L * x) ^ (0xD1B54A32D192ED03L * z);
        bits = (bits ^ (bits >>> 30)) * 0xBF58476D1CE4E5B9L;
        bits = (bits ^ (bits >>> 27)) * 0x94D049BB133111EBL;
        bits ^= bits >>> 31;
        return (bits >>> 11) * 0x1.0p-52 - 1.0;
    }
}

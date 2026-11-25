package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.config.ContentProfile.Caves;

/** A winding, chunk-independent tunnel cross-section that leaves the floor and surface intact. */
record CaveSpan(int bottom, int top) {
    static final CaveSpan NONE = new CaveSpan(1, 0);

    static CaveSpan sample(Caves rule, long seed, int x, int z, int terrainBottom, int terrainTop) {
        double phase = (ContentRandom.seed(seed, 0, 0, 27) >>> 11) * 0x1.0p-53 * 2 * Math.PI;
        double centerLine = StrictMath.sin(x / 39.0 + 1.5 * StrictMath.sin(z / 73.0 + phase) + phase);
        double across = centerLine / (rule.radius() / 40.0);
        if (Math.abs(across) >= 1) {
            return NONE;
        }
        int middle = rule.minY() + (int) Math.round((rule.maxY() - rule.minY()) * (0.5 + 0.28 * StrictMath.sin(z / 47.0 + phase)));
        int radius = (int) Math.round(rule.radius() * StrictMath.sqrt(1 - across * across));
        int bottom = Math.max(middle - radius, Math.max(rule.minY(), terrainBottom + 2));
        int top = Math.min(middle + radius, Math.min(rule.maxY(), terrainTop - 6));
        return new CaveSpan(bottom, top);
    }

    boolean contains(int y) {
        return bottom <= y && y <= top;
    }
}

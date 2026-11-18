package com.gwsheridan.worldarchitect.worldgen;

final class ContentRandom {
    private ContentRandom() {}

    static long seed(long seed, int x, int z, long salt) {
        long value = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xD1B54A32D192ED03L ^ salt * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}

package com.gwsheridan.worldsunwritten.client;

import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small software projection, shared by terrain and template views; no world renderer or GPU cache. */
final class IsoCanvas {
    private IsoCanvas() {}
    static int[] point(double x, double y, double z, double yaw, double scale, int cx, int cy) {
        double c = Math.cos(yaw), s = Math.sin(yaw), a = x * c - z * s, b = x * s + z * c;
        return new int[]{cx + (int)Math.round(a * scale), cy + (int)Math.round((b * .5 - y) * scale)};
    }
    static void quad(GuiGraphicsExtractor g, int color, int[]... points) {
        int min = Arrays.stream(points).mapToInt(p -> p[1]).min().orElse(0);
        int max = Arrays.stream(points).mapToInt(p -> p[1]).max().orElse(0);
        for (int y = min; y <= max; y++) {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < points.length; i++) {
                int[] a = points[i], b = points[(i + 1) % points.length];
                if (a[1] == b[1] || y < Math.min(a[1], b[1]) || y >= Math.max(a[1], b[1])) continue;
                double x = a[0] + (double)(y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
                lo = Math.min(lo, x); hi = Math.max(hi, x);
            }
            if (lo <= hi) g.fill((int)Math.floor(lo), y, (int)Math.ceil(hi) + 1, y + 1, color);
        }
    }
}

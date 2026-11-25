package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.config.EndProgression;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Version-two End landing geometry. These permanent islands never spend expansion credits. */
public final class CellEndReservations {
    public static final int FLOOR = 75, RADIUS = 24;
    private static final Map<BlockPos, BlockPos> ARRIVALS = arrivals();

    private CellEndReservations() {}

    private static Map<BlockPos, BlockPos> arrivals() {
        var result = new LinkedHashMap<BlockPos, BlockPos>();
        for (int i = 0; i < 20; i++) {
            double angle = 2 * (-Math.PI + Math.PI / 20 * i);
            var gateway =
                    new BlockPos(
                            (int) Math.floor(96 * Math.cos(angle)),
                            75,
                            (int) Math.floor(96 * Math.sin(angle)));
            var direction = new Vec3(gateway.getX(), 0, gateway.getZ()).normalize().scale(1024);
            result.put(gateway, BlockPos.containing(direction.x + .5, FLOOR, direction.z + .5));
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<BlockPos, BlockPos> arrivalsByGateway() {
        return ARRIVALS;
    }

    public static Optional<BlockPos> arrival(BlockPos gateway) {
        return Optional.ofNullable(ARRIVALS.get(gateway));
    }

    public static boolean landing(int x, int z) {
        for (var p : ARRIVALS.values())
            if (Math.abs((long) x - p.getX()) <= RADIUS && Math.abs((long) z - p.getZ()) <= RADIUS)
                return true;
        return false;
    }

    public static boolean floor(int x, int z) {
        for (var p : ARRIVALS.values())
            if (Math.abs((long) x - p.getX()) <= 2 && Math.abs((long) z - p.getZ()) <= 2)
                return true;
        return false;
    }

    public static Set<com.gwsheridan.worldsunwritten.api.CellLayout.Cell> arenaCells(
            CellWorldProfile profile) {
        var result = new HashSet<com.gwsheridan.worldsunwritten.api.CellLayout.Cell>();
        for (var cell : profile.layout().within(profile.growth().radius())) {
            var b = profile.layout().bounds(cell);
            if (EndProgression.intersects(b.minX(), b.minZ(), b.maxX() - 1, b.maxZ() - 1))
                result.add(cell);
        }
        return Set.copyOf(result);
    }
}

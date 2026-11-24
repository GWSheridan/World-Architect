package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.spawn.SafeSpawns;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndGatewayFeature;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Native paired gateways on permanent landing islands. Unexpected builds always win over
 * construction.
 */
public final class CellEndArrivals {
    private CellEndArrivals() {}

    public static Map<BlockPos, BlockState> frame(BlockPos centre) {
        var result = new LinkedHashMap<BlockPos, BlockState>();
        for (var p : BlockPos.betweenClosed(centre.offset(-1, -2, -1), centre.offset(1, 2, 1))) {
            int x = p.getX() - centre.getX(),
                    y = p.getY() - centre.getY(),
                    z = p.getZ() - centre.getZ();
            BlockState block;
            if (x == 0 && y == 0 && z == 0) block = Blocks.END_GATEWAY.defaultBlockState();
            else if (y == 0) block = Blocks.AIR.defaultBlockState();
            else if (Math.abs(y) == 2)
                block =
                        x == 0 && z == 0
                                ? Blocks.BEDROCK.defaultBlockState()
                                : Blocks.AIR.defaultBlockState();
            else
                block =
                        x == 0 || z == 0
                                ? Blocks.BEDROCK.defaultBlockState()
                                : Blocks.AIR.defaultBlockState();
            result.put(p.immutable(), block);
        }
        return Collections.unmodifiableMap(result);
    }

    public static Vec3 fallback(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof CellGenerator generator))
            return null;
        return SafeSpawns.nearby(
                        level,
                        generator.spawn(
                                new CellGenerator.Key(
                                        0,
                                        com.gwsheridan.worldsunwritten.api.CellLayout.Cell.ORIGIN),
                                level))
                .map(Vec3::atBottomCenterOf)
                .orElse(null);
    }

    public static Vec3 arrive(ServerLevel level, BlockPos entry, TheEndGatewayBlockEntity origin) {
        if (!(level.getChunkSource().getGenerator() instanceof CellGenerator generator)
                || !generator.campaignEnd())
            throw new IllegalArgumentException("Shared End campaign required.");
        var reserved = CellEndReservations.arrival(entry);
        if (reserved.isEmpty()) return fallback(level);
        var island = reserved.get();
        var portal = island.offset(-2, 10, -2);
        var expected = frame(portal);
        // Level.getHeight returns minY for an unloaded chunk. Load the complete reservation first,
        // otherwise a saved player build could be mistaken for empty space after a restart.
        for (int x = Math.floorDiv(island.getX() - CellEndReservations.RADIUS, 16);
                x <= Math.floorDiv(island.getX() + CellEndReservations.RADIUS, 16);
                x++) {
            for (int z = Math.floorDiv(island.getZ() - CellEndReservations.RADIUS, 16);
                    z <= Math.floorDiv(island.getZ() + CellEndReservations.RADIUS, 16);
                    z++) {
                level.getChunk(x, z);
            }
        }
        // The permanent platform and reserved air must still match. A partial native frame can
        // recover after a crash.
        for (int x = island.getX() - CellEndReservations.RADIUS;
                x <= island.getX() + CellEndReservations.RADIUS;
                x++)
            for (int z = island.getZ() - CellEndReservations.RADIUS;
                    z <= island.getZ() + CellEndReservations.RADIUS;
                    z++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = level.getMinY(); y < top; y++) {
                    var p = new BlockPos(x, y, z);
                    var actual = level.getBlockState(p);
                    if (actual.isAir()) continue;
                    var frame = expected.get(p);
                    if (frame != null && actual == frame) continue;
                    if (y == CellEndReservations.FLOOR
                            && Math.abs(x - island.getX()) <= 2
                            && Math.abs(z - island.getZ()) <= 2
                            && actual.is(Blocks.END_STONE)) continue;
                    return fallback(level);
                }
            }
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                if (!level.getBlockState(island.offset(x, 0, z)).is(Blocks.END_STONE))
                    return fallback(level);
        try {
            CellWorlds.nativeArrival(
                    level,
                    expected,
                    () ->
                            EndGatewayFeature.knownExit(entry, false)
                                    .place(
                                            level,
                                            generator,
                                            RandomSource.create(portal.asLong()),
                                            portal));
            for (var e : expected.entrySet())
                if (level.getBlockState(e.getKey()) != e.getValue()) return fallback(level);
            if (!(level.getBlockEntity(portal) instanceof TheEndGatewayBlockEntity))
                return fallback(level);
            origin.setExitPosition(portal, false);
            if (!level.getServer().saveEverything(true, true, true)) return fallback(level);
            return SafeSpawns.nearby(level, island.above())
                    .filter(p -> generator.nativeReservation(p.getX(), p.getZ()))
                    .map(Vec3::atBottomCenterOf)
                    .orElseGet(() -> fallback(level));
        } catch (RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("A campaign gateway was preserved at {}: {}", entry, error.getMessage());
            return fallback(level);
        }
    }
}

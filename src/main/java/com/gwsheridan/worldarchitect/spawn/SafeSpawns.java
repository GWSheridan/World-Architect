package com.gwsheridan.worldarchitect.spawn;

import com.gwsheridan.worldarchitect.config.IslandTemplate;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class SafeSpawns {
    private SafeSpawns() {}

    public static boolean safe(ServerLevel level, BlockPos pos) {
        return level.isInsideBuildHeight(pos.below()) && level.isInsideBuildHeight(pos.above())
                && level.getWorldBorder().isWithinBounds(pos) && level.getWorldBorder().isWithinBounds(pos.above())
                && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && IslandTemplate.safeFloor(level.getBlockState(pos.below()), level, pos.below());
    }

    public static Optional<BlockPos> nearby(ServerLevel level, BlockPos target) {
        if (safe(level, target)) {
            return Optional.of(target);
        }
        for (int radius = 1; radius <= 4; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dz)), Math.abs(dy)) != radius) {
                            continue;
                        }
                        BlockPos pos = target.offset(dx, dy, dz);
                        if (safe(level, pos)) {
                            return Optional.of(pos);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public record Target(ServerLevel level, BlockPos position, float yaw) {
        public Target {
            position = position.immutable();
        }

        public Vec3 center() {
            return Vec3.atBottomCenterOf(position);
        }

        public TeleportTransition transition(TeleportTransition.PostTeleportTransition after) {
            return new TeleportTransition(level, center(), Vec3.ZERO, yaw, 0.0F, after);
        }
    }
}

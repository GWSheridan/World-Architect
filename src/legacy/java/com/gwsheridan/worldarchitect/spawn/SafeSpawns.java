package com.gwsheridan.worldarchitect.spawn;
import net.minecraft.world.phys.Vec3;import net.minecraft.server.level.ServerLevel;import net.minecraft.core.BlockPos;import net.minecraft.world.level.portal.TeleportTransition;import net.minecraft.world.level.storage.LevelData;
public final class SafeSpawns {
 private SafeSpawns() {}
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

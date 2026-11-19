package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.spawn.SafeSpawns;
import com.gwsheridan.worldarchitect.worldgen.ManagedGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TheEndGatewayBlockEntity.class)
abstract class EndGatewaySafetyMixin {
    @Inject(method = "getPortalPosition", at = @At("RETURN"), cancellable = true)
    private void worldarchitect$safeArrival(ServerLevel level, BlockPos entry, CallbackInfoReturnable<Vec3> callback) {
        Vec3 result = callback.getReturnValue();
        if (result == null || !(level.getChunkSource().getGenerator() instanceof ManagedGenerator generator)
                || generator.endProgression().isEmpty()) return;
        BlockPos target = BlockPos.containing(result);
        var nearby = SafeSpawns.nearby(level, target);
        if (nearby.isPresent()) { callback.setReturnValue(Vec3.atBottomCenterOf(nearby.orElseThrow())); return; }
        for (int radius = 0; radius <= 16; radius++) {
            for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                for (int dy = -16; dy <= 16; dy++) {
                    BlockPos candidate = target.offset(dx, dy, dz);
                    if (SafeSpawns.safe(level, candidate)) {
                        callback.setReturnValue(Vec3.atBottomCenterOf(candidate)); return;
                    }
                }
            }
        }
        com.mojang.logging.LogUtils.getLogger().warn("End gateway at {} has no safe arrival within 16 blocks of {}", entry, target);
        callback.setReturnValue(null);
    }
}

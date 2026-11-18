package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.skyblock.IslandSpawns;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EndPortalBlock.class, NetherPortalBlock.class})
abstract class VoidPortalMixin {
    // Destination handling precedes vanilla's automatic Nether frame and End platform placement.
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void worldarchitect$voidEntry(ServerLevel current, Entity entity, BlockPos portal,
            CallbackInfoReturnable<TeleportTransition> callback) {
        boolean endPortal = (Object) this instanceof EndPortalBlock;
        var destination = endPortal ? (current.dimension() == Level.END ? current.getRespawnData().dimension() : Level.END)
                : (current.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
        ServerLevel level = current.getServer().getLevel(destination);
        if (level != null && level.getChunkSource().getGenerator() instanceof VoidGenerator generator) {
            if (endPortal && current.dimension() == Level.END && generator.island().isPresent()
                    && entity instanceof ServerPlayer player && player.getRespawnConfig() != null) {
                TeleportTransition respawn = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
                if (!respawn.missingRespawnBlock()) {
                    callback.setReturnValue(respawn);
                    return;
                }
            }
            callback.setReturnValue(IslandSpawns.portalEntry(level, generator, entity));
        }
    }
}

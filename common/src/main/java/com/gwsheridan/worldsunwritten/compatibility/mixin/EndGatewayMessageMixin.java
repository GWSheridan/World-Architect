package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndGatewayBlock.class)
abstract class EndGatewayMessageMixin {
    @Inject(method = "getPortalDestination", at = @At("RETURN"))
    private void worldsunwritten$explainBlockedGateway(ServerLevel level, Entity entity, BlockPos pos,
            CallbackInfoReturnable<TeleportTransition> callback) {
        if (callback.getReturnValue() == null && entity instanceof ServerPlayer player
                && level.getChunkSource().getGenerator() instanceof ManagedGenerator managed && managed.endProgression().isPresent()) {
            player.sendSystemMessage(Component.literal("This gateway has no safe landing nearby. Clear a landing area at its destination before trying again."));
        }
    }
}

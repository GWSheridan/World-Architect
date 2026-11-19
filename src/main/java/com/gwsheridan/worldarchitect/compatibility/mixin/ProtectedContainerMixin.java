package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.skyblock.IslandProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ProtectedContainerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = {"handleContainerClick", "handleContainerButtonClick"}, at = @At("HEAD"), cancellable = true)
    private void worldarchitect$protectOpenContainer(CallbackInfo callback) {
        if (player.level().getServer().isSameThread() && IslandProtection.menuDenied(player)) {
            callback.cancel();
        }
    }
}

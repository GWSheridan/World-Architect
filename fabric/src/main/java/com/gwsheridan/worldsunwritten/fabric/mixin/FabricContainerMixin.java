package com.gwsheridan.worldsunwritten.fabric.mixin;

import com.gwsheridan.worldsunwritten.skyblock.IslandProtection;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class FabricContainerMixin {
    @Inject(method = "openMenu", at = @At("RETURN"))
    private void worldsunwritten$menuOpened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> callback) {
        callback.getReturnValue().ifPresent(menu -> IslandProtection.opened((ServerPlayer) (Object) this, menu));
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void worldsunwritten$menuClosed(CallbackInfo callback) {
        var player = (ServerPlayer) (Object) this;
        IslandProtection.closed(player, player.containerMenu.containerId);
    }
}

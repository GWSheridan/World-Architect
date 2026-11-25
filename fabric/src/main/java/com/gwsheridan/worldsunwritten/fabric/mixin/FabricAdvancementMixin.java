package com.gwsheridan.worldsunwritten.fabric.mixin;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
abstract class FabricAdvancementMixin {
    @Shadow private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void worldsunwritten$earned(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> callback) {
        CellWorlds.earned(player, advancement.id());
    }
}

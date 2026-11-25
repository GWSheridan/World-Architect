package com.gwsheridan.worldsunwritten.fabric.mixin;

import com.gwsheridan.worldsunwritten.fabric.SpawnAdmission;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class FabricEntityAdmissionMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$admit(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof SpawnAdmission spawn && spawn.worldsunwritten$spawnDenied()) callback.setReturnValue(false);
    }
}

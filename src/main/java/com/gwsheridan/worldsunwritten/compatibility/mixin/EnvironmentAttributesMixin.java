package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.environment.EnvironmentAttributes;
import com.gwsheridan.worldsunwritten.environment.EnvironmentClocks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnvironmentAttributeSystem.class)
abstract class EnvironmentAttributesMixin {
    @WrapOperation(method = "addDynamicLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;clockManager()Lnet/minecraft/world/clock/ClockManager;"))
    private static ClockManager worldsunwritten$dimensionClock(Level level, Operation<ClockManager> original) {
        return EnvironmentClocks.wrap(level, original.call(level));
    }

    @Inject(method = "addDynamicLayers", at = @At("RETURN"))
    private static void worldsunwritten$environmentLayers(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo callback) {
        EnvironmentAttributes.addLayers(builder, level);
    }
}

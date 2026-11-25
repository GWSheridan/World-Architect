package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import java.util.Optional;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class EnvironmentClockMixin {
    @Inject(method = "getClockTimeTicks", at = @At("RETURN"), cancellable = true)
    private void worldsunwritten$dimensionClock(Optional<?> clock, CallbackInfoReturnable<Long> callback) {
        Level level = (Level) (Object) this;
        if (clock.isPresent() && clock.equals(level.dimensionType().defaultClock())) {
            callback.setReturnValue(WorldEnvironment.profile(level).time().apply(callback.getReturnValue()));
        }
    }
}

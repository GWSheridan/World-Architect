package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.environment.EnvironmentProfile;
import com.gwsheridan.worldarchitect.environment.WorldEnvironment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class EnvironmentWeatherMixin {
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void worldarchitect$dimensionWeather(CallbackInfo callback) {
        if (WorldEnvironment.applyWeather((ServerLevel) (Object) this)) callback.cancel();
    }

    @Inject(method = "getWeatherData", at = @At("HEAD"), cancellable = true)
    private void worldarchitect$weatherView(CallbackInfoReturnable<WeatherData> callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (WorldEnvironment.profile(level).weather() != EnvironmentProfile.Weather.VANILLA) callback.setReturnValue(WorldEnvironment.weatherData(level));
    }
}

package com.gwsheridan.worldsunwritten.client.mixin;

import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldTypeEntry.class)
abstract class ModpackWorldTypeMixin {
    @Shadow @Final private Holder<WorldPreset> preset;

    @Inject(method = "describePreset", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$bundleLabel(CallbackInfoReturnable<Component> callback) {
        ModpackRuntime.installed().filter(bundle -> preset != null && preset.is(bundle.key()))
                .ifPresent(bundle -> callback.setReturnValue(Component.literal(bundle.manifest().title())));
    }
}

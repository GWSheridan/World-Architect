package com.gwsheridan.worldsunwritten.client.mixin;

import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
abstract class ModpackCreateWorldMixin {
    @Shadow public abstract WorldCreationUiState getUiState();

    @Inject(method = {"openDataPackSelectionScreen", "openExperimentsScreen"}, at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$lockPackEditors(WorldDataConfiguration dataConfiguration, CallbackInfo callback) {
        if (ModpackRuntime.locked().isPresent()) {
            worldsunwritten$showPolicy(Component.translatable("worldsunwritten.modpack.datapacks_locked"));
            callback.cancel();
        }
    }

    @Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$validateBeforeCreate(CallbackInfo callback) {
        if (ModpackRuntime.locked().isPresent()) {
            var settings = getUiState().getSettings();
            try {
                var selected = settings.selectedDimensions();
                var live = new WorldDimensions(selected.bake(settings.datapackDimensions()).dimensions());
                ModpackRuntime.validate(settings.options(), selected, live, settings.worldgenLoadContext(),
                        settings.dataConfiguration().dataPacks().getEnabled());
            } catch (IllegalStateException | IllegalArgumentException e) {
                worldsunwritten$showPolicy(Component.literal(e.getMessage()));
                callback.cancel();
            }
        }
    }

    @Unique
    private void worldsunwritten$showPolicy(Component message) {
        CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
        Minecraft.getInstance().gui.setScreen(new AlertScreen(() -> Minecraft.getInstance().gui.setScreen(screen),
                Component.translatable("worldsunwritten.modpack.title"), message));
    }
}

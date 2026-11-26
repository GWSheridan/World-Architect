package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;

final class AuthoringPreview {
    private AuthoringPreview() {}

    static void open(Screen parent, String name, WorldOptions options,
            Function<WorldCreationContext, WorldDimensions> dimensions) {
        if (ModpackRuntime.locked().isPresent())
            throw new IllegalArgumentException("Use a separate authoring instance to create a playtest world.");
        var minecraft = Minecraft.getInstance();
        CreateWorldScreen.openFresh(minecraft, () -> minecraft.gui.setScreen(parent));
        if (!(minecraft.gui.screen() instanceof CreateWorldScreen screen))
            throw new IllegalStateException("Could not open the new world screen.");
        var state = screen.getUiState();
        state.setName(name + " " + UUID.randomUUID().toString().substring(0, 8));
        state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
        state.setAllowCommands(true);
        try {
            var context = state.getSettings();
            state.setSettings(context.withSettings(options, dimensions.apply(context)));
        } catch (RuntimeException error) {
            minecraft.gui.setScreen(parent);
            throw error;
        }
    }
}

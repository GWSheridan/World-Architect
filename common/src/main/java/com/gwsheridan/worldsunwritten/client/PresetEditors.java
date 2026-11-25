package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds;
import com.gwsheridan.worldsunwritten.modpack.BundleManifest.Lock;
import com.gwsheridan.worldsunwritten.modpack.PresetBundle;
import java.util.List;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

public final class PresetEditors {
    private static final List<ResourceKey<WorldPreset>> PRESETS = List.of(
            WorldsUnwritten.PRESET, WorldsUnwritten.LAYERED_PRESET, WorldsUnwritten.DIMENSIONS_PRESET,
            legacy("single_block"), legacy("layered"), legacy("dimensions"));

    private PresetEditors() {}

    private static ResourceKey<WorldPreset> legacy(String name) {
        return ResourceKey.create(Registries.WORLD_PRESET, LegacyIds.id(name));
    }

    public static PresetEditor get(ResourceKey<WorldPreset> key) {
        return PRESETS.contains(key) ? DimensionManagerScreen::new : null;
    }

    public static PresetEditor bundleEditor(PresetBundle bundle) {
        if (bundle.manifest().lock() == Lock.FULL
                || bundle.manifest().locked() && bundle.manifest().editableDimensions().isEmpty() && bundle.manifest().editableFeatures().isEmpty()) {
            return (screen, context) -> new LockedPresetScreen(screen, bundle.manifest());
        }
        return DimensionManagerScreen::new;
    }
}

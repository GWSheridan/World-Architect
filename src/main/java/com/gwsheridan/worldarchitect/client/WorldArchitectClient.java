package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.WorldArchitect;
import com.gwsheridan.worldarchitect.modpack.BundleManifest.Lock;
import com.gwsheridan.worldarchitect.modpack.PresetBundle;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;

@Mod(value = WorldArchitect.ID, dist = Dist.CLIENT)
public final class WorldArchitectClient {
    public WorldArchitectClient(IEventBus modBus) {
        modBus.addListener(WorldArchitectClient::registerEditors);
        modBus.addListener(ClientEnvironment::register);
        modBus.addListener(ClientStructurePreview::register);
        modBus.addListener(ClientStudioViews::register);
        modBus.addListener(IslandModelPreview::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientStructurePreview::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientEnvironment::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArchitectTheme::background);
    }

    private static void registerEditors(RegisterPresetEditorsEvent event) {
        event.register(WorldArchitect.PRESET, DimensionManagerScreen::new);
        event.register(WorldArchitect.LAYERED_PRESET, DimensionManagerScreen::new);
        event.register(WorldArchitect.DIMENSIONS_PRESET, DimensionManagerScreen::new);
    }

    public static PresetEditor bundleEditor(PresetBundle bundle) {
        if (bundle.manifest().lock() == Lock.FULL
                || bundle.manifest().locked() && bundle.manifest().editableDimensions().isEmpty() && bundle.manifest().editableFeatures().isEmpty()) {
            return (screen, context) -> new LockedPresetScreen(screen, bundle.manifest());
        }
        return DimensionManagerScreen::new;
    }
}

package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import com.gwsheridan.worldsunwritten.modpack.BundleManifest.Lock;
import com.gwsheridan.worldsunwritten.modpack.PresetBundle;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;

@Mod(value = WorldsUnwritten.ID, dist = Dist.CLIENT)
public final class WorldsUnwrittenClient {
    public WorldsUnwrittenClient(IEventBus modBus) {
        modBus.addListener(WorldsUnwrittenClient::registerEditors);
        modBus.addListener(ClientEnvironment::register);
        modBus.addListener(ClientStructurePreview::register);
        modBus.addListener(ClientStudioViews::register);
        modBus.addListener(IslandModelPreview::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientStructurePreview::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientEnvironment::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(UnwrittenTheme::background);
    }

    private static void registerEditors(RegisterPresetEditorsEvent event) {
        event.register(WorldsUnwritten.PRESET, DimensionManagerScreen::new);
        event.register(WorldsUnwritten.LAYERED_PRESET, DimensionManagerScreen::new);
        event.register(WorldsUnwritten.DIMENSIONS_PRESET, DimensionManagerScreen::new);
        for (var name : java.util.List.of("single_block", "layered", "dimensions")) {
            event.register(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.WORLD_PRESET,
                    com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds.id(name)), DimensionManagerScreen::new);
        }
    }

    public static PresetEditor bundleEditor(PresetBundle bundle) {
        if (bundle.manifest().lock() == Lock.FULL
                || bundle.manifest().locked() && bundle.manifest().editableDimensions().isEmpty() && bundle.manifest().editableFeatures().isEmpty()) {
            return (screen, context) -> new LockedPresetScreen(screen, bundle.manifest());
        }
        return DimensionManagerScreen::new;
    }
}

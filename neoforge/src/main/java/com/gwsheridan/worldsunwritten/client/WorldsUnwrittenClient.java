package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;

@Mod(value = WorldsUnwritten.ID, dist = Dist.CLIENT)
public final class WorldsUnwrittenClient {
    public WorldsUnwrittenClient(IEventBus modBus) {
        IslandModelPreview.configure(net.minecraft.client.gui.GuiGraphicsExtractor::peekScissorStack,
                net.minecraft.client.renderer.block.BlockModelRenderState::submitMultiLayer);
        modBus.addListener(WorldsUnwrittenClient::registerEditors);
        ClientEnvironment.initialize();
        modBus.addListener((net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent event) -> {
            event.register(com.gwsheridan.worldsunwritten.environment.EnvironmentPayload.TYPE, (payload, context) -> ClientEnvironment.receive(payload));
            event.register(com.gwsheridan.worldsunwritten.structure.PlacementPreviewPayload.TYPE, (payload, context) -> ClientStructurePreview.receive(payload));
            event.register(com.gwsheridan.worldsunwritten.studio.StudioViewPayload.TYPE, (payload, context) -> ClientStudioViews.receive(payload));
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) ->
                event.register(IslandModelPreview.State.class, IslandModelPreview.Renderer::new));
        var gameBus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        gameBus.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> ClientStructurePreview.tick());
        gameBus.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> ClientEnvironment.logout());
        gameBus.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Background event) ->
                UnwrittenTheme.background(event.getScreen(), event.getGuiGraphics()));
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

}

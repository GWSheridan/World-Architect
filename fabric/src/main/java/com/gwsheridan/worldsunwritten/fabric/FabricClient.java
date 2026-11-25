package com.gwsheridan.worldsunwritten.fabric;

import com.gwsheridan.worldsunwritten.client.ClientEnvironment;
import com.gwsheridan.worldsunwritten.client.ClientStructurePreview;
import com.gwsheridan.worldsunwritten.client.ClientStudioViews;
import com.gwsheridan.worldsunwritten.client.IslandModelPreview;
import com.gwsheridan.worldsunwritten.client.UnwrittenTheme;
import com.gwsheridan.worldsunwritten.environment.EnvironmentPayload;
import com.gwsheridan.worldsunwritten.structure.PlacementPreviewPayload;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

public final class FabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientEnvironment.initialize();
        ClientPlayNetworking.registerGlobalReceiver(EnvironmentPayload.TYPE, (payload, context) -> ClientEnvironment.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(PlacementPreviewPayload.TYPE, (payload, context) -> ClientStructurePreview.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(StudioViewPayload.TYPE, (payload, context) -> ClientStudioViews.receive(payload));
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientStructurePreview.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEnvironment.logout());
        PictureInPictureRendererRegistry.register(context -> new IslandModelPreview.Renderer());
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (UnwrittenTheme.owns(screen)) {
                ScreenEvents.afterBackground(screen).register((target, graphics, x, y, partialTick) -> UnwrittenTheme.background(target, graphics));
            }
        });
    }
}

package com.gwsheridan.worldsunwritten.forge;

import com.gwsheridan.worldsunwritten.client.ClientEnvironment;
import com.gwsheridan.worldsunwritten.client.ClientStructurePreview;
import com.gwsheridan.worldsunwritten.client.IslandModelPreview;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
import net.minecraftforge.event.TickEvent;

final class ForgeClient {
    private ForgeClient() {}

    static void initialize() {
        ClientEnvironment.initialize();
        RegisterPictureInPictureRendererEvent.BUS.addListener(event -> event.register(new IslandModelPreview.Renderer()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> ClientStructurePreview.tick());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ClientEnvironment.logout());
    }
}

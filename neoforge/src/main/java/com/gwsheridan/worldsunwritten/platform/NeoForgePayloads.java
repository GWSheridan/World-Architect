package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.environment.EnvironmentPayload;
import com.gwsheridan.worldsunwritten.structure.PlacementPreviewPayload;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchEditPayload;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchStudio;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

final class NeoForgePayloads {
    private NeoForgePayloads() {}

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(EnvironmentPayload.TYPE, EnvironmentPayload.STREAM_CODEC);
        event.registrar("1.3").playToClient(PlacementPreviewPayload.TYPE, PlacementPreviewPayload.STREAM_CODEC);
        event.registrar("2.1").playToClient(StudioViewPayload.TYPE, StudioViewPayload.STREAM_CODEC);
        event.registrar("1.9").playToServer(WorkbenchEditPayload.TYPE, WorkbenchEditPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) WorkbenchStudio.handle(player, payload);
                }));
    }
}

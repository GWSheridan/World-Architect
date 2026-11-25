package com.gwsheridan.worldsunwritten.forge;

import com.gwsheridan.worldsunwritten.client.ClientEnvironment;
import com.gwsheridan.worldsunwritten.client.ClientStructurePreview;
import com.gwsheridan.worldsunwritten.client.ClientStudioViews;
import com.gwsheridan.worldsunwritten.environment.EnvironmentPayload;
import com.gwsheridan.worldsunwritten.structure.PlacementPreviewPayload;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchEditPayload;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchStudio;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;

final class ForgePayloads {
    private static final SimpleChannel CHANNEL = ChannelBuilder.named("worldsunwritten:main").networkProtocolVersion(1).simpleChannel();

    private ForgePayloads() {}

    static SimpleChannel channel() {
        return CHANNEL;
    }

    static void register() {
        CHANNEL.messageBuilder(EnvironmentPayload.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(EnvironmentPayload.STREAM_CODEC).consumerMainThread((payload, context) -> ClientEnvironment.receive(payload)).add();
        CHANNEL.messageBuilder(PlacementPreviewPayload.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(PlacementPreviewPayload.STREAM_CODEC).consumerMainThread((payload, context) -> ClientStructurePreview.receive(payload)).add();
        CHANNEL.messageBuilder(StudioViewPayload.class, NetworkDirection.PLAY_TO_CLIENT)
                .codec(StudioViewPayload.STREAM_CODEC).consumerMainThread((payload, context) -> ClientStudioViews.receive(payload)).add();
        CHANNEL.messageBuilder(WorkbenchEditPayload.class, NetworkDirection.PLAY_TO_SERVER)
                .codec(WorkbenchEditPayload.STREAM_CODEC).consumerMainThread((payload, context) -> {
                    if (context.getSender() != null) WorkbenchStudio.handle(context.getSender(), payload);
                }).add();
        CHANNEL.build();
    }
}

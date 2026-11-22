package com.gwsheridan.worldsunwritten.environment;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record EnvironmentPayload(Identifier dimension, EnvironmentProfile profile) implements CustomPacketPayload {
    public static final Type<EnvironmentPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("worldsunwritten", "environment"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnvironmentPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, EnvironmentPayload::dimension,
            ByteBufCodecs.fromCodec(EnvironmentProfile.CODEC), EnvironmentPayload::profile, EnvironmentPayload::new);

    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC);
    }
}

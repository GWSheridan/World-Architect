package com.gwsheridan.worldarchitect.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Geometry only: no inventories, block-entity metadata or paths cross this channel. */
public record PlacementPreviewPayload(CompoundTag visual) implements CustomPacketPayload {
    public static final Type<PlacementPreviewPayload> TYPE = new Type<>(Identifier.parse("worldarchitect:placement_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementPreviewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, PlacementPreviewPayload::visual, PlacementPreviewPayload::new);
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) { event.registrar("1.3").playToClient(TYPE, STREAM_CODEC); }
}

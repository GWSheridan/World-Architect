package com.gwsheridan.worldsunwritten.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Geometry only: no inventories, block-entity metadata or paths cross this channel. */
public record PlacementPreviewPayload(CompoundTag visual) implements CustomPacketPayload {
    public static final Type<PlacementPreviewPayload> TYPE = new Type<>(Identifier.parse("worldsunwritten:placement_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementPreviewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, PlacementPreviewPayload::visual, PlacementPreviewPayload::new);
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

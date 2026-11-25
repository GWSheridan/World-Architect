package com.gwsheridan.worldsunwritten.studio;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-owned authoring views contain geometry and labels, never template block-entity data. */
public record StudioViewPayload(CompoundTag view) implements CustomPacketPayload {
    public static final Type<StudioViewPayload> TYPE =
            new Type<>(Identifier.parse("worldsunwritten:studio_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StudioViewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.COMPOUND_TAG, StudioViewPayload::view, StudioViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}

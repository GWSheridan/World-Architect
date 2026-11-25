package com.gwsheridan.worldsunwritten.workbench;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WorkbenchEditPayload(String token,int serial,String operation,String name,String document) implements CustomPacketPayload {
    public static final Type<WorkbenchEditPayload> TYPE=new Type<>(Identifier.parse("worldsunwritten:workbench_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WorkbenchEditPayload> STREAM_CODEC=StreamCodec.composite(
        ByteBufCodecs.stringUtf8(36),WorkbenchEditPayload::token,ByteBufCodecs.VAR_INT,WorkbenchEditPayload::serial,
        ByteBufCodecs.stringUtf8(16),WorkbenchEditPayload::operation,ByteBufCodecs.stringUtf8(64),WorkbenchEditPayload::name,
        ByteBufCodecs.stringUtf8(WorkbenchScenario.MAX_BYTES),WorkbenchEditPayload::document,WorkbenchEditPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}

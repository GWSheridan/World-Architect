package com.gwsheridan.worldsunwritten.packtest;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FabricPackTest implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> PackTestMod.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(PackTestMod::tick);
    }
}

package com.gwsheridan.worldsunwritten.packtest;

import com.gwsheridan.worldsunwritten.api.DimensionAdapter;
import com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FabricPackTest implements DedicatedServerModInitializer {
    @SuppressWarnings("unchecked")
    public static void registerAdapters() {
        var registry = (Registry<DimensionAdapter>) BuiltInRegistries.REGISTRY.getValue(DimensionAdapterRegistry.KEY.identifier());
        Registry.register(registry, PackTestAdapter.ID, new PackTestAdapter());
    }

    @Override
    public void onInitializeServer() {
        PackTestAdapter.verifyRegistration();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> PackTestMod.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(PackTestMod::tick);
    }
}

package com.gwsheridan.worldsunwritten.platform;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public record NeoForgeRegistrations(IEventBus bus, String namespace) implements WorldgenRegistration.Registrar {
    @Override
    public <T> void register(ResourceKey<? extends Registry<T>> registry, String name, Supplier<? extends T> value) {
        DeferredRegister<T> entries = DeferredRegister.create(registry, namespace);
        entries.register(name, value);
        entries.register(bus);
    }
}

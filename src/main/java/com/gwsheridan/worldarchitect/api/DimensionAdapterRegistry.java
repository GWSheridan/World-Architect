package com.gwsheridan.worldarchitect.api;

import com.gwsheridan.worldarchitect.WorldArchitect;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class DimensionAdapterRegistry {
    public static final ResourceKey<Registry<DimensionAdapter>> KEY = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(WorldArchitect.ID, "dimension_adapter"));
    private static final Registry<DimensionAdapter> REGISTRY = new RegistryBuilder<>(KEY).create();

    private DimensionAdapterRegistry() {}

    public static Optional<DimensionAdapter> get(Identifier dimensionId) {
        return REGISTRY.getOptional(dimensionId);
    }

    public static void registerRegistry(NewRegistryEvent event) {
        event.register(REGISTRY);
    }
}

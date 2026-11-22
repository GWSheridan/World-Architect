package com.gwsheridan.worldarchitect.api;

import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class DimensionAdapterRegistry {
    public static final ResourceKey<Registry<DimensionAdapter>> KEY = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(LegacyIds.NAMESPACE, "dimension_adapter"));
    private static final Registry<DimensionAdapter> REGISTRY = new RegistryBuilder<>(KEY).create();

    private DimensionAdapterRegistry() {}

    public static Optional<DimensionAdapter> get(Identifier dimensionId) { return com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry.get(dimensionId).map(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyAdapters::legacy); }

    public static java.util.Set<Identifier> registeredIds() { return REGISTRY.keySet(); }
    public static Optional<DimensionAdapter> registered(Identifier id) { return REGISTRY.getOptional(id); }
    public static void registerRegistry(NewRegistryEvent event) {
        event.register(REGISTRY);
    }
}

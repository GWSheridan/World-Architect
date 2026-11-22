package com.gwsheridan.worldsunwritten.api;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class DimensionAdapterRegistry {
    public static final ResourceKey<Registry<DimensionAdapter>> KEY = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(WorldsUnwritten.ID, "dimension_adapter"));
    private static final Registry<DimensionAdapter> REGISTRY = new RegistryBuilder<>(KEY).create();

    private DimensionAdapterRegistry() {}

    public static Optional<DimensionAdapter> get(Identifier dimensionId) {
        return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyAdapters.dimension(dimensionId, REGISTRY.getOptional(dimensionId));
    }

    public static java.util.Set<Identifier> registeredIds() { return REGISTRY.keySet(); }
    public static void registerRegistry(NewRegistryEvent event) {
        event.register(REGISTRY);
    }
}

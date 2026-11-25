package com.gwsheridan.worldsunwritten.api;

import com.gwsheridan.worldsunwritten.platform.AdapterLookup;
import com.gwsheridan.worldsunwritten.platform.Platform;
import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public final class DimensionAdapterRegistry {
    public static final ResourceKey<Registry<DimensionAdapter>> KEY = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(WorldsUnwritten.ID, "dimension_adapter"));
    private static final AdapterLookup<DimensionAdapter> REGISTRY = Platform.get().createAdapterRegistry(KEY);

    private DimensionAdapterRegistry() {}

    public static Optional<DimensionAdapter> get(Identifier dimensionId) {
        return Platform.get().dimensionAdapter(dimensionId, REGISTRY.values().apply(dimensionId));
    }

    public static java.util.Set<Identifier> registeredIds() { return REGISTRY.keys().get(); }
    public static void initialize() {}
}

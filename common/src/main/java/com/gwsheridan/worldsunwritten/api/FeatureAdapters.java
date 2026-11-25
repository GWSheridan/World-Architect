package com.gwsheridan.worldsunwritten.api;

import com.gwsheridan.worldsunwritten.platform.AdapterLookup;
import com.gwsheridan.worldsunwritten.platform.Platform;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;

public final class FeatureAdapters {
    public static final ResourceKey<Registry<FeatureAdapter>> KEY = ResourceKey.createRegistryKey(Identifier.parse("worldsunwritten:feature_adapter"));
    private static final AdapterLookup<FeatureAdapter> REGISTRY = Platform.get().createAdapterRegistry(KEY);
    private static final FeatureAdapter SIMPLE = new FeatureAdapter() {
        public int version() { return 1; }
        public int workLimit(Feature feature) {
            return feature instanceof SimpleBlockFeature simple && !simple.scheduleTick()
                    && simple.toPlace().value() instanceof SimpleStateProvider provider
                    && BlockCompatibility.feature(provider.state()).isSuccess() ? 1 : 0;
        }
    };
    private FeatureAdapters() {}
    public static Identifier type(Feature feature) { return BuiltInRegistries.FEATURE_TYPE.getKey(feature.codec()); }
    public static Optional<FeatureAdapter> get(Feature feature) {
        var registered = Platform.get().featureAdapter(type(feature), REGISTRY.values().apply(type(feature)));
        return registered.isPresent() ? registered : feature instanceof SimpleBlockFeature ? Optional.of(SIMPLE) : Optional.empty();
    }
    public static java.util.Set<Identifier> registeredIds() { return REGISTRY.keys().get(); }
    public static void initialize() {}
}

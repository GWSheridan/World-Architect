package com.gwsheridan.worldsunwritten.compatibility.legacy;

import com.gwsheridan.worldsunwritten.api.DimensionAdapter;
import com.gwsheridan.worldsunwritten.api.FeatureAdapter;
import com.gwsheridan.worldsunwritten.api.StructureAdapter;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Both registries retain their native value types; the runtime sees one effective adapter. */
public final class LegacyAdapters {
    private LegacyAdapters() {}
    private static void conflict(Identifier id, boolean present, boolean old) {
        if (present && old) throw new IllegalStateException("Worlds Unwritten found conflicting current and legacy adapters for " + id);
    }
    public static Optional<DimensionAdapter> dimension(Identifier id, Optional<DimensionAdapter> current) {
        var old = com.gwsheridan.worldarchitect.api.DimensionAdapterRegistry.registered(id); conflict(id, current.isPresent(), old.isPresent());
        return current.isPresent() ? current : old.map(DimensionBridge::new);
    }
    public static Optional<FeatureAdapter> feature(Identifier id, Optional<FeatureAdapter> current) {
        var old = com.gwsheridan.worldarchitect.api.FeatureAdapters.registered(id); conflict(id, current.isPresent(), old.isPresent());
        return current.isPresent() ? current : old.map(FeatureBridge::new);
    }
    public static Optional<StructureAdapter> structure(Identifier id, Optional<StructureAdapter> current) {
        var old = com.gwsheridan.worldarchitect.api.StructureAdapters.registered(id); conflict(id, current.isPresent(), old.isPresent());
        return current.isPresent() ? current : old.map(StructureBridge::new);
    }
    private record DimensionBridge(com.gwsheridan.worldarchitect.api.DimensionAdapter delegate) implements DimensionAdapter {
        public int version() { return delegate.version(); }
        public boolean supportsPortalPolicies() { return delegate.supportsPortalPolicies(); }
        public boolean supportsCellularTerrain() { return delegate.supportsCellularTerrain(); }
        public Support inspect(ResourceKey<LevelStem> id, LevelStem stem) { return LegacyValues.current(delegate.inspect(id, stem)); }
    }
    private record FeatureBridge(com.gwsheridan.worldarchitect.api.FeatureAdapter delegate) implements FeatureAdapter {
        public int version() { return delegate.version(); }
        public int workLimit(Feature feature) { return delegate.workLimit(feature); }
    }
    private record StructureBridge(com.gwsheridan.worldarchitect.api.StructureAdapter delegate) implements StructureAdapter {
        public int version() { return delegate.version(); }
        public boolean supports(Structure structure) { return delegate.supports(structure); }
    }
    public static com.gwsheridan.worldarchitect.api.DimensionAdapter legacy(DimensionAdapter adapter) {
        if (adapter instanceof DimensionBridge bridge) return bridge.delegate();
        return new com.gwsheridan.worldarchitect.api.DimensionAdapter() {
            public int version() { return adapter.version(); }
            public boolean supportsPortalPolicies() { return adapter.supportsPortalPolicies(); }
            public boolean supportsCellularTerrain() { return adapter.supportsCellularTerrain(); }
            public Support inspect(ResourceKey<LevelStem> id, LevelStem stem) { return LegacyValues.legacy(adapter.inspect(id, stem)); }
        };
    }
    public static com.gwsheridan.worldarchitect.api.FeatureAdapter legacy(FeatureAdapter adapter) {
        if (adapter instanceof FeatureBridge bridge) return bridge.delegate();
        return new com.gwsheridan.worldarchitect.api.FeatureAdapter() {
            public int version() { return adapter.version(); }
            public int workLimit(Feature feature) { return adapter.workLimit(feature); }
        };
    }
    public static com.gwsheridan.worldarchitect.api.StructureAdapter legacy(StructureAdapter adapter) {
        if (adapter instanceof StructureBridge bridge) return bridge.delegate();
        return new com.gwsheridan.worldarchitect.api.StructureAdapter() {
            public int version() { return adapter.version(); }
            public boolean supports(Structure structure) { return adapter.supports(structure); }
        };
    }
    public static void validate() {
        unique(com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry.registeredIds(), com.gwsheridan.worldarchitect.api.DimensionAdapterRegistry.registeredIds());
        unique(com.gwsheridan.worldsunwritten.api.FeatureAdapters.registeredIds(), com.gwsheridan.worldarchitect.api.FeatureAdapters.registeredIds());
        unique(com.gwsheridan.worldsunwritten.api.StructureAdapters.registeredIds(), com.gwsheridan.worldarchitect.api.StructureAdapters.registeredIds());
    }
    private static void unique(Set<Identifier> current, Set<Identifier> old) { for (var id : current) conflict(id, true, old.contains(id)); }
}

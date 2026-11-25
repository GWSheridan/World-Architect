package com.gwsheridan.worldsunwritten.api;

import com.gwsheridan.worldsunwritten.platform.AdapterLookup;
import com.gwsheridan.worldsunwritten.platform.Platform;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class StructureAdapters {
    public static final ResourceKey<Registry<StructureAdapter>> KEY = ResourceKey.createRegistryKey(Identifier.parse("worldsunwritten:structure_adapter"));
    private static final AdapterLookup<StructureAdapter> REGISTRY = Platform.get().createAdapterRegistry(KEY);
    private StructureAdapters() {}
    public static Identifier type(Structure structure) { return BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type()); }
    public static Optional<StructureAdapter> get(Structure structure) { return Platform.get().structureAdapter(type(structure), REGISTRY.values().apply(type(structure))); }
    public static java.util.Set<Identifier> registeredIds() { return REGISTRY.keys().get(); }
    public static void initialize() {}
}

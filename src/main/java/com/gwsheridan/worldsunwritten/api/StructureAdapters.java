package com.gwsheridan.worldsunwritten.api;

import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class StructureAdapters {
    public static final ResourceKey<Registry<StructureAdapter>> KEY = ResourceKey.createRegistryKey(Identifier.parse("worldsunwritten:structure_adapter"));
    private static final Registry<StructureAdapter> REGISTRY = new RegistryBuilder<>(KEY).create();
    private StructureAdapters() {}
    public static Identifier type(Structure structure) { return BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type()); }
    public static Optional<StructureAdapter> get(Structure structure) { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyAdapters.structure(type(structure), REGISTRY.getOptional(type(structure))); }
    public static java.util.Set<Identifier> registeredIds() { return REGISTRY.keySet(); }
    public static void registerRegistry(NewRegistryEvent event) { event.register(REGISTRY); }
}

package com.gwsheridan.worldarchitect.api;

import com.google.gson.JsonObject;
import com.gwsheridan.worldarchitect.structure.ModularStructure;
import com.gwsheridan.worldarchitect.structure.NativeTemplate;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Validated, detached definitions. Register returned structures through a datapack registry. */
public final class StructureTemplates {
    private StructureTemplates() {}
    public static NativeTemplate read(CompoundTag tag, HolderLookup.Provider registries) throws IOException { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.StructureTemplates.read(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(tag), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(registries))); }
    public static ModularStructure modular(JsonObject definition, HolderLookup.Provider registries) throws IOException { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.StructureTemplates.modular(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(definition), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(registries))); }
}

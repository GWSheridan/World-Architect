package com.gwsheridan.worldsunwritten.compatibility.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/** Presentation-only mapping leaves the frozen legacy preset tag and generation resources intact. */
public final class LegacyPresets {
    private static final Set<String> PRESETS = Set.of("single_block", "layered", "dimensions");
    private static final Set<String> TYPES = Set.of("single_block", "layered", "void", "skyblock", "cells", "regions", "terrain_regions", "end_arena", "modular", "module");
    private LegacyPresets() {}
    public static Holder<WorldPreset> current(Holder<WorldPreset> existing, HolderLookup.Provider registries) {
        var key = existing.unwrapKey(); if (key.isEmpty()) return existing;
        var id = key.orElseThrow().identifier();
        if (!id.getNamespace().equals(LegacyIds.NAMESPACE) || !PRESETS.contains(id.getPath())) return existing;
        var next = registries.lookupOrThrow(Registries.WORLD_PRESET).get(ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath(LegacyIds.CURRENT, id.getPath())));
        if (next.isEmpty()) return existing;
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var old = WorldPreset.DIRECT_CODEC.encodeStart(ops, existing.value()).result();
        var current = WorldPreset.DIRECT_CODEC.encodeStart(ops, next.orElseThrow().value()).result();
        // A datapack override is retained unless its definition is actually equivalent.
        return old.isPresent() && current.isPresent() && presentation(old.orElseThrow()).equals(presentation(current.orElseThrow())) ? next.orElseThrow() : existing;
    }
    private static JsonElement presentation(JsonElement value) {
        if (value.isJsonObject()) { var copy = new JsonObject(); value.getAsJsonObject().entrySet().forEach(entry -> copy.add(entry.getKey(), presentation(entry.getValue()))); return copy; }
        if (value.isJsonArray()) { var copy = new JsonArray(); value.getAsJsonArray().forEach(entry -> copy.add(presentation(entry))); return copy; }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString(), prefix = LegacyIds.NAMESPACE + ":";
            if (text.startsWith(prefix) && TYPES.contains(text.substring(prefix.length()))) return new JsonPrimitive(LegacyIds.CURRENT + ":" + text.substring(prefix.length()));
        }
        return value.deepCopy();
    }
}

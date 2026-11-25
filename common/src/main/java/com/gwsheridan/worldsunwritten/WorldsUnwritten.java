package com.gwsheridan.worldsunwritten;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

public final class WorldsUnwritten {
    public static final String ID = "worldsunwritten";
    public static final Identifier GENERATOR_ID = Identifier.fromNamespaceAndPath(ID, "single_block");
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, GENERATOR_ID);
    public static final ResourceKey<WorldPreset> LAYERED_PRESET = preset("layered");
    public static final ResourceKey<WorldPreset> DIMENSIONS_PRESET = preset("dimensions");

    private WorldsUnwritten() {}

    private static ResourceKey<WorldPreset> preset(String name) {
        return ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath(ID, name));
    }
}

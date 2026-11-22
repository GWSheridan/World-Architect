package com.gwsheridan.worldarchitect.api;

import com.gwsheridan.worldsunwritten.config.TerrainMaterial;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Capability checks apply equally to vanilla and modded blocks; no namespace allowlist. */
public final class BlockCompatibility {
    public static final Codec<BlockState> TERRAIN_CODEC = TerrainMaterial.CODEC;
    private BlockCompatibility() {}
    public static DataResult<BlockState> terrain(BlockState state) { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.BlockCompatibility.terrain(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(state))); }
    public static DataResult<BlockState> feature(BlockState state) { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.BlockCompatibility.feature(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(state))); }
}

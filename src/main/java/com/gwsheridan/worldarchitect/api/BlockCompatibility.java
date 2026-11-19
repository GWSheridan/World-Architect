package com.gwsheridan.worldarchitect.api;

import com.gwsheridan.worldarchitect.config.TerrainMaterial;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Capability checks apply equally to vanilla and modded blocks; no namespace allowlist. */
public final class BlockCompatibility {
    public static final Codec<BlockState> TERRAIN_CODEC = TerrainMaterial.CODEC;
    private BlockCompatibility() {}
    public static DataResult<BlockState> terrain(BlockState state) { return TerrainMaterial.validate(state); }
    public static DataResult<BlockState> feature(BlockState state) {
        return terrain(state).flatMap(valid -> valid.getBlock() instanceof FallingBlock
                ? DataResult.error(() -> "Feature blocks must not fall.") : DataResult.success(valid));
    }
}

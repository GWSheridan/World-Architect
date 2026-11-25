package com.gwsheridan.worldsunwritten.config;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class TerrainMaterial {
    public static final Codec<BlockState> CODEC = Codec.STRING.comapFlatMap(TerrainMaterial::parse, BlockStateParser::serialize);

    private TerrainMaterial() {}

    public static DataResult<BlockState> parse(String input) {
        try {
            return validate(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, input, false).blockState());
        } catch (CommandSyntaxException e) {
            return DataResult.error(() -> e.getRawMessage().getString());
        }
    }

    public static DataResult<BlockState> validate(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.isRandomlyTicking()
                || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return DataResult.error(() -> "Terrain requires a full solid block without fluids, block entities or random ticks.");
        }
        return DataResult.success(state);
    }
}

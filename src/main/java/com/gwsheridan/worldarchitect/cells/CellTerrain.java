package com.gwsheridan.worldarchitect.cells;

import com.gwsheridan.worldarchitect.config.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Cell surfaces may tick naturally. Legacy layered-material restrictions are unchanged. */
public final class CellTerrain {
    private CellTerrain(){}
    public static final Codec<BlockState> MATERIAL=Codec.STRING.comapFlatMap(CellTerrain::parse,BlockStateParser::serialize);
    private static final Codec<LayeredProfile.Layer> LAYER=RecordCodecBuilder.create(b->b.group(MATERIAL.fieldOf("material").forGetter(LayeredProfile.Layer::material),Codec.intRange(1,383).fieldOf("depth").forGetter(LayeredProfile.Layer::depth)).apply(b,LayeredProfile.Layer::new));
    public static final Codec<LayeredProfile> CODEC=RecordCodecBuilder.<LayeredProfile>create(b->b.group(
            TerrainMaterial.CODEC.fieldOf("base_material").forGetter(LayeredProfile::base),LAYER.listOf(0,32).fieldOf("layers").forGetter(LayeredProfile::layers),
            TerrainShape.CODEC.forGetter(LayeredProfile::shape),LayeredProfile.Stratum.CODEC.listOf(0,16).optionalFieldOf("strata",List.of()).forGetter(LayeredProfile::strata)
    ).apply(b,LayeredProfile::new)).validate(LayeredProfile::validate);
    public static DataResult<BlockState> parse(String text){try{var state=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,text,false).blockState();return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO)&&state.getFluidState().isEmpty()&&!state.hasBlockEntity()&&!(state.getBlock() instanceof FallingBlock)?DataResult.success(state):DataResult.error(()->"Choose a full surface block without fluids, gravity or block entities.");}catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){return DataResult.error(()->error.getRawMessage().getString());}}
}

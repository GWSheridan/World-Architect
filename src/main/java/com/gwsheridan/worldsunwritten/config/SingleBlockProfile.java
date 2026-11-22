package com.gwsheridan.worldsunwritten.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

public record SingleBlockProfile(BlockState material, TerrainShape shape) {
    public static final int VERSION = 1;
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;

    public static final MapCodec<SingleBlockProfile> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            TerrainMaterial.CODEC.fieldOf("material").forGetter(SingleBlockProfile::material),
            TerrainShape.CODEC.forGetter(SingleBlockProfile::shape)
    ).apply(builder, SingleBlockProfile::new));

    public SingleBlockProfile(int generatorVersion, BlockState material, int elevation, int relief) {
        this(material, TerrainShape.legacy(generatorVersion, elevation, relief));
    }

    public int generatorVersion() {
        return shape.version();
    }

    public int elevation() {
        return shape.elevation();
    }

    public int relief() {
        return shape.relief();
    }
}

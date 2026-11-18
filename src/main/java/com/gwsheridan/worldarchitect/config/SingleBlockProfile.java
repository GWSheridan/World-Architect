package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

public record SingleBlockProfile(int generatorVersion, BlockState material, int elevation, int relief) {
    public static final int VERSION = 1;
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;

    private static final Codec<Integer> VERSION_CODEC = Codec.INT.validate(version -> version == VERSION
            ? DataResult.success(version)
            : DataResult.error(() -> "Unsupported World Architect generator version " + version));

    public static final MapCodec<SingleBlockProfile> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            VERSION_CODEC.fieldOf("generator_version").forGetter(SingleBlockProfile::generatorVersion),
            TerrainMaterial.CODEC.fieldOf("material").forGetter(SingleBlockProfile::material),
            Codec.intRange(TerrainShape.MIN_ELEVATION, TerrainShape.MAX_ELEVATION).fieldOf("elevation").forGetter(SingleBlockProfile::elevation),
            Codec.intRange(0, TerrainShape.MAX_RELIEF).fieldOf("relief").forGetter(SingleBlockProfile::relief)
    ).apply(builder, SingleBlockProfile::new));

    public TerrainShape shape() {
        return new TerrainShape(elevation, relief);
    }
}

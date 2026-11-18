package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

public record LayeredProfile(int generatorVersion, BlockState base, List<Layer> layers, int elevation, int relief) {
    public static final int MAX_LAYERS = 32;
    public static final int MAX_DEPTH = SingleBlockProfile.HEIGHT - 1;
    public static final MapCodec<LayeredProfile> CODEC = RecordCodecBuilder.<LayeredProfile>mapCodec(builder -> builder.group(
            Codec.intRange(1, 1).fieldOf("generator_version").forGetter(LayeredProfile::generatorVersion),
            TerrainMaterial.CODEC.fieldOf("base_material").forGetter(LayeredProfile::base),
            Layer.CODEC.listOf(0, MAX_LAYERS).fieldOf("layers").forGetter(LayeredProfile::layers),
            Codec.intRange(TerrainShape.MIN_ELEVATION, TerrainShape.MAX_ELEVATION).fieldOf("elevation").forGetter(LayeredProfile::elevation),
            Codec.intRange(0, TerrainShape.MAX_RELIEF).fieldOf("relief").forGetter(LayeredProfile::relief)
    ).apply(builder, LayeredProfile::new)).validate(LayeredProfile::validate);

    public LayeredProfile {
        layers = List.copyOf(layers);
    }

    public DataResult<LayeredProfile> validate() {
        if (layers.stream().mapToInt(Layer::depth).sum() > MAX_DEPTH) {
            return DataResult.error(() -> "Combined layer depth must be at most " + MAX_DEPTH + " blocks.");
        }
        return DataResult.success(this);
    }

    public TerrainShape shape() {
        return new TerrainShape(elevation, relief);
    }

    public record Layer(BlockState material, int depth) {
        private static final Codec<Layer> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                TerrainMaterial.CODEC.fieldOf("material").forGetter(Layer::material),
                Codec.intRange(1, MAX_DEPTH).fieldOf("depth").forGetter(Layer::depth)
        ).apply(builder, Layer::new));
    }
}

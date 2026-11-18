package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.config.DimensionProfile;
import com.gwsheridan.worldarchitect.config.LayeredProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public final class LayeredGenerator extends SolidColumnGenerator {
    public static final MapCodec<LayeredGenerator> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Biome.CODEC.fieldOf("biome").forGetter(generator -> generator.biome),
            LayeredProfile.CODEC.fieldOf("profile").forGetter(LayeredGenerator::profile),
            DimensionProfile.CODEC.optionalFieldOf("dimension_profile").forGetter(LayeredGenerator::dimensionProfile)
    ).apply(builder, LayeredGenerator::new));

    private final LayeredProfile profile;
    private final BlockState[] layers;

    public LayeredGenerator(Holder<Biome> biome, LayeredProfile profile, Optional<DimensionProfile> dimensionProfile) {
        super(biome, profile.elevation(), profile.relief(), dimensionProfile);
        this.profile = profile;
        this.layers = new BlockState[profile.layers().stream().mapToInt(LayeredProfile.Layer::depth).sum()];
        int start = 0;
        for (LayeredProfile.Layer layer : profile.layers()) {
            int end = start + layer.depth();
            Arrays.fill(layers, start, end, layer.material());
            start = end;
        }
    }

    public LayeredProfile profile() {
        return profile;
    }

    public LayeredGenerator withProfile(LayeredProfile newProfile) {
        return new LayeredGenerator(biome, newProfile, dimensionProfile());
    }

    @Override
    public LayeredGenerator withDimensionProfile(DimensionProfile newProfile) {
        return new LayeredGenerator(biome, profile, Optional.of(newProfile));
    }

    @Override
    protected MapCodec<LayeredGenerator> codec() {
        return CODEC;
    }

    @Override
    protected BlockState materialAtDepth(int depth) {
        return depth < layers.length ? layers[depth] : profile.base();
    }
}

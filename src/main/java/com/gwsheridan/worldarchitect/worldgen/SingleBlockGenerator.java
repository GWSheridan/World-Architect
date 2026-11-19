package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.config.DimensionProfile;
import com.gwsheridan.worldarchitect.config.SingleBlockProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public final class SingleBlockGenerator extends SolidColumnGenerator {
    public static final MapCodec<SingleBlockGenerator> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Biome.CODEC.fieldOf("biome").forGetter(generator -> generator.biome),
            SingleBlockProfile.CODEC.fieldOf("profile").forGetter(SingleBlockGenerator::profile),
            DimensionProfile.CODEC.optionalFieldOf("dimension_profile").forGetter(SingleBlockGenerator::dimensionProfile)
    ).apply(builder, SingleBlockGenerator::new));

    private final SingleBlockProfile profile;

    public SingleBlockGenerator(Holder<Biome> biome, SingleBlockProfile profile, Optional<DimensionProfile> dimensionProfile) {
        super(biome, profile.shape(), List.of(), dimensionProfile);
        this.profile = profile;
    }

    public SingleBlockProfile profile() {
        return profile;
    }

    public SingleBlockGenerator withProfile(SingleBlockProfile newProfile) {
        return new SingleBlockGenerator(biome, newProfile, dimensionProfile());
    }

    @Override
    public SingleBlockGenerator withDimensionProfile(DimensionProfile newProfile) {
        return new SingleBlockGenerator(biome, profile, Optional.of(newProfile));
    }

    @Override
    protected MapCodec<SingleBlockGenerator> codec() {
        return CODEC;
    }

    @Override
    protected BlockState materialAtDepth(int depth) {
        return profile.material();
    }
}

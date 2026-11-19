package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;

public record LayeredProfile(BlockState base, List<Layer> layers, TerrainShape shape, List<Stratum> strata) {
    public static final int MAX_LAYERS = 32;
    public static final int MAX_DEPTH = SingleBlockProfile.HEIGHT - 1;
    public static final MapCodec<LayeredProfile> CODEC = RecordCodecBuilder.<LayeredProfile>mapCodec(builder -> builder.group(
            TerrainMaterial.CODEC.fieldOf("base_material").forGetter(LayeredProfile::base),
            Layer.CODEC.listOf(0, MAX_LAYERS).fieldOf("layers").forGetter(LayeredProfile::layers),
            TerrainShape.CODEC.forGetter(LayeredProfile::shape),
            Stratum.CODEC.listOf(0, ContentProfile.MAX_RULES).optionalFieldOf("strata", List.of()).forGetter(LayeredProfile::strata)
    ).apply(builder, LayeredProfile::new)).validate(LayeredProfile::validate);

    public LayeredProfile {
        layers = List.copyOf(layers);
        strata = List.copyOf(strata);
    }

    public LayeredProfile(int generatorVersion, BlockState base, List<Layer> layers, int elevation, int relief) {
        this(base, layers, TerrainShape.legacy(generatorVersion, elevation, relief), List.of());
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

    public DataResult<LayeredProfile> validate() {
        if (layers.stream().mapToInt(Layer::depth).sum() > MAX_DEPTH) {
            return DataResult.error(() -> "Combined layer depth must be at most " + MAX_DEPTH + " blocks.");
        }
        if (shape.advanced().isEmpty() && !strata.isEmpty()) {
            return DataResult.error(() -> "Layers with biome or height rules require generator version 2.");
        }
        return DataResult.success(this);
    }

    public record Layer(BlockState material, int depth) {
        private static final Codec<Layer> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                TerrainMaterial.CODEC.fieldOf("material").forGetter(Layer::material),
                Codec.intRange(1, MAX_DEPTH).fieldOf("depth").forGetter(Layer::depth)
        ).apply(builder, Layer::new));
    }

    public enum Anchor implements StringRepresentable {
        HEIGHT, DEPTH;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Stratum(BlockState material, Anchor anchor, int from, int to, List<Holder<Biome>> biomes, int transition) {
        private static final Codec<Integer> BOUND = Codec.intRange(DimensionType.MIN_Y, DimensionType.Y_SIZE - 1);
        public static final Codec<Stratum> CODEC = RecordCodecBuilder.<Stratum>create(builder -> builder.group(
                TerrainMaterial.CODEC.fieldOf("material").forGetter(Stratum::material),
                StringRepresentable.fromEnum(Anchor::values).fieldOf("anchor").forGetter(Stratum::anchor),
                BOUND.fieldOf("from").forGetter(Stratum::from),
                BOUND.fieldOf("to").forGetter(Stratum::to),
                Biome.CODEC.listOf(0, ContentProfile.MAX_RULES).optionalFieldOf("biomes", List.of()).forGetter(Stratum::biomes),
                Codec.intRange(0, 32).optionalFieldOf("transition", 0).forGetter(Stratum::transition)
        ).apply(builder, Stratum::new)).validate(Stratum::validate);

        public Stratum {
            biomes = List.copyOf(biomes);
        }

        private DataResult<Stratum> validate() {
            if (from > to || (anchor == Anchor.DEPTH && from < 0)) {
                return DataResult.error(() -> "Layer bounds must be ordered and depths must be nonnegative.");
            }
            if (biomes.stream().anyMatch(biome -> biome.unwrapKey().isEmpty()) || new HashSet<>(biomes).size() != biomes.size()) {
                return DataResult.error(() -> "Layer biome filters must be distinct registered biomes.");
            }
            return DataResult.success(this);
        }
    }
}

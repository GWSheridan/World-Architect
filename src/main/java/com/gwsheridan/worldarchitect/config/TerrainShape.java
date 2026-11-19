package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

public record TerrainShape(int elevation, int relief, Optional<AdvancedTerrain> advanced) {
    public static final int MIN_ELEVATION = -2032;
    public static final int MAX_ELEVATION = 2028;
    public static final int MAX_RELIEF = 80;

    public static final TerrainShape FLAT = new TerrainShape(68, 0);
    public static final TerrainShape HILLS = new TerrainShape(68, 44);
    public static final TerrainShape HIGHLANDS = new TerrainShape(108, 72);

    public static final MapCodec<TerrainShape> CODEC = RecordCodecBuilder.<EncodedShape>mapCodec(builder -> builder.group(
            Codec.intRange(SingleBlockProfile.VERSION, AdvancedTerrain.VERSION).fieldOf("generator_version").forGetter(EncodedShape::version),
            Codec.intRange(MIN_ELEVATION, MAX_ELEVATION).fieldOf("elevation").forGetter(encoded -> encoded.shape().elevation()),
            Codec.intRange(0, MAX_RELIEF).fieldOf("relief").forGetter(encoded -> encoded.shape().relief()),
            AdvancedTerrain.CODEC.optionalFieldOf("terrain").forGetter(encoded -> encoded.shape().advanced())
    ).apply(builder, (version, elevation, relief, advanced) -> new EncodedShape(version, new TerrainShape(elevation, relief, advanced))))
            .flatXmap(encoded -> encoded.version() == encoded.shape().version()
                    ? DataResult.success(encoded.shape())
                    : DataResult.error(() -> "Generator version 2 requires a terrain object. Version 1 forbids it."),
                    shape -> DataResult.success(new EncodedShape(shape.version(), shape)));

    public TerrainShape(int elevation, int relief) {
        this(elevation, relief, Optional.empty());
    }

    static TerrainShape legacy(int version, int elevation, int relief) {
        if (version != SingleBlockProfile.VERSION) {
            throw new IllegalArgumentException("This constructor only accepts generator version 1.");
        }
        return new TerrainShape(elevation, relief);
    }

    public int version() {
        return advanced.isPresent() ? AdvancedTerrain.VERSION : SingleBlockProfile.VERSION;
    }

    private record EncodedShape(int version, TerrainShape shape) {}
}

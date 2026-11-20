package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

public record TerrainShape(int elevation, int relief, Optional<AdvancedTerrain> advanced, Optional<RegionalTerrain> regions) {
    public static final int MIN_ELEVATION = -2032;
    public static final int MAX_ELEVATION = 2028;
    public static final int MAX_RELIEF = 80;
    public static final TerrainShape FLAT = new TerrainShape(68, 0);
    public static final TerrainShape HILLS = new TerrainShape(68, 44);
    public static final TerrainShape HIGHLANDS = new TerrainShape(108, 72);
    public static final MapCodec<TerrainShape> CODEC = RecordCodecBuilder.<EncodedShape>mapCodec(builder -> builder.group(
            Codec.intRange(SingleBlockProfile.VERSION, RegionalTerrain.POLYGON_VERSION).fieldOf("generator_version").forGetter(EncodedShape::version),
            Codec.intRange(MIN_ELEVATION, MAX_ELEVATION).fieldOf("elevation").forGetter(encoded -> encoded.shape().elevation()),
            Codec.intRange(0, MAX_RELIEF).fieldOf("relief").forGetter(encoded -> encoded.shape().relief()),
            AdvancedTerrain.CODEC.optionalFieldOf("terrain").forGetter(encoded -> encoded.shape().advanced()),
            RegionalTerrain.CODEC.optionalFieldOf("terrain_regions").forGetter(encoded -> encoded.shape().regions())
    ).apply(builder, (version, elevation, relief, advanced, regions) -> new EncodedShape(version, new TerrainShape(elevation, relief, advanced, regions))))
            .flatXmap(encoded -> encoded.version() == encoded.shape().version()
                    ? encoded.shape().validate()
                    : DataResult.error(() -> "Versions 1/2 retain legacy terrain; version 3 requires regions and version 4 requires a polygon."),
                    shape -> shape.validate().map(checked -> new EncodedShape(checked.version(), checked)));
    public TerrainShape(int elevation, int relief) { this(elevation, relief, Optional.empty(), Optional.empty()); }
    /** Retained API 1 constructor. */
    public TerrainShape(int elevation, int relief, Optional<AdvancedTerrain> advanced) { this(elevation, relief, advanced, Optional.empty()); }
    public DataResult<TerrainShape> validate() {
        if(regions.isPresent()) {
            if(advanced.isEmpty() || advanced.orElseThrow().landform()==AdvancedTerrain.Landform.FLOATING_ISLANDS)
                return DataResult.error(() -> "Regional terrain requires a solid version 2 base landform.");
            return regions.orElseThrow().validate().map(ignored -> this);
        }
        return DataResult.success(this);
    }
    static TerrainShape legacy(int version, int elevation, int relief) {
        if (version != SingleBlockProfile.VERSION) throw new IllegalArgumentException("This constructor only accepts generator version 1.");
        return new TerrainShape(elevation, relief);
    }
    public int version() { return regions.isPresent() ? regions.orElseThrow().version() : advanced.isPresent() ? AdvancedTerrain.VERSION : SingleBlockProfile.VERSION; }
    private record EncodedShape(int version, TerrainShape shape) {}
}

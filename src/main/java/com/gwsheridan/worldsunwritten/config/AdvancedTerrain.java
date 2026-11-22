package com.gwsheridan.worldsunwritten.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public record AdvancedTerrain(Landform landform, int scale, long seedSalt) {
    public static final int VERSION = 2;
    public static final int MIN_SCALE = 32;
    public static final int MAX_SCALE = 2048;
    public static final int DEFAULT_SCALE = 192;

    public static final Codec<AdvancedTerrain> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            StringRepresentable.fromEnum(Landform::values).fieldOf("landform").forGetter(AdvancedTerrain::landform),
            Codec.intRange(MIN_SCALE, MAX_SCALE).optionalFieldOf("scale", DEFAULT_SCALE).forGetter(AdvancedTerrain::scale),
            Codec.LONG.optionalFieldOf("seed_salt", 0L).forGetter(AdvancedTerrain::seedSalt)
    ).apply(builder, AdvancedTerrain::new));

    public enum Landform implements StringRepresentable {
        MOUNTAINS, VALLEYS, PLATEAUS, CANYONS, BASINS, CRATERS, CONTINENTS, ARCHIPELAGOS, FLOATING_ISLANDS;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}

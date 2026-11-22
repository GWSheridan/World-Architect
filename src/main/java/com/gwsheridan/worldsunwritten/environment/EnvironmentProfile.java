package com.gwsheridan.worldsunwritten.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;

/** World-local overrides. Vanilla values leave the underlying dimension and clocks untouched. */
public record EnvironmentProfile(Time time, Weather weather, boolean monsters, boolean creatures, boolean spawners,
        Portal portal, Visual visual) {
    public static final EnvironmentProfile DEFAULT = new EnvironmentProfile(Time.DEFAULT, Weather.VANILLA, true, true, true, Portal.ALL, Visual.DEFAULT);
    public static final Codec<EnvironmentProfile> CODEC = RecordCodecBuilder.create(b -> b.group(
            Time.CODEC.optionalFieldOf("time", Time.DEFAULT).forGetter(EnvironmentProfile::time),
            StringRepresentable.fromEnum(Weather::values).optionalFieldOf("weather", Weather.VANILLA).forGetter(EnvironmentProfile::weather),
            Codec.BOOL.optionalFieldOf("monsters", true).forGetter(EnvironmentProfile::monsters),
            Codec.BOOL.optionalFieldOf("creatures", true).forGetter(EnvironmentProfile::creatures),
            Codec.BOOL.optionalFieldOf("spawners", true).forGetter(EnvironmentProfile::spawners),
            StringRepresentable.fromEnum(Portal::values).optionalFieldOf("portal", Portal.ALL).forGetter(EnvironmentProfile::portal),
            Visual.CODEC.optionalFieldOf("visual", Visual.DEFAULT).forGetter(EnvironmentProfile::visual)
    ).apply(b, EnvironmentProfile::new));

    public boolean allowsSpawn(MobCategory category, EntitySpawnReason reason) {
        if (EntitySpawnReason.isSpawner(reason)) return spawners;
        return switch (reason) {
            case NATURAL, CHUNK_GENERATION, STRUCTURE, PATROL, EVENT, REINFORCEMENT, JOCKEY -> category == MobCategory.MONSTER ? monsters : creatures;
            default -> true;
        };
    }

    public enum TimeMode implements StringRepresentable {
        VANILLA, FIXED, OFFSET;
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Weather implements StringRepresentable {
        VANILLA, CLEAR, RAIN, THUNDER;
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Portal implements StringRepresentable {
        ALL, PLAYERS, OPERATORS, NONE;
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Clouds implements StringRepresentable {
        VANILLA, HIDDEN, SHOWN;
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public record Time(TimeMode mode, int ticks) {
        public static final Time DEFAULT = new Time(TimeMode.VANILLA, 0);
        public static final Codec<Time> CODEC = RecordCodecBuilder.create(b -> b.group(
                StringRepresentable.fromEnum(TimeMode::values).fieldOf("mode").forGetter(Time::mode),
                Codec.intRange(0, 23999).optionalFieldOf("ticks", 0).forGetter(Time::ticks)
        ).apply(b, Time::new));

        public long apply(long original) {
            return switch (mode) {
                case VANILLA -> original;
                case FIXED -> ticks;
                case OFFSET -> original + ticks;
            };
        }
    }
    public record Fog(float start, float end) {
        public static final Codec<Fog> CODEC = RecordCodecBuilder.<Fog>create(b -> b.group(
                Codec.floatRange(0, 4096).fieldOf("start").forGetter(Fog::start),
                Codec.floatRange(0.01F, 4096).fieldOf("end").forGetter(Fog::end)
        ).apply(b, Fog::new)).validate(f -> Float.isFinite(f.start) && Float.isFinite(f.end) && f.end > f.start
                ? DataResult.success(f) : DataResult.error(() -> "Fog end must exceed its start, with finite distances up to 4096."));
    }
    public record Visual(Optional<Integer> fogColor, Optional<Fog> fog, Optional<Integer> skyColor, Clouds clouds,
            Optional<Integer> cloudColor, Optional<Float> cloudHeight) {
        private static final Codec<Integer> COLOR = Codec.intRange(0, 0xffffff);
        public static final Visual DEFAULT = new Visual(Optional.empty(), Optional.empty(), Optional.empty(), Clouds.VANILLA, Optional.empty(), Optional.empty());
        public static final Codec<Visual> CODEC = RecordCodecBuilder.<Visual>create(b -> b.group(
                COLOR.optionalFieldOf("fog_color").forGetter(Visual::fogColor),
                Fog.CODEC.optionalFieldOf("fog").forGetter(Visual::fog),
                COLOR.optionalFieldOf("sky_color").forGetter(Visual::skyColor),
                StringRepresentable.fromEnum(Clouds::values).optionalFieldOf("clouds", Clouds.VANILLA).forGetter(Visual::clouds),
                COLOR.optionalFieldOf("cloud_color").forGetter(Visual::cloudColor),
                Codec.floatRange(-2032, 4096).optionalFieldOf("cloud_height").forGetter(Visual::cloudHeight)
        ).apply(b, Visual::new)).validate(v -> v.cloudHeight.filter(h -> !Float.isFinite(h)).isPresent()
                ? DataResult.error(() -> "Cloud height must be finite.") : DataResult.success(v));
    }
}

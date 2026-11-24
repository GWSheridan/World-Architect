package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.config.EndProgression;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

public final class EndArenaBiomeSource extends BiomeSource {
    protected final boolean legacyEncoding =
            com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing();

    public final boolean legacyEncoding() {
        return legacyEncoding;
    }

    public static final MapCodec<EndArenaBiomeSource> CODEC =
            RecordCodecBuilder.mapCodec(
                    builder ->
                            builder.group(
                                            BiomeSource.CODEC
                                                    .fieldOf("outer")
                                                    .forGetter(value -> value.outer),
                                            Biome.CODEC
                                                    .fieldOf("arena")
                                                    .forGetter(value -> value.arena))
                                    .apply(builder, EndArenaBiomeSource::new));
    private final BiomeSource outer;
    private final Holder<Biome> arena;

    public EndArenaBiomeSource(BiomeSource outer, Holder<Biome> arena) {
        this.outer = outer;
        this.arena = arena;
    }

    @Override
    protected MapCodec<EndArenaBiomeSource> codec() {
        return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.serialized(
                CODEC, legacyEncoding);
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(outer.possibleBiomes().stream(), Stream.of(arena)).distinct();
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        var resolver = outer.createResolver(sampler);
        return (x, y, z) -> nativeZone(x * 4, z * 4) ? arena : resolver.getNoiseBiome(x, y, z);
    }

    private boolean nativeZone(int x, int z) {
        return EndProgression.contains(x, z)
                || outer instanceof CellBiomeSource cells
                        && cells.campaign()
                        && com.gwsheridan.worldsunwritten.cells.CellEndReservations.landing(x, z);
    }

    public Holder<Biome> sample(int x, int y, int z) {
        if (nativeZone(x * 4, z * 4)) return arena;
        return outer instanceof ZoneBiomeSource zones
                ? zones.sample(x, y, z)
                : outer instanceof RegionBiomeSource regions
                        ? regions.getNoiseBiome(x, y, z)
                        : outer instanceof CellBiomeSource cells
                                ? cells.sample(x * 4, z * 4)
                                : ((net.minecraft.world.level.biome.FixedBiomeSource) outer)
                                        .getNoiseBiome(x, y, z);
    }
}

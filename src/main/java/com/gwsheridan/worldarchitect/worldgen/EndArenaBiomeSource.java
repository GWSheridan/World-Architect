package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.config.EndProgression;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

public final class EndArenaBiomeSource extends BiomeSource {
    public static final MapCodec<EndArenaBiomeSource> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            BiomeSource.CODEC.fieldOf("outer").forGetter(value -> value.outer),
            Biome.CODEC.fieldOf("arena").forGetter(value -> value.arena)
    ).apply(builder, EndArenaBiomeSource::new));
    private final BiomeSource outer;
    private final Holder<Biome> arena;
    public EndArenaBiomeSource(BiomeSource outer, Holder<Biome> arena) { this.outer = outer; this.arena = arena; }
    @Override protected MapCodec<EndArenaBiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(outer.possibleBiomes().stream(), Stream.of(arena)).distinct();
    }
    @Override public BiomeResolver createResolver(Climate.Sampler sampler) {
        var resolver = outer.createResolver(sampler);
        return (x, y, z) -> EndProgression.contains(x * 4, z * 4) ? arena : resolver.getNoiseBiome(x, y, z);
    }
    public Holder<Biome> sample(int x, int y, int z) {
        if (EndProgression.contains(x * 4, z * 4)) return arena;
        return outer instanceof RegionBiomeSource regions ? regions.getNoiseBiome(x, y, z)
                : ((net.minecraft.world.level.biome.FixedBiomeSource) outer).getNoiseBiome(x, y, z);
    }
}

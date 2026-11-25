package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.config.RegionalTerrain;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;

public final class ZoneBiomeSource extends BiomeSource {
    protected final boolean legacyEncoding = com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing();
    public final boolean legacyEncoding() { return legacyEncoding; }
    public static final MapCodec<ZoneBiomeSource> CODEC=RecordCodecBuilder.mapCodec(b->b.group(
            BiomeSource.CODEC.fieldOf("outer").forGetter(s->s.outer), RegionalTerrain.CODEC.fieldOf("regions").forGetter(s->s.regions)
    ).apply(b,ZoneBiomeSource::new));
    private final BiomeSource outer; private final RegionalTerrain regions;
    public ZoneBiomeSource(BiomeSource outer,RegionalTerrain regions){this.outer=outer;this.regions=regions;}
    @Override protected MapCodec<ZoneBiomeSource> codec(){return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.serialized(CODEC, legacyEncoding);}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){return Stream.concat(outer.possibleBiomes().stream(),regions.zones().stream().flatMap(z->z.biome().stream())).distinct();}
    @Override public BiomeResolver createResolver(Climate.Sampler sampler){var base=outer.createResolver(sampler);return(x,y,z)->regions.biomeAt(x*4,z*4).orElseGet(()->base.getNoiseBiome(x,y,z));}
    public Holder<Biome> sample(int x,int y,int z){return regions.biomeAt(x*4,z*4).orElseGet(()->outer instanceof RegionBiomeSource r?r.getNoiseBiome(x,y,z):((FixedBiomeSource)outer).getNoiseBiome(x,y,z));}
}

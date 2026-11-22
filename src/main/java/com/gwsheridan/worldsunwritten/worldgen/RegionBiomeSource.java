package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.config.ContentProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;

public final class RegionBiomeSource extends BiomeSource {
    protected final boolean legacyEncoding = com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing();
    public final boolean legacyEncoding() { return legacyEncoding; }
    public static final MapCodec<RegionBiomeSource> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Biome.CODEC.listOf(2, ContentProfile.MAX_RULES).fieldOf("biomes").forGetter(source -> source.biomes),
            Codec.intRange(16, 1024).fieldOf("scale").forGetter(source -> source.scale),
            Codec.LONG.fieldOf("seed").forGetter(source -> source.seed)
    ).apply(builder, RegionBiomeSource::new));

    private final List<Holder<Biome>> biomes;
    private final int scale;
    private final long seed;

    public RegionBiomeSource(List<Holder<Biome>> biomes, int scale, long seed) {
        this.biomes = List.copyOf(biomes);
        this.scale = scale;
        this.seed = seed;
    }

    static BiomeSource forContent(Holder<Biome> fallback, ContentProfile content) {
        return switch (content.biomes().size()) {
            case 0 -> new FixedBiomeSource(fallback);
            case 1 -> new FixedBiomeSource(content.biomes().getFirst());
            default -> new RegionBiomeSource(content.biomes(), content.biomeScale(), content.biomeSeed());
        };
    }

    @Override
    protected MapCodec<RegionBiomeSource> codec() {
        return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.serialized(CODEC, legacyEncoding);
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return this::getNoiseBiome;
    }

    Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return resolve(quartX * 4, quartZ * 4);
    }

    private Holder<Biome> resolve(int x, int z) {
        int column = Math.floorDiv(x, scale);
        int row = Math.floorDiv(z, scale);
        long bestDistance = Long.MAX_VALUE;
        long closest = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cellX = column + dx;
                int cellZ = row + dz;
                long hash = ContentRandom.seed(seed, cellX, cellZ, 0);
                long deltaX = (long) cellX * scale + scale / 4 + Math.floorMod(hash, scale / 2) - x;
                long deltaZ = (long) cellZ * scale + scale / 4 + Math.floorMod(hash >>> 32, scale / 2) - z;
                long distance = deltaX * deltaX + deltaZ * deltaZ;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    closest = hash;
                }
            }
        }
        return biomes.get(Math.floorMod(closest >>> 16, biomes.size()));
    }
}

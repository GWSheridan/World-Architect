package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.cells.CellWorldProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;

import java.util.stream.Stream;

/** Biomes are selected before unlocking; opening terrain never changes a saved biome palette. */
public final class CellBiomeSource extends BiomeSource {
    protected final boolean legacyEncoding =
            com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing();

    public final boolean legacyEncoding() {
        return legacyEncoding;
    }

    public static final MapCodec<CellBiomeSource> CODEC =
            RecordCodecBuilder.mapCodec(
                    b ->
                            b.group(
                                            CellWorldProfile.CODEC
                                                    .fieldOf("cells")
                                                    .forGetter(s -> s.profile))
                                    .apply(b, CellBiomeSource::new));
    private final CellWorldProfile profile;
    private final java.util.concurrent.ConcurrentMap<CellGenerator.Key, Holder<Biome>> selected =
            new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long seed;

    public CellBiomeSource(CellWorldProfile profile) {
        this.profile = profile;
    }

    public boolean campaign() {
        return profile.version() == 2
                && profile.progressive()
                && profile.growth().scope()
                        == com.gwsheridan.worldsunwritten.cells.GrowthRules.Scope.SHARED;
    }

    public void seed(long value) {
        if (seed != value) selected.clear();
        seed = value;
    }

    @Override
    protected MapCodec<CellBiomeSource> codec() {
        return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.serialized(
                CODEC, legacyEncoding);
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return profile.tiles().stream().map(CellWorldProfile.Tile::biome).distinct();
    }

    public Holder<Biome> sample(int x, int z) {
        var address = profile.address(x, z);
        if (address == null) return profile.tiles().getFirst().biome();
        if (selected.size() > 4096) selected.clear();
        return selected.computeIfAbsent(
                new CellGenerator.Key(address.territory(), address.cell()),
                key -> profile.tile(seed, key.territory(), key.cell()).biome());
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (x, y, z) -> sample(x * 4, z * 4);
    }
}

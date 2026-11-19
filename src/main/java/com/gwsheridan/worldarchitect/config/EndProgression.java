package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Version 1 reserves a fixed, chunk-aligned native End arena. */
public final class EndProgression {
    public static final int MIN = -256;
    public static final int MAX = 255;
    public static final Codec<EndProgression> CODEC = Codec.lazyInitialized(EndProgression::codec);

    private static Codec<EndProgression> codec() { return RecordCodecBuilder.create(builder -> builder.group(
            Codec.intRange(1, 1).fieldOf("version").forGetter(value -> 1),
            Codec.STRING.validate(value -> value.equals("vanilla_arena")
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "Unknown End progression mode: " + value))
                    .fieldOf("mode").forGetter(value -> "vanilla_arena"),
            RegistryOps.retrieveGetter(Registries.NOISE),
            RegistryOps.retrieveElement(Biomes.THE_END),
            RegistryOps.retrieveElement(NoiseGeneratorSettings.END)
    ).apply(builder, (version, mode, noises, biome, settings) -> new EndProgression(noises, biome, settings))); }

    private final HolderGetter<NormalNoise> noises;
    private final Holder<NoiseGeneratorSettings> settings;
    private final Holder<Biome> biome;
    private final NoiseBasedChunkGenerator generator;
    private final LinkedHashMap<Long, RandomState> states = new LinkedHashMap<>(4, 0.75F, true);

    private EndProgression(HolderGetter<NormalNoise> noises, Holder<Biome> biome, Holder<NoiseGeneratorSettings> settings) {
        this.noises = noises;
        this.settings = settings;
        this.biome = biome;
        this.generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), settings);
    }

    public static EndProgression vanillaArena(HolderLookup.Provider registries) {
        return new EndProgression(registries.lookupOrThrow(Registries.NOISE),
                registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_END),
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.END));
    }

    public NoiseBasedChunkGenerator generator() { return generator; }
    public Holder<Biome> biome() { return biome; }

    public synchronized RandomState state(long seed) {
        RandomState state = states.get(seed);
        if (state == null) {
            state = RandomState.create(noises, seed, settings.value());
            if (states.size() == 4) states.remove(states.keySet().iterator().next());
            states.put(seed, state);
        }
        return state;
    }

    public static boolean contains(int x, int z) {
        return x >= MIN && x <= MAX && z >= MIN && z <= MAX;
    }

    public static boolean intersects(int minX, int minZ, int maxX, int maxZ) {
        return maxX >= MIN && minX <= MAX && maxZ >= MIN && minZ <= MAX;
    }

    public static boolean allowsGenerationWrite(int originX, int originZ, int targetX, int targetZ) {
        return contains(originX, originZ) == contains(targetX, targetZ);
    }
}

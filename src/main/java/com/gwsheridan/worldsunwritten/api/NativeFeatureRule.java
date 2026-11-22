package com.gwsheridan.worldsunwritten.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Explicit native feature calls. Height/biome conditions apply to the origin, not every native write. */
public record NativeFeatureRule(Holder<Feature> feature, int attempts, int minY, int maxY, boolean surface, List<Holder<Biome>> biomes) {
    public static final int MAX_RULES = 16;
    public static final int MAX_WORK = 65536;
    public static final Codec<NativeFeatureRule> CODEC = RecordCodecBuilder.<NativeFeatureRule>create(b -> b.group(
            Feature.CODEC.fieldOf("feature").forGetter(NativeFeatureRule::feature),
            Codec.intRange(1, 8).fieldOf("attempts").forGetter(NativeFeatureRule::attempts),
            Codec.intRange(-2032, 2031).fieldOf("min_y").forGetter(NativeFeatureRule::minY),
            Codec.intRange(-2032, 2031).fieldOf("max_y").forGetter(NativeFeatureRule::maxY),
            Codec.BOOL.optionalFieldOf("surface", false).forGetter(NativeFeatureRule::surface),
            Biome.CODEC.listOf(0, 16).optionalFieldOf("biomes", List.of()).forGetter(NativeFeatureRule::biomes)
    ).apply(b, NativeFeatureRule::new)).validate(rule -> rule.feature.unwrapKey().isPresent() && rule.minY <= rule.maxY
            && rule.biomes.stream().allMatch(biome -> biome.unwrapKey().isPresent())
            ? DataResult.success(rule) : DataResult.error(() -> "Native feature rules require registered features/biomes and an ordered height range."));
    public NativeFeatureRule { biomes = List.copyOf(biomes); }
}

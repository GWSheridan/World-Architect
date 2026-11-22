package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.api.FeatureAdapters;
import com.gwsheridan.worldsunwritten.api.NativeFeatureRule;
import com.gwsheridan.worldsunwritten.api.StructureAdapters;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Dispatch only explicitly selected native extensions; legacy profiles take the original path. */
public final class GenerationExtensions {
    private GenerationExtensions() {}
    public static Optional<String> problem(SolidColumnGenerator generator) {
        long work = 0;
        for (var rule : generator.content().nativeFeatures()) {
            if (!rule.feature().isBound()) return Optional.of("A selected native feature is not registered.");
            var adapter = FeatureAdapters.get(rule.feature().value());
            if (adapter.isEmpty() || adapter.orElseThrow().version() < 1) return Optional.of("Native feature " + rule.feature().unwrapKey().orElseThrow().identifier() + " needs a compatible feature adapter.");
            int limit = adapter.orElseThrow().workLimit(rule.feature().value());
            if (limit < 1 || limit > NativeFeatureRule.MAX_WORK) return Optional.of("The feature adapter rejected the configuration or its work limit.");
            work += (long) limit * rule.attempts();
            if (rule.minY() < generator.getMinY() || rule.maxY() >= generator.getMinY() + generator.getGenDepth()) return Optional.of("Native feature origins must fit inside the dimension.");
            if (!generator.getBiomeSource().possibleBiomes().containsAll(rule.biomes())) return Optional.of("Native feature biomes must be enabled in the dimension.");
        }
        return work > NativeFeatureRule.MAX_WORK ? Optional.of("Native features exceed the 65,536-unit per-chunk adapter work budget.") : Optional.empty();
    }
    static void place(SolidColumnGenerator generator, WorldGenLevel level, ChunkAccess chunk) {
        int index = 0;
        for (var rule : generator.content().nativeFeatures()) {
            long salt = 10000L + rule.feature().unwrapKey().orElseThrow().identifier().toString().hashCode() + index++;
            var random = RandomSource.create(ContentRandom.seed(level.getSeed(), chunk.getPos().x(), chunk.getPos().z(), salt));
            for (int attempt = 0; attempt < rule.attempts(); attempt++) {
                int x = chunk.getPos().getMinBlockX() + random.nextInt(16), z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
                int y = rule.surface() ? generator.surfaceY(x, z, chunk, level.getSeed()) + 1 : rule.minY() + random.nextInt(rule.maxY() - rule.minY() + 1);
                var pos = new BlockPos(x, y, z);
                if (y >= rule.minY() && y <= rule.maxY() && (rule.biomes().isEmpty() || rule.biomes().contains(level.getBiome(pos)))) {
                    rule.feature().value().place(level, generator, random, pos);
                }
            }
        }
    }
    public static boolean needsResourcePins(SolidColumnGenerator generator) {
        return !generator.content().nativeFeatures().isEmpty() || generator.structureRules().stream().anyMatch(rule -> StructureAdapters.get(rule.structure().value()).isPresent());
    }
    public static CompoundTag pins(WorldDimensions dimensions, HolderLookup.Provider registries) {
        var result = new CompoundTag(); var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        dimensions.dimensions().forEach((dimension, stem) -> {
            if (!(stem.generator() instanceof SolidColumnGenerator generator)) return;
            var selected = new CompoundTag();
            for (var rule : generator.content().nativeFeatures()) {
                var feature = rule.feature().value(); var adapter = FeatureAdapters.get(feature).orElseThrow();
                var pin = new CompoundTag();pin.putString("type", FeatureAdapters.type(feature).toString());pin.putInt("version", adapter.version());pin.putInt("work", adapter.workLimit(feature));
                pin.put("definition", Feature.DIRECT_CODEC.encodeStart(ops, feature).getOrThrow());
                selected.put("feature/" + rule.feature().unwrapKey().orElseThrow().identifier(), pin);
            }
            generator.structureRules().forEach(rule -> StructureAdapters.get(rule.structure().value()).ifPresent(adapter -> {
                var pin = new CompoundTag();pin.putString("type", StructureAdapters.type(rule.structure().value()).toString());pin.putInt("version", adapter.version());
                selected.put("structure/" + rule.structure().unwrapKey().orElseThrow().identifier(), pin);
            }));
            if (!selected.isEmpty()) result.put(dimension.identifier().toString(), selected);
        });
        return result;
    }
}

package com.gwsheridan.worldsunwritten.dimension;

import com.gwsheridan.worldsunwritten.api.DimensionAdapter;
import com.gwsheridan.worldsunwritten.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry;
import com.gwsheridan.worldsunwritten.config.DimensionProfile;
import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.slf4j.Logger;

public final class DimensionCatalog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<TerrainMode> STANDARD_MODES = Set.of(TerrainMode.values());

    private DimensionCatalog() {}

    public static List<ResourceKey<LevelStem>> discover(Map<ResourceKey<LevelStem>, LevelStem> selected, Registry<LevelStem> datapackDimensions) {
        Map<ResourceKey<LevelStem>, LevelStem> entries = new LinkedHashMap<>(selected);
        datapackDimensions.listElements().forEach(element -> entries.putIfAbsent(element.key(), element.value()));
        return WorldDimensions.keysInOrder(entries.keySet()).toList();
    }

    public static LevelStem effective(ResourceKey<LevelStem> key, Map<ResourceKey<LevelStem>, LevelStem> selected,
            Registry<LevelStem> datapackDimensions) {
        LevelStem edited = selected.get(key);
        if (isExplicitProfile(edited)) {
            return edited;
        }
        return datapackDimensions.getOptional(key).orElse(edited);
    }

    public static boolean isExplicitProfile(LevelStem stem) {
        return stem != null && stem.generator() instanceof ManagedGenerator generator && generator.dimensionProfile().isPresent();
    }

    public static Replacement inspect(RegistryAccess registries, ResourceKey<LevelStem> key, LevelStem original) {
        Optional<DimensionAdapter> registered = DimensionAdapterRegistry.get(key.identifier());
        if (registered.isPresent()) {
            DimensionAdapter adapter = registered.orElseThrow();
            DimensionAdapter.Support support;
            try {
                support = adapter.inspect(key, original);
            } catch (RuntimeException exception) {
                LOGGER.warn("Dimension adapter {} failed to inspect its source", key.identifier(), exception);
                return unavailable("worldsunwritten.dimensions.adapter_invalid");
            }
            var modes=support.modes().stream().filter(mode->mode!=TerrainMode.CELLULAR||adapter.supportsCellularTerrain()).collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (modes.isEmpty()) {
                return new Replacement(Set.of(), Optional.empty(), Optional.empty(), support.detail());
            }
            if (adapter.version() < 1) {
                return unavailable("worldsunwritten.dimensions.adapter_invalid");
            }
            Optional<Holder<Biome>> biome = support.preferredBiome().isPresent()
                    ? registries.lookupOrThrow(Registries.BIOME).get(support.preferredBiome().orElseThrow()).map(holder -> holder)
                    : selectBiome(registries, key, original);
            return biome.isPresent()
                    ? new Replacement(modes, biome, Optional.of(new DimensionProfile.AdapterBinding(key.identifier(), adapter.version())), support.detail())
                    : unavailable("worldsunwritten.dimensions.missing_biome");
        }

        if (!usesStandardGenerator(original)) {
            return unavailable("worldsunwritten.dimensions.adapter_needed");
        }
        return selectBiome(registries, key, original)
                .map(biome -> new Replacement(STANDARD_MODES, Optional.of(biome), Optional.empty(), Component.translatable("worldsunwritten.dimensions.standard")))
                .orElseGet(() -> unavailable("worldsunwritten.dimensions.missing_biome"));
    }

    public static boolean usesStandardGenerator(LevelStem stem) {
        return stem.generator() instanceof ManagedGenerator || stem.generator().getClass() == NoiseBasedChunkGenerator.class;
    }

    private static Optional<Holder<Biome>> selectBiome(RegistryAccess registries, ResourceKey<LevelStem> key, LevelStem stem) {
        ResourceKey<Biome> preferred = key.equals(LevelStem.OVERWORLD) ? Biomes.PLAINS
                : key.equals(LevelStem.NETHER) ? Biomes.NETHER_WASTES : key.equals(LevelStem.END) ? Biomes.THE_END : null;
        if (preferred != null) {
            var holder = registries.lookupOrThrow(Registries.BIOME).get(preferred);
            if (holder.isPresent()) {
                return Optional.of(holder.orElseThrow());
            }
        }
        return stem.generator().getBiomeSource().possibleBiomes().stream()
                .filter(biome -> biome.unwrapKey().isPresent())
                .min(java.util.Comparator.comparing(biome -> biome.unwrapKey().orElseThrow().identifier()));
    }

    private static Replacement unavailable(String translationKey) {
        return new Replacement(Set.of(), Optional.empty(), Optional.empty(), Component.translatable(translationKey));
    }

    public record Replacement(Set<TerrainMode> modes, Optional<Holder<Biome>> biome,
            Optional<DimensionProfile.AdapterBinding> adapter, Component detail) {}
}

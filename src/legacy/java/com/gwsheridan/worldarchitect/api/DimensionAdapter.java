package com.gwsheridan.worldarchitect.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;

/** Register an adapter under the exact registry ID of the dimension it supports. */
public interface DimensionAdapter {
    /** Change this number when an earlier compatibility contract can no longer be honored. */
    int version();

    /** Opt in only when every custom portal entry calls EnvironmentApi.canEnter before teleporting. */
    default boolean supportsPortalPolicies() { return false; }
    /** Explicit opt-in: an older adapter using EnumSet.allOf must not silently claim a new generator. */
    default boolean supportsCellularTerrain() { return false; }

    /** Inspect the original stem. Implementations must be side-effect free. */
    Support inspect(ResourceKey<LevelStem> dimension, LevelStem original);

    enum TerrainMode {
        SINGLE_BLOCK,
        LAYERED,
        VOID,
        SKYBLOCK,
        CELLULAR
    }

    /** An empty mode set keeps the dimension's original generator available. */
    record Support(Set<TerrainMode> modes, Optional<ResourceKey<Biome>> preferredBiome, Component detail) {
        public Support {
            modes = Set.copyOf(modes);
            Objects.requireNonNull(preferredBiome);
            Objects.requireNonNull(detail);
        }
    }
}

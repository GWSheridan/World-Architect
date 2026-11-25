package com.gwsheridan.worldsunwritten.api;

import net.minecraft.world.level.levelgen.feature.Feature;

/** Register under the exact FEATURE_TYPE codec ID. Implementations must be pure and thread safe. */
public interface FeatureAdapter {
    /** Increase when the feature algorithm/configuration contract changes incompatibly. */
    int version();
    /** Conservative work units per call, 1..65536; zero rejects the configuration. */
    int workLimit(Feature feature);
}

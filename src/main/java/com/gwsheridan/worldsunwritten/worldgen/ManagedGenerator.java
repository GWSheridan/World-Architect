package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldsunwritten.config.DimensionProfile;
import java.util.Optional;

public interface ManagedGenerator {
    Optional<DimensionProfile> dimensionProfile();

    default Optional<com.gwsheridan.worldsunwritten.config.EndProgression> endProgression() {
        return dimensionProfile().flatMap(DimensionProfile::endProgression);
    }

    TerrainMode terrainMode();

    Optional<String> profileProblem();
}

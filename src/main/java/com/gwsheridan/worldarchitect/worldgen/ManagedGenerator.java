package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldarchitect.config.DimensionProfile;
import java.util.Optional;

public interface ManagedGenerator {
    Optional<DimensionProfile> dimensionProfile();

    default Optional<com.gwsheridan.worldarchitect.config.EndProgression> endProgression() {
        return dimensionProfile().flatMap(DimensionProfile::endProgression);
    }

    TerrainMode terrainMode();

    Optional<String> profileProblem();
}

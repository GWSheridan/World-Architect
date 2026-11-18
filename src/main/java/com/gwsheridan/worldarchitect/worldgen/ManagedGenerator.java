package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldarchitect.config.DimensionProfile;
import java.util.Optional;

public interface ManagedGenerator {
    Optional<DimensionProfile> dimensionProfile();

    TerrainMode terrainMode();

    Optional<String> profileProblem();
}

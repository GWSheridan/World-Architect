package com.gwsheridan.worldsunwritten.worldgen.terrain;

import com.gwsheridan.worldsunwritten.config.TerrainShape;
import net.minecraft.world.level.LevelHeightAccessor;

/** Corridor profiles use the versioned signed-distance blend; older samplers stay frozen. */
public final class HeightfieldV6 {
    private HeightfieldV6() {}
    public static HeightfieldV2.Column sample(TerrainShape shape,long seed,int x,int z,LevelHeightAccessor height) {
        return HeightfieldV5.sample(shape,seed,x,z,height);
    }
}

package com.gwsheridan.worldarchitect.worldgen.terrain;

import com.gwsheridan.worldarchitect.config.*;
import net.minecraft.world.level.LevelHeightAccessor;

/** Version 3 blends absolute samples, not previously generated neighbouring chunks. */
public final class HeightfieldV3 {
    private HeightfieldV3() {}
    public static HeightfieldV2.Column sample(TerrainShape shape,long seed,int x,int z,LevelHeightAccessor height) {
        double top=HeightfieldV2.sample(shape,shape.advanced().orElseThrow(),seed,x,z,height).top();
        for(var zone:shape.regions().orElseThrow().ordered().reversed()) {
            double weight=zone.weight(x,z);if(weight==0)continue;
            var targetShape=new TerrainShape(zone.elevation(),zone.relief(),java.util.Optional.of(zone.terrain()));
            int target=HeightfieldV2.sample(targetShape,zone.terrain(),seed,x,z,height).top();top+=weight*(target-top);
        }
        return new HeightfieldV2.Column(height.getMinY(),Math.clamp((int)Math.round(top),height.getMinY(),height.getMaxY()-3));
    }
}

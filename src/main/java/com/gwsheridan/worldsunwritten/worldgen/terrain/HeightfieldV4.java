package com.gwsheridan.worldsunwritten.worldgen.terrain;

import com.gwsheridan.worldsunwritten.config.*;
import net.minecraft.world.level.LevelHeightAccessor;

/** Polygon profiles explicitly opt into version 4; older profiles retain version 3. */
public final class HeightfieldV4 {
    private HeightfieldV4() {}
    public static HeightfieldV2.Column sample(TerrainShape shape,long seed,int x,int z,LevelHeightAccessor height) {
        double top=HeightfieldV2.sample(shape,shape.advanced().orElseThrow(),seed,x,z,height).top();
        for(var zone:shape.regions().orElseThrow().ordered().reversed()) {
            double weight=zone.weight(x,z);if(weight==0)continue;
            var target=new TerrainShape(zone.elevation(),zone.relief(),java.util.Optional.of(zone.terrain()));
            top+=weight*(HeightfieldV2.sample(target,zone.terrain(),seed,x,z,height).top()-top);
        }
        return new HeightfieldV2.Column(height.getMinY(),Math.clamp((int)Math.round(top),height.getMinY(),height.getMaxY()-3));
    }
}

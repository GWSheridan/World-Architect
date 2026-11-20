package com.gwsheridan.worldarchitect.worldgen.terrain;

import com.gwsheridan.worldarchitect.config.*;
import net.minecraft.world.level.LevelHeightAccessor;

/** Composite masks opt into their own signed-distance blend. Versions 1–4 stay unchanged. */
public final class HeightfieldV5 {
    private HeightfieldV5(){}
    public static HeightfieldV2.Column sample(TerrainShape shape,long seed,int x,int z,LevelHeightAccessor height){
        double top=HeightfieldV2.sample(shape,shape.advanced().orElseThrow(),seed,x,z,height).top();var regions=shape.regions().orElseThrow();var distances=regions.distances(x,z);
        for(var zone:regions.ordered().reversed()){double weight=RegionalTerrain.weight(zone,distances.get(zone.name()));if(weight==0)continue;var target=new TerrainShape(zone.elevation(),zone.relief(),java.util.Optional.of(zone.terrain()));top+=weight*(HeightfieldV2.sample(target,zone.terrain(),seed,x,z,height).top()-top);}
        return new HeightfieldV2.Column(height.getMinY(),Math.clamp((int)Math.round(top),height.getMinY(),height.getMaxY()-3));
    }
}

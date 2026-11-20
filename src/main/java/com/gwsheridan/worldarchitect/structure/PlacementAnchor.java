package com.gwsheridan.worldarchitect.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import com.gwsheridan.worldarchitect.config.TerrainShape;

/** Explicit 1.5 placement settings. Omission retains the original ocean-floor anchor. */
public record PlacementAnchor(Mode mode,int fixedY) {
    public static final int MIN_Y=TerrainShape.MIN_ELEVATION, MAX_Y=TerrainShape.MAX_ELEVATION+3;
    public enum Mode implements StringRepresentable {
        SURFACE, SEABED, FIXED;
        @Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}
    }
    public static final Codec<PlacementAnchor> CODEC=RecordCodecBuilder.<PlacementAnchor>create(b->b.group(
            com.gwsheridan.worldarchitect.config.ExactInt.range(1,1).fieldOf("version").forGetter(a->1),
            StringRepresentable.fromEnum(Mode::values).fieldOf("mode").forGetter(PlacementAnchor::mode),
            com.gwsheridan.worldarchitect.config.ExactInt.range(MIN_Y,MAX_Y).optionalFieldOf("fixed_y",0).forGetter(PlacementAnchor::fixedY)
    ).apply(b,(version,mode,y)->new PlacementAnchor(mode,y))).validate(PlacementAnchor::validate);
    public DataResult<PlacementAnchor> validate(){return fixedY<MIN_Y||fixedY>MAX_Y||mode!=Mode.FIXED&&fixedY!=0
            ?DataResult.error(()->"Only fixed anchors accept fixed_y, within the dimension height limits."):DataResult.success(this);}
    public int resolve(int surface,int seabed,int offset){return Math.addExact(switch(mode){case SURFACE->surface;case SEABED->seabed;case FIXED->fixedY;},offset);}
}

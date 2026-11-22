package com.gwsheridan.worldsunwritten.api;

import net.minecraft.world.level.levelgen.structure.Structure;

/** Opt a native structure into managed solid terrain, using its own native lifecycle and codecs. */
public interface StructureAdapter {
    int version();
    /** Pure inspection of this configuration. Native placement must be deterministic and bounded. */
    boolean supports(Structure structure);
}

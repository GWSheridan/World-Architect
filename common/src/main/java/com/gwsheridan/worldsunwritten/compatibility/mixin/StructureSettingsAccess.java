package com.gwsheridan.worldsunwritten.compatibility.mixin;

import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Structure.class)
public interface StructureSettingsAccess {
    @Accessor("settings")
    Structure.StructureSettings worldsunwritten$settings();
}

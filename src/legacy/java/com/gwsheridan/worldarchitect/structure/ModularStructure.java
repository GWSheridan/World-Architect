package com.gwsheridan.worldarchitect.structure;
import com.mojang.serialization.MapCodec;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs;
public final class ModularStructure extends com.gwsheridan.worldsunwritten.structure.ModularStructure {
 public static final MapCodec<ModularStructure> CODEC=LegacyCodecs.alias(com.gwsheridan.worldsunwritten.structure.ModularStructure.CODEC).xmap(ModularStructure::new,s->s);
 public ModularStructure(com.gwsheridan.worldsunwritten.structure.ModularStructure source){super(source);}
}

package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.config.EndProgression;
import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class EndArenaStructuresMixin {
    @Inject(method = "createStructures", at = @At("RETURN"))
    private void worldsunwritten$excludeArena(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager manager,
            ChunkAccess chunk, StructureTemplateManager templates, ResourceKey<Level> dimension, CallbackInfo callback) {
        if ((Object) this instanceof ManagedGenerator managed && managed.endProgression().isPresent()) {
            for (var entry : java.util.List.copyOf(chunk.getAllStarts().entrySet())) {
                var start = entry.getValue();
                if (!start.isValid()) continue;
                var box = start.getBoundingBox();
                if (EndProgression.intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                    chunk.setStartForStructure(entry.getKey(), StructureStart.INVALID_START);
                }
            }
        }
    }
}

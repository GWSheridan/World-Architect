package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.skyblock.IslandSpawns;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PrepareSpawnTask.class)
abstract class PrepareSpawnTaskMixin {
    @Shadow @Final private NameAndId nameAndId;

    // Vanilla only invokes this branch when there is no saved player position.
    @WrapOperation(method = "start", at = @At(value = "INVOKE", ordinal = 1, target =
            "Ljava/util/Optional;orElseGet(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
    private Object worldarchitect$structureSpawn(Optional<?> saved, Supplier<?> fallback, Operation<Object> original,
            @Local ServerLevel level) {
        if (saved.isEmpty() && level.getChunkSource().getGenerator() instanceof VoidGenerator generator) {
            // Capture join refusals in the future even when the executor runs the work inline.
            return CompletableFuture.supplyAsync(() -> IslandSpawns.initial(level, generator, nameAndId.id()), level.getServer());
        }
        return original.call(saved, fallback);
    }
}

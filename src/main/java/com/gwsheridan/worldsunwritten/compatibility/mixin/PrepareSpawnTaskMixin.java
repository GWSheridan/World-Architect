package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.skyblock.IslandSpawns;
import com.gwsheridan.worldsunwritten.worldgen.VoidGenerator;
import com.gwsheridan.worldsunwritten.spawn.SpawnPolicies;
import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import com.gwsheridan.worldsunwritten.spawn.SafeSpawns;
import com.gwsheridan.worldsunwritten.skyblock.SkyblockJoinException;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrepareSpawnTask.class)
abstract class PrepareSpawnTaskMixin {
    @Shadow @Final private NameAndId nameAndId;
    @Unique private CompletableFuture<SafeSpawns.Target> worldsunwritten$initialTarget;

    @WrapOperation(method = "start", at = @At(value = "INVOKE", ordinal = 0, target =
            "Ljava/util/Optional;orElseGet(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
    private Object worldsunwritten$spawnDimension(Optional<?> saved, Supplier<?> fallback, Operation<Object> original) {
        Object level = original.call(saved, fallback);
        return saved.isEmpty() && level instanceof ServerLevel serverLevel ? SpawnPolicies.initialLevel(serverLevel) : level;
    }

    // Vanilla only invokes this branch when there is no saved player position.
    @WrapOperation(method = "start", at = @At(value = "INVOKE", ordinal = 1, target =
            "Ljava/util/Optional;orElseGet(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
    private Object worldsunwritten$structureSpawn(Optional<?> saved, Supplier<?> fallback, Operation<Object> original,
            @Local ServerLevel level) {
        var policy = SpawnPolicies.initialPolicy(level.getServer());
        if (saved.isEmpty() && policy.isPresent()) {
            worldsunwritten$initialTarget = CompletableFuture.supplyAsync(() -> {
                var target = SpawnPolicies.resolveOrFallback(level.getServer(), policy.orElseThrow(), nameAndId.id(), nameAndId.name());
                if (target.level() != level) {
                    throw new SkyblockJoinException("Worlds Unwritten could not find a safe initial spawn in " + level.dimension().identifier());
                }
                return target;
            }, level.getServer());
            return worldsunwritten$initialTarget.thenApply(SafeSpawns.Target::center);
        }
        if (saved.isEmpty() && level.getChunkSource().getGenerator() instanceof VoidGenerator generator) {
            // Capture join refusals in the future even when the executor runs the work inline.
            return CompletableFuture.supplyAsync(() -> IslandSpawns.initial(level, generator, nameAndId.id(),nameAndId.name()), level.getServer());
        }
        return original.call(saved, fallback);
    }

    @Inject(method = "spawnPlayer", at = @At("RETURN"))
    private void worldsunwritten$initialFacing(CallbackInfoReturnable<ServerPlayer> callback) {
        if (worldsunwritten$initialTarget != null) {
            var target = worldsunwritten$initialTarget.join();
            var pos = target.center();
            callback.getReturnValue().teleportTo(target.level(), pos.x, pos.y, pos.z, java.util.Set.of(), target.yaw(), 0.0F, true);
        }
    }
}

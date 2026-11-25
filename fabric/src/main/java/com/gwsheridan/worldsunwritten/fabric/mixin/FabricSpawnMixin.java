package com.gwsheridan.worldsunwritten.fabric.mixin;

import com.gwsheridan.worldsunwritten.spawn.InitialSpawn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class FabricSpawnMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void worldsunwritten$initializeSpawn(ServerLevel level, ServerLevelData settings,
            boolean bonusChest, boolean debug, LevelLoadListener loading, CallbackInfo callback) {
        if (InitialSpawn.apply(level, settings)) callback.cancel();
    }
}

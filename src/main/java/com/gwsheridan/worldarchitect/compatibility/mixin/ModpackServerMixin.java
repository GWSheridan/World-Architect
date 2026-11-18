package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.compatibility.GenerationDependencies;
import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.gwsheridan.worldarchitect.modpack.WorldBundles;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
abstract class ModpackServerMixin {
    @Inject(method = "reloadResources", at = @At("HEAD"), cancellable = true)
    private void worldarchitect$protectDatapackSelection(Collection<String> enabled, CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        try {
            MinecraftServer server = (MinecraftServer) (Object) this;
            ModpackRuntime.validatePacks(WorldBundles.active(server), enabled, server.getPackRepository());
            GenerationDependencies.verifyReload(server, enabled);
        } catch (IllegalStateException e) {
            callback.setReturnValue(CompletableFuture.failedFuture(e));
        }
    }

    @Inject(method = "setRespawnData", at = @At("HEAD"))
    private void worldarchitect$protectWorldSpawn(LevelData.RespawnData respawn, CallbackInfo callback) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (WorldBundles.active(server).filter(bundle -> !bundle.manifest().canEditDimension(LevelStem.OVERWORLD.identifier())).isPresent()) {
            throw ModpackRuntime.problem("The bundled Overworld spawn is locked");
        }
    }
}

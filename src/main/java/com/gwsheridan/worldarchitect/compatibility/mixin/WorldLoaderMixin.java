package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.compatibility.GenerationDependencies;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.WorldLoader;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WorldLoader.class)
abstract class WorldLoaderMixin {
    @WrapMethod(method = "load")
    private static <D, R> CompletableFuture<R> worldarchitect$provideGenerationResources(WorldLoader.InitConfig configuration,
            WorldLoader.WorldDataSupplier<D> data, WorldLoader.ResultFactory<D, R> result, Executor background, Executor main,
            Operation<CompletableFuture<R>> original) {
        WorldLoader.WorldDataSupplier<D> tracked = context -> GenerationDependencies.duringLoad(context.resources(), () -> data.get(context));
        return original.call(configuration, tracked, result, background, main);
    }
}

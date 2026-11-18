package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.modpack.WorldBundles;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPacksSource.class)
abstract class ServerPacksSourceMixin {
    @WrapMethod(method = "createPackRepository(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;)Lnet/minecraft/server/packs/repository/PackRepository;")
    private static PackRepository worldarchitect$useWorldArchive(LevelStorageSource.LevelStorageAccess storage, Operation<PackRepository> original) {
        return WorldBundles.createRepository(storage, () -> original.call(storage));
    }
}

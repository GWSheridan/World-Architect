package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelStorageSource.LevelStorageAccess.class)
abstract class LevelStorageAccessMixin {
    @Shadow public abstract Path getLevelPath(LevelResource resource);

    @Inject(method = "saveLevelData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"))
    private void worldsunwritten$rememberProtectedSave(CompoundTag root, CallbackInfo callback) {
        SaveProtection.markLevelData(root, getLevelPath(LevelResource.DATA));
    }
}

package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelStorageSource.class)
abstract class LevelStorageSourceMixin {
    // The load path otherwise falls back to default generation when its settings fail to decode.
    @Inject(method = "getLevelDataAndDimensions", at = @At("HEAD"))
    private static void worldsunwritten$validateSavedGenerator(LevelStorageSource.LevelStorageAccess access, Dynamic<?> levelData,
            WorldDataConfiguration configuration, Registry<LevelStem> datapackDimensions, HolderLookup.Provider registries,
            CallbackInfoReturnable<LevelDataAndDimensions> callback) {
        SaveProtection.verify(access, levelData, configuration, datapackDimensions, registries);
    }
}

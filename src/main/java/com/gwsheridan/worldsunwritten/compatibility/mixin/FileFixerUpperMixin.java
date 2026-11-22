package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.compatibility.GenerationSnapshot;
import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.filefix.FileFixerUpper;
import net.minecraft.util.worldupdate.UpgradeProgress;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FileFixerUpper.class)
abstract class FileFixerUpperMixin {
    @Inject(method = "fix(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lcom/mojang/serialization/Dynamic;Lnet/minecraft/util/worldupdate/UpgradeProgress;I)Lcom/mojang/serialization/Dynamic;",
            at = @At("HEAD"))
    private void worldsunwritten$guardFileUpgrade(LevelStorageSource.LevelStorageAccess storage, Dynamic<?> data,
            UpgradeProgress progress, int target, CallbackInfoReturnable<Dynamic<?>> callback) {
        var dataDirectory = storage.getLevelPath(LevelResource.DATA);
        if ((GenerationSnapshot.exists(dataDirectory) || SaveProtection.knowsProtectedSave(dataDirectory, data))
                && NbtUtils.getDataVersion(data) != SharedConstants.getCurrentVersion().dataVersion().version()) {
            throw SaveProtection.incompatible("The protected save requires a different Minecraft data version. World file conversion was skipped");
        }
    }
}

package com.gwsheridan.worldarchitect.client.mixin;

import com.gwsheridan.worldarchitect.compatibility.SaveProtection.IncompatibleSaveException;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldOpenFlows.class)
abstract class ProtectedWorldOpenMixin {
    @Shadow @Final private Minecraft minecraft;

    @WrapMethod(method = "openWorldLoadLevelStem")
    private void worldarchitect$reportArchiveFailure(LevelStorageSource.LevelStorageAccess access, Dynamic<?> data,
            boolean safeMode, Runnable onCancel, Operation<Void> original) {
        try {
            original.call(access, data, safeMode, onCancel);
        } catch (IncompatibleSaveException e) {
            LogUtils.getLogger().warn(e.getMessage());
            access.safeClose();
            minecraft.gui.setScreen(new AlertScreen(onCancel, Component.translatable("worldarchitect.save_protection.title"),
                    Component.literal(e.getMessage())));
        }
    }
}

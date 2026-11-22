package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.skyblock.SkyblockJoinException;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletionException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class ServerConfigurationPacketListenerMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ConfigurationTask;tick()Z"))
    private boolean worldsunwritten$explainRejectedJoin(ConfigurationTask task, Operation<Boolean> original) {
        try {
            return original.call(task);
        } catch (CompletionException error) {
            if (error.getCause() instanceof SkyblockJoinException refusal) {
                ((ServerConfigurationPacketListenerImpl) (Object) this).disconnect(Component.literal(refusal.getMessage()));
                return false;
            }
            throw error;
        }
    }
}

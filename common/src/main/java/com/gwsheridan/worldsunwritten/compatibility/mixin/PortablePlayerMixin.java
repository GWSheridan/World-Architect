package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.platform.DeliveryData;
import com.gwsheridan.worldsunwritten.spawn.SpawnPolicies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class PortablePlayerMixin implements DeliveryData {
    @Unique private CompoundTag worldsunwritten$receipts = new CompoundTag();

    @Override
    public CompoundTag worldsunwritten$deliveryData() {
        return worldsunwritten$receipts;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void worldsunwritten$loadDeliveryData(ValueInput input, CallbackInfo callback) {
        worldsunwritten$receipts = input.read(KEY, CompoundTag.CODEC).orElseGet(CompoundTag::new);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void worldsunwritten$saveDeliveryData(ValueOutput output, CallbackInfo callback) {
        if (!worldsunwritten$receipts.isEmpty()) output.store(KEY, CompoundTag.CODEC, worldsunwritten$receipts);
    }

    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("RETURN"), cancellable = true)
    private void worldsunwritten$respawn(boolean alive, TeleportTransition.PostTeleportTransition after,
            CallbackInfoReturnable<TeleportTransition> callback) {
        SpawnPolicies.respawn((ServerPlayer) (Object) this, callback.getReturnValue(), callback::setReturnValue);
    }
}

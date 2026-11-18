package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.worldgen.ManagedGenerator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    // The dragon fight can create a portal and spikes independently of chunk generation.
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/DimensionType;hasEnderDragonFight()Z"))
    private boolean worldarchitect$strictDragonFight(DimensionType type, Operation<Boolean> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        return original.call(type) && !(level.getChunkSource().getGenerator() instanceof ManagedGenerator generator
                && generator.dimensionProfile().isPresent());
    }
}

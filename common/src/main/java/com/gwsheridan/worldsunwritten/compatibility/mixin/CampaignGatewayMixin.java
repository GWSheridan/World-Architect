package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.cells.CellEndArrivals;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TheEndGatewayBlockEntity.class)
abstract class CampaignGatewayMixin {
    @Shadow private BlockPos exitPortal;

    @Inject(method = "getPortalPosition", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$campaignArrival(
            ServerLevel level, BlockPos entry, CallbackInfoReturnable<Vec3> result) {
        if (exitPortal == null
                && level.getChunkSource().getGenerator() instanceof CellGenerator generator
                && generator.campaignEnd())
            result.setReturnValue(
                    CellEndArrivals.arrive(level, entry, (TheEndGatewayBlockEntity) (Object) this));
    }
}

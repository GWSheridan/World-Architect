package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.config.EndProgression;
import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
abstract class EndArenaRegionMixin {
    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$reserveArena(BlockPos pos, BlockState state, int flags, int recursion,
            CallbackInfoReturnable<Boolean> callback) {
        WorldGenRegion region = (WorldGenRegion) (Object) this;
        if (!EndProgression.allowsGenerationWrite(region.getCenter().getMinBlockX(), region.getCenter().getMinBlockZ(), pos.getX(), pos.getZ())
                && region.getLevel().getChunkSource().getGenerator() instanceof ManagedGenerator managed
                && managed.endProgression().isPresent()) callback.setReturnValue(false);
    }
}

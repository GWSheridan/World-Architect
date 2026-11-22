package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class CellTickMixin {
    @Inject(method="tickBlock",at=@At("HEAD"),cancellable=true)
    private void worldsunwritten$pauseReservedBlocks(BlockPos pos,net.minecraft.world.level.block.Block block,CallbackInfo callback){var level=(ServerLevel)(Object)this;if(CellWorlds.locked(level,pos)){if(CellWorlds.activating(level,pos))level.scheduleTick(pos,block,20);callback.cancel();}}
    @Inject(method="tickFluid",at=@At("HEAD"),cancellable=true)
    private void worldsunwritten$pauseReservedFluids(BlockPos pos,net.minecraft.world.level.material.Fluid fluid,CallbackInfo callback){var level=(ServerLevel)(Object)this;if(CellWorlds.locked(level,pos)){if(CellWorlds.activating(level,pos))level.scheduleTick(pos,fluid,20);callback.cancel();}}
}

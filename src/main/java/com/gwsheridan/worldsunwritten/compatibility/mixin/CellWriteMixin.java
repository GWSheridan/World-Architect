package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class CellWriteMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void worldsunwritten$keepReservedVoid(BlockPos pos,BlockState state,int flags,int limit,CallbackInfoReturnable<Boolean> result){var level=(Level)(Object)this;if(CellWorlds.outsideWritePermit(level,pos,state)||CellWorlds.locked(level,pos)||CellWorlds.fluidOutside(level,pos,state))result.setReturnValue(false);}
}

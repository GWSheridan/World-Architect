package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(HopperBlockEntity.class)
abstract class CellHopperMixin {
    @Inject(method="getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;DDD)Lnet/minecraft/world/Container;",at=@At("HEAD"),cancellable=true)
    private static void worldsunwritten$hideLockedContainer(Level level,BlockPos pos,BlockState state,double x,double y,double z,CallbackInfoReturnable<Container> result){if(CellWorlds.locked(level,pos))result.setReturnValue(null);}
    @Inject(method="pushItemsTick",at=@At("HEAD"),cancellable=true)
    private static void worldsunwritten$pauseLockedHopper(Level level,BlockPos pos,BlockState state,HopperBlockEntity entity,CallbackInfo callback){if(CellWorlds.locked(level,pos))callback.cancel();}
}

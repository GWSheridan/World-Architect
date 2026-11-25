package com.gwsheridan.worldsunwritten.fabric.mixin;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class FabricPistonMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$protectCells(Level level, BlockPos pos, Direction facing, boolean extending,
            CallbackInfoReturnable<Boolean> callback) {
        if (level instanceof ServerLevel server && CellWorlds.pistonDenied(server, pos, pos.relative(facing),
                extending ? facing : facing.getOpposite(), new PistonStructureResolver(level, pos, facing, extending))) {
            callback.setReturnValue(false);
        }
    }
}

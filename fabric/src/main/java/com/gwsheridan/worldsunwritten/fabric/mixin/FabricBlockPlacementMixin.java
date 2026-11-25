package com.gwsheridan.worldsunwritten.fabric.mixin;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import com.gwsheridan.worldsunwritten.skyblock.IslandProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
abstract class FabricBlockPlacementMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean worldsunwritten$canPlace(BlockItem item, BlockPlaceContext context, BlockState state, Operation<Boolean> original) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            BlockPos position = context.getClickedPos();
            var footprint = switch (state.getBlock()) {
                case DoorBlock ignored -> List.of(position, position.above());
                case DoublePlantBlock ignored -> List.of(position, position.above());
                case BedBlock ignored -> List.of(position, position.relative(state.getValue(BedBlock.FACING)));
                default -> List.of(position);
            };
            if (footprint.stream().anyMatch(pos -> CellWorlds.denied(player, pos))
                    || IslandProtection.placeDenied(player, footprint.stream())) return false;
        }
        return original.call(item, context, state);
    }
}

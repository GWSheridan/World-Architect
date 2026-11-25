package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

final class NeoForgeCellEvents {
    private NeoForgeCellEvents() {}

    static void register(IEventBus bus) {
        bus.addListener((AdvancementEvent.AdvancementEarnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) CellWorlds.earned(player, event.getAdvancement().id());
        });
        bus.addListener((BreakBlockEvent event) -> {
            if (event.getPlayer() instanceof ServerPlayer player && CellWorlds.denied(player, event.getPos())) {
                event.setCanceled(true);
                event.setNotifyClient(true);
            }
        });
        bus.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && (event instanceof BlockEvent.EntityMultiPlaceEvent multi
                    ? multi.getReplacedBlockSnapshots().stream().anyMatch(snapshot -> CellWorlds.denied(player, snapshot.getPos()))
                    : CellWorlds.denied(player, event.getPos()))) event.setCanceled(true);
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player && CellWorlds.denied(player, event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener((PistonEvent.Pre event) -> {
            if (event.getLevel() instanceof ServerLevel level && CellWorlds.pistonDenied(level, event.getPos(),
                    event.getFaceOffsetPos(), event.getDirection(), event.getStructureHelper())) event.setCanceled(true);
        });
        bus.addListener((MobSpawnEvent.PositionCheck event) -> {
            if (CellWorlds.locked(event.getLevel().getLevel(), event.getEntity().blockPosition())) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            }
        });
    }
}

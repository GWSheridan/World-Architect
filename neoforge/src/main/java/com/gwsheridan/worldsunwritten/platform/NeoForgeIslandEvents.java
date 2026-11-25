package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.skyblock.IslandProtection;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

final class NeoForgeIslandEvents {
    private NeoForgeIslandEvents() {}

    static void register(IEventBus bus) {
        bus.addListener((BreakBlockEvent event) -> {
            if (event.getPlayer() instanceof ServerPlayer player && IslandProtection.breakDenied(player, event.getPos(), event.getState())) {
                event.setCanceled(true);
                event.setNotifyClient(true);
            }
        });
        bus.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && IslandProtection.placeDenied(player,
                    event instanceof BlockEvent.EntityMultiPlaceEvent multi
                            ? multi.getReplacedBlockSnapshots().stream().map(snapshot -> snapshot.getPos())
                            : Stream.of(event.getPos()))) event.setCanceled(true);
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player && IslandProtection.interactionDenied(player, event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener((PlayerContainerEvent.Open event) -> {
            if (event.getEntity() instanceof ServerPlayer player) IslandProtection.opened(player, event.getContainer().containerId);
        });
        bus.addListener((PlayerContainerEvent.Close event) -> {
            if (event.getEntity() instanceof ServerPlayer player) IslandProtection.closed(player, event.getContainer().containerId);
        });
    }
}

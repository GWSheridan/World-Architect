package com.gwsheridan.worldsunwritten.forge;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import com.gwsheridan.worldsunwritten.platform.ServerEvents;
import com.gwsheridan.worldsunwritten.skyblock.IslandProtection;
import com.gwsheridan.worldsunwritten.skyblock.IslandSelections;
import com.gwsheridan.worldsunwritten.spawn.InitialSpawn;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

final class ForgeEvents {
    private ForgeEvents() {}

    static void register() {
        ServerAboutToStartEvent.BUS.addListener(event -> ServerEvents.starting(event.getServer()));
        ServerStoppedEvent.BUS.addListener(event -> CellWorlds.stopped(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> CellWorlds.tick(event.server()));
        TickEvent.PlayerTickEvent.Post.BUS.addListener(event -> IslandSelections.tick(event.player()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) ServerEvents.joined(player);
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) ServerEvents.left(player);
        });
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(event -> ServerEvents.dimensionChanged(event.getEntity()));
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(event -> WorldEnvironment.respawned(event.getEntity()));
        AdvancementEvent.AdvancementEarnEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) CellWorlds.earned(player, event.getAdvancement().id());
        });
        LevelEvent.CreateSpawnPosition.BUS.addListener(event -> {
            return event.getLevel() instanceof ServerLevel level && InitialSpawn.apply(level, event.getSettings());
        });
        BlockEvent.BreakEvent.BUS.addListener(event -> {
            return event.getPlayer() instanceof ServerPlayer player && (CellWorlds.denied(player, event.getPos())
                    || IslandProtection.breakDenied(player, event.getPos(), event.getState()));
        });
        BlockEvent.EntityPlaceEvent.BUS.addListener(event -> {
            return event.getEntity() instanceof ServerPlayer player && placeDenied(player, java.util.List.of(event.getPos()));
        });
        BlockEvent.EntityMultiPlaceEvent.BUS.addListener(event -> {
            return event.getEntity() instanceof ServerPlayer player
                    && placeDenied(player, event.getReplacedBlockSnapshots().stream().map(snapshot -> snapshot.getPos()).toList());
        });
        PlayerInteractEvent.RightClickBlock.BUS.addListener(event -> {
            return event.getEntity() instanceof ServerPlayer player && (CellWorlds.denied(player, event.getPos())
                    || IslandProtection.interactionDenied(player, event.getPos()));
        });
        PlayerContainerEvent.Open.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) IslandProtection.opened(player, event.getContainer().containerId);
        });
        PlayerContainerEvent.Close.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) IslandProtection.closed(player, event.getContainer().containerId);
        });
        PistonEvent.Pre.BUS.addListener(event -> {
            return event.getLevel() instanceof ServerLevel level && CellWorlds.pistonDenied(level, event.getPos(),
                    event.getFaceOffsetPos(), event.getDirection(), event.getStructureHelper());
        });
        MobSpawnEvent.SpawnPlacementCheck.BUS.addListener(event -> {
            if (WorldEnvironment.spawnDenied(event.getLevel().getLevel(), event.getEntityType(), event.getSpawnReason())) event.setResult(Result.DENY);
        });
        MobSpawnEvent.PositionCheck.BUS.addListener(event -> {
            var level = event.getLevel().getLevel();
            if (CellWorlds.locked(level, event.getEntity().blockPosition())
                    || WorldEnvironment.spawnDenied(level, event.getEntity().getType(), event.getSpawnReason())) event.setResult(Result.DENY);
        });
        MobSpawnEvent.FinalizeSpawn.BUS.addListener(event -> {
            if (WorldEnvironment.spawnDenied(event.getLevel().getLevel(), event.getEntity().getType(), event.getSpawnReason())) event.setSpawnCancelled(true);
        });
    }

    private static boolean placeDenied(ServerPlayer player, java.util.List<BlockPos> positions) {
        return positions.stream().anyMatch(pos -> CellWorlds.denied(player, pos))
                || IslandProtection.placeDenied(player, positions.stream());
    }
}

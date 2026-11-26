package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import com.gwsheridan.worldsunwritten.spawn.InitialSpawn;

import com.gwsheridan.worldsunwritten.commands.AllCommands;
import com.gwsheridan.worldsunwritten.commands.CommandRegistration;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCommands;
import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.gwsheridan.worldsunwritten.modpack.ModpackCommands;
import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import com.gwsheridan.worldsunwritten.environment.EnvironmentCommands;
import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import com.gwsheridan.worldsunwritten.spawn.SpawnPolicies;
import com.gwsheridan.worldsunwritten.skyblock.IslandAdministration;
import com.gwsheridan.worldsunwritten.skyblock.IslandProtection;
import com.gwsheridan.worldsunwritten.structure.StructureCommands;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

@Mod(WorldsUnwritten.ID)
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint(IEventBus modBus) {
        Platform.install(NeoForgePlatform.INSTANCE);
        WorldgenRegistration.register(new NeoForgeRegistrations(modBus, WorldsUnwritten.ID), false);
        modBus.addListener((net.neoforged.neoforge.event.AddPackFindersEvent event) -> {
            if (event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA && !event.isTrusted()) {
                ModpackRuntime.bundlePackSource().ifPresent(event::addRepositorySource);
            }
        });
        modBus.addListener(NeoForgePlatform.INSTANCE::registerAdapters);
        modBus.addListener(NeoForgePayloads::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> com.gwsheridan.worldsunwritten.workbench.WorkbenchStudio.logout(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                AllCommands.register(new CommandRegistration(event.getDispatcher(), event.getBuildContext())));
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::createSpawn);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> SaveProtection.recordNewWorld(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> CellWorlds.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> CellWorlds.stopped(event.getServer()));
        NeoForgeCellEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> com.gwsheridan.worldsunwritten.cells.CellWorlds.logout(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> WorldAdministration.load(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> WorldEnvironment.validateSaved(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> com.gwsheridan.worldsunwritten.structure.SiteStudio.logout(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> WorldEnvironment.login(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> ServerEvents.dimensionChanged(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) -> WorldEnvironment.respawned(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                SpawnPolicies.respawn(player, event.getOriginalTeleportTransition(), event::setTeleportTransition);
            }
        });
        NeoForgeSpawnEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> com.gwsheridan.worldsunwritten.skyblock.IslandSelections.login(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> com.gwsheridan.worldsunwritten.skyblock.IslandSelections.logout(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) -> com.gwsheridan.worldsunwritten.skyblock.IslandSelections.tick(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> com.gwsheridan.worldsunwritten.skyblock.IslandCaptures.logout(event.getEntity()));
        NeoForgeIslandEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> IslandProtection.logout(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> StructureCommands.onLogout(event.getEntity()));
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                (net.neoforged.neoforge.event.RegisterCommandsEvent event) -> LegacyCommands.register(new CommandRegistration(event.getDispatcher(), event.getBuildContext())));
    }

    private static void createSpawn(LevelEvent.CreateSpawnPosition event) {
        if (event.getLevel() instanceof ServerLevel level && InitialSpawn.apply(level, event.getSettings())) {
            event.setCanceled(true);
        }
    }
}

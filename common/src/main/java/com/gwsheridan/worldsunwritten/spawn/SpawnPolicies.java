package com.gwsheridan.worldsunwritten.spawn;

import com.gwsheridan.worldsunwritten.skyblock.IslandAllocations;
import com.gwsheridan.worldsunwritten.skyblock.IslandSpawns;
import com.gwsheridan.worldsunwritten.skyblock.SkyblockJoinException;
import com.gwsheridan.worldsunwritten.structure.StructureSpawns;
import com.gwsheridan.worldsunwritten.worldgen.VoidGenerator;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldsunwritten.rules.RuleSpawns;
import com.gwsheridan.worldsunwritten.rules.RuleSet;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import java.util.function.Consumer;

public final class SpawnPolicies {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private SpawnPolicies() {}
    public static boolean supportsTeamPolicy(VoidGenerator generator){return !(generator instanceof com.gwsheridan.worldsunwritten.worldgen.SkyblockGenerator rich)||rich.catalog().teams();}

    public static ServerLevel initialLevel(ServerLevel fallback) {
        return initialPolicy(fallback.getServer()).flatMap(policy -> level(fallback.getServer(), policy)).orElse(fallback);
    }

    public static Optional<SpawnPolicy> initialPolicy(MinecraftServer server) {
        return WorldAdministration.get(server).initial().or(() -> RuleSpawns.select(server.overworld(), RuleSet.SpawnEvent.INITIAL));
    }

    public static Optional<SpawnPolicy> portalPolicy(ServerLevel level) {
        return Optional.ofNullable(WorldAdministration.get(level.getServer()).portals().get(level.dimension().identifier()))
                .or(() -> RuleSpawns.select(level, RuleSet.SpawnEvent.PORTAL));
    }

    public static Optional<SafeSpawns.Target> primary(MinecraftServer server, SpawnPolicy policy, UUID player, String name) throws IOException {
        ServerLevel level = level(server, policy).orElseThrow(() -> new IOException("Unknown spawn dimension " + policy.dimension()));
        if (policy.kind() == SpawnPolicy.Kind.FIXED) {
            return SafeSpawns.nearby(level, policy.position()).map(pos -> new SafeSpawns.Target(level, pos, 0.0F));
        }
        if (policy.kind() == SpawnPolicy.Kind.MARKER || policy.kind() == SpawnPolicy.Kind.GENERATED) {
            return StructureSpawns.resolve(level, policy);
        }
        if (!(level.getChunkSource().getGenerator() instanceof VoidGenerator generator) || generator.layout().isEmpty()) {
            throw new IOException("Island spawn policies require a Skyblock dimension.");
        }
        var island = generator.layout().orElseThrow();
        if(policy.kind()==SpawnPolicy.Kind.TEAM&&!supportsTeamPolicy(generator))throw new IOException("Team spawn policies require a world created with team island allocation.");
        int slot = 0;
        if (island.personalSlots() > 0) {
            if (level != server.overworld()) {
                throw new IOException("Personal island policies require the Overworld.");
            }
            slot = com.gwsheridan.worldsunwritten.skyblock.IslandSelections.resolveSlot(server,generator,player,name,policy.kind()==SpawnPolicy.Kind.TEAM);
        }
        return IslandSpawns.position(level, island, slot).map(pos -> new SafeSpawns.Target(level, pos, 0.0F));
    }

    private static Optional<ServerLevel> level(MinecraftServer server, SpawnPolicy policy) {
        return Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, policy.dimension())));
    }

    public static SafeSpawns.Target resolveOrFallback(MinecraftServer server, SpawnPolicy policy, UUID player, String name) {
        try {
            var primary = primary(server, policy, player, name);
            if (primary.isPresent()) {
                return primary.orElseThrow();
            }
        } catch (IOException e) {
            LOGGER.warn("Cannot resolve configured spawn for {}. {}", name, e.getMessage());
        }
        ServerLevel level = level(server, policy).orElse(server.overworld());
        if (level.getChunkSource().getGenerator() instanceof VoidGenerator generator && generator.layout().isPresent()) {
            var island = generator.layout().orElseThrow();
            int slot = island.personalSlots() == 0 ? 0 : IslandAllocations.assignedSlot(
                    server.getWorldPath(LevelResource.DATA), island.personalSlots(), player);
            var position = IslandSpawns.position(level, island, slot);
            if (position.isPresent()) {
                return new SafeSpawns.Target(level, position.orElseThrow(), 0.0F);
            }
        }
        if (level.getChunkSource().getGenerator() instanceof SolidColumnGenerator generator) {
            var position = generator.initialSpawn(level, level.getChunkSource().randomState().seed()).flatMap(pos -> SafeSpawns.nearby(level, pos));
            if (position.isPresent()) {
                return new SafeSpawns.Target(level, position.orElseThrow(), 0.0F);
            }
        }
        var spawn = server.overworld().getRespawnData();
        ServerLevel spawnLevel = server.getLevel(spawn.dimension());
        if (spawnLevel != null) {
            var position = SafeSpawns.nearby(spawnLevel, spawn.pos());
            if (position.isPresent()) {
                return new SafeSpawns.Target(spawnLevel, position.orElseThrow(), spawn.yaw());
            }
        }
        throw new SkyblockJoinException("Worlds Unwritten could not find a safe spawn or fallback. Contact the world administrator.");
    }

    public static void respawn(ServerPlayer player, TeleportTransition original, Consumer<TeleportTransition> setTransition) {
        if (player.getRespawnConfig() != null && !original.missingRespawnBlock()) {
            return;
        }
        var policy = WorldAdministration.get(player.level().getServer()).respawn()
                .or(() -> RuleSpawns.select(player.level(), RuleSet.SpawnEvent.RESPAWN))
                .or(() -> RuleSpawns.select(player.level().getServer().overworld(), RuleSet.SpawnEvent.RESPAWN));
        if (policy.isEmpty()) {
            var cellState=com.gwsheridan.worldsunwritten.cells.CellWorlds.state(original.newLevel());
            if(cellState!=null){
                try{int slot=com.gwsheridan.worldsunwritten.cells.CellWorlds.territory(cellState,player,true);if(cellState.ledger.status(new com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key(slot,com.gwsheridan.worldsunwritten.api.CellLayout.Cell.ORIGIN))!=com.gwsheridan.worldsunwritten.cells.CellLedger.Status.READY)slot=0;
                    var pos=cellState.generator.spawn(new com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key(slot,com.gwsheridan.worldsunwritten.api.CellLayout.Cell.ORIGIN),original.newLevel());
                    var safe=SafeSpawns.nearby(original.newLevel(),pos);if(safe.isEmpty()){player.connection.disconnect(Component.literal("Worlds Unwritten could not find a safe cell respawn. Ask an administrator to repair its marker."));return;}
                    setTransition.accept(new TeleportTransition(original.newLevel(),net.minecraft.world.phys.Vec3.atBottomCenterOf(safe.orElseThrow()),net.minecraft.world.phys.Vec3.ZERO,0,0,original.missingRespawnBlock(),false,original.relatives(),original.postTeleportTransition()));return;
                }catch(IOException error){throw new java.io.UncheckedIOException(error);}
            }
            IslandSpawns.respawn(player, original, setTransition);
            return;
        }
        try {
            var target = resolveOrFallback(player.level().getServer(), policy.orElseThrow(), player.getUUID(), player.getPlainTextName());
            var old = original;
            setTransition.accept(new TeleportTransition(target.level(), target.center(), net.minecraft.world.phys.Vec3.ZERO,
                    target.yaw(), 0.0F, old.missingRespawnBlock(), false, old.relatives(), old.postTeleportTransition()));
        } catch (RuntimeException e) {
            LOGGER.error("Cannot resolve a safe respawn", e);
            player.connection.disconnect(Component.literal("Worlds Unwritten could not find a safe respawn. Contact the world administrator."));
        }
    }

    public static Optional<TeleportTransition> portal(ServerLevel level, Entity entity) {
        var policy = portalPolicy(level);
        if (policy.isEmpty()) {
            return Optional.empty();
        }
        String name = entity instanceof ServerPlayer player ? player.getPlainTextName() : entity.getStringUUID();
        try {
            var target = resolveOrFallback(level.getServer(), policy.orElseThrow(), entity.getUUID(), name);
            return Optional.of(target.transition(TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
        } catch (RuntimeException e) {
            LOGGER.warn("Configured portal arrival has no safe destination. {}", e.getMessage());
            return Optional.empty();
        }
    }
}

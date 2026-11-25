package com.gwsheridan.worldsunwritten.skyblock;

import net.minecraft.world.entity.player.Player;
import com.gwsheridan.worldsunwritten.config.IslandProfile;
import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import com.gwsheridan.worldsunwritten.worldgen.VoidGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import java.util.stream.Stream;

public final class IslandProtection {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<MinecraftServer, Map<java.util.UUID, ContainerAccess>> OPENED = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<java.util.UUID, ContainerAccess>> PENDING = new WeakHashMap<>();

    private IslandProtection() {}

    public static boolean denied(ServerPlayer player, BlockPos position) {
        return denied(player.level(),player,position);
    }
    public static boolean denied(net.minecraft.server.level.ServerLevel level,ServerPlayer player,BlockPos position){
        if(com.gwsheridan.worldsunwritten.cells.CellWorlds.denied(level,player,position))return true;
        var server = level.getServer();
        if (level != server.overworld() || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) {
            return false;
        }
        if (!(level.getChunkSource().getGenerator() instanceof VoidGenerator generator)
                || generator.layout().filter(island -> island.personalSlots() > 0).isEmpty()) {
            return false;
        }
        com.gwsheridan.worldsunwritten.api.IslandLayout profile = generator.layout().orElseThrow();
        int slot = cell(profile, position);
        if (slot < 0) {
            return false;
        }
        if (slot == 0) {
            return WorldAdministration.get(server).protection().shared();
        }
        var team = player.getTeam();
        try {
            return !IslandAllocations.owns(server.getWorldPath(LevelResource.DATA), profile.personalSlots(), player.getUUID(),
                    team == null ? "" : team.getName(), slot);
        } catch (java.io.UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            LOGGER.error("Cannot verify island ownership", e);
            return true;
        }
    }

    private static int cell(com.gwsheridan.worldsunwritten.api.IslandLayout profile, BlockPos position) {
        int x = position.getX();
        int z = position.getZ();
        if (x < 0 || z < 0 || x / profile.spacing() >= 16) {
            return -1;
        }
        long slot = (long) (z / profile.spacing()) * 16 + x / profile.spacing();
        return slot <= profile.personalSlots() ? (int) slot : -1;
    }

    public static boolean breakDenied(ServerPlayer player, BlockPos position, BlockState state) {
        if (IslandSelections.pending(player)) return true;
        var protection = WorldAdministration.get(player.level().getServer()).protection();
        if ((protection.breaking() || protection.containers() && designated(protection, state)) && denied(player, position)) {
            message(player);
            return true;
        }
        return false;
    }

    public static boolean placeDenied(ServerPlayer player, Stream<BlockPos> positions) {
        if (IslandSelections.pending(player)) return true;
        if (WorldAdministration.get(player.level().getServer()).protection().placement()
                && positions.anyMatch(pos -> denied(player, pos))) {
            message(player);
            return true;
        }
        return false;
    }

    public static boolean interactionDenied(ServerPlayer player, BlockPos position) {
        if (IslandSelections.pending(player)) return true;
        var server = player.level().getServer();
        var protection = WorldAdministration.get(server).protection();
        var state = player.level().getBlockState(position);
        boolean container = protection.containers() && designated(protection, state);
        if (container && containerDenied(player, position)) {
            message(player);
            return true;
        }
        var tracked = PENDING.computeIfAbsent(server, ignored -> new HashMap<>());
        if (state.hasBlockEntity() || state.getMenuProvider(player.level(), position) != null) {
            tracked.put(player.getUUID(), new ContainerAccess(player.level().dimension().identifier(), position.immutable(), -1));
        } else {
            tracked.remove(player.getUUID());
        }
        return false;
    }

    public static void opened(ServerPlayer player, int menu) {
        var server = player.level().getServer();
        var pending = PENDING.get(server);
        var access = pending == null ? null : pending.remove(player.getUUID());
        var tracked = OPENED.computeIfAbsent(server, ignored -> new HashMap<>());
        if (access == null) {
            tracked.remove(player.getUUID());
        } else {
            tracked.put(player.getUUID(), new ContainerAccess(access.dimension(), access.position(), menu));
        }
    }

    public static void closed(ServerPlayer player, int menu) {
        var tracked = OPENED.get(player.level().getServer());
        if (tracked != null) {
            var access = tracked.get(player.getUUID());
            if (access != null && access.menu() == menu) tracked.remove(player.getUUID());
        }
    }

    public static boolean menuDenied(ServerPlayer player) {
        if (player.containerMenu == player.inventoryMenu) {
            return false;
        }
        var server = player.level().getServer();
        var tracked = OPENED.get(server);
        var access = tracked == null ? null : tracked.get(player.getUUID());
        var protection = WorldAdministration.get(server).protection();
        if (access == null || access.menu() != player.containerMenu.containerId || !protection.containers() || !access.dimension().equals(player.level().dimension().identifier())
                || !designated(protection, player.level().getBlockState(access.position()))) {
            return false;
        }
        if (containerDenied(player, access.position())) {
            player.closeContainer();
            player.inventoryMenu.sendAllDataToRemote();
            tracked.remove(player.getUUID());
            message(player);
            return true;
        }
        return false;
    }

    public static void revalidate(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(IslandProtection::menuDenied);
    }

    public static void logout(Player actor) {
        if (actor instanceof ServerPlayer player) {
            var tracked = OPENED.get(player.level().getServer());
            if (tracked != null) {
                tracked.remove(player.getUUID());
            }
            var pending = PENDING.get(player.level().getServer());
            if (pending != null) {
                pending.remove(player.getUUID());
            }
        }
    }

    private static boolean designated(WorldAdministration.Protection protection, BlockState state) {
        return protection.containerBlocks().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static boolean containerDenied(ServerPlayer player, BlockPos position) {
        if (denied(player, position)) {
            return true;
        }
        var state = player.level().getBlockState(position);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connected = position.relative(ChestBlock.getConnectedDirection(state));
            return player.level().getBlockState(connected).is(state.getBlock()) && denied(player, connected);
        }
        return false;
    }

    private static void message(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("This island is protected. Use your assigned island or ask an operator."), true);
    }

    private record ContainerAccess(Identifier dimension, BlockPos position, int menu) {}
}

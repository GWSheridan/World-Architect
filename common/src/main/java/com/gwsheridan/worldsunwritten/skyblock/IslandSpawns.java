package com.gwsheridan.worldsunwritten.skyblock;

import com.gwsheridan.worldsunwritten.config.IslandProfile;
import com.gwsheridan.worldsunwritten.config.IslandTemplate;
import com.gwsheridan.worldsunwritten.worldgen.VoidGenerator;
import com.gwsheridan.worldsunwritten.spawn.SafeSpawns;
import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class IslandSpawns {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private IslandSpawns() {}

    public static Vec3 initial(ServerLevel level, VoidGenerator generator, UUID player) {
        var current=level.getServer().getPlayerList().getPlayer(player);
        return initial(level,generator,player,current==null?player.toString():current.getPlainTextName());
    }

    public static Vec3 initial(ServerLevel level, VoidGenerator generator, UUID player, String name) {
        if (generator.layout().isEmpty()) {
            return Vec3.atBottomCenterOf(new BlockPos(8, level.getMinY() + 4, 8));
        }
        com.gwsheridan.worldsunwritten.api.IslandLayout profile = generator.layout().orElseThrow();
        int slot = IslandSelections.resolveSlot(level.getServer(),generator,player,name,false);
        return position(level, profile, slot).map(Vec3::atBottomCenterOf).orElseThrow(() ->
                new SkyblockJoinException("Worlds Unwritten could not find a safe Skyblock spawn. Contact the world administrator."));
    }

    public static void respawn(ServerPlayer player, TeleportTransition original, Consumer<TeleportTransition> setTransition) {
        if (player.getRespawnConfig() != null && !original.missingRespawnBlock()) {
            return;
        }
        ServerLevel level = player.level().getServer().overworld();
        if (!(level.getChunkSource().getGenerator() instanceof VoidGenerator generator) || generator.layout().isEmpty()) {
            return;
        }
        var old = original;
        try {
            com.gwsheridan.worldsunwritten.api.IslandLayout profile = generator.layout().orElseThrow();
            var position = position(level, profile, assignedSlot(level, profile, player.getUUID()));
            if (position.isPresent()) {
                setTransition.accept(new TeleportTransition(level, Vec3.atBottomCenterOf(position.orElseThrow()), Vec3.ZERO,
                        0.0F, 0.0F, old.missingRespawnBlock(), false, old.relatives(), old.postTeleportTransition()));
                return;
            }
            LOGGER.warn("No safe Skyblock respawn for {}", player.getPlainTextName());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not resolve the Skyblock respawn for {}", player.getPlainTextName(), exception);
        }
        player.connection.disconnect(Component.literal("Worlds Unwritten could not find a safe Skyblock respawn. Contact the world administrator."));
    }

    public static @Nullable TeleportTransition portalEntry(ServerLevel level, VoidGenerator generator, Entity entity) {
        if (generator.layout().isEmpty()) {
            return null;
        }
        com.gwsheridan.worldsunwritten.api.IslandLayout profile = generator.layout().orElseThrow();
        try {
            return position(level, profile, assignedSlot(level, profile, entity.getUUID()))
                    .map(pos -> new TeleportTransition(level, Vec3.atBottomCenterOf(pos), Vec3.ZERO, 0.0F, 0.0F,
                            TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)))
                    .orElse(null);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not resolve a Skyblock portal arrival", exception);
            return null;
        }
    }

    private static int assignedSlot(ServerLevel level, com.gwsheridan.worldsunwritten.api.IslandLayout profile, UUID player) {
        if(level.getChunkSource().getGenerator() instanceof com.gwsheridan.worldsunwritten.worldgen.SkyblockGenerator rich){var current=level.getServer().getPlayerList().getPlayer(player);return IslandSelections.assigned(level.getServer(),rich,player,current==null?player.toString():current.getPlainTextName());}
        return profile.personalSlots() == 0 ? 0 : IslandAllocations.assignedSlot(
                level.getServer().getWorldPath(LevelResource.DATA), profile.personalSlots(), player);
    }

    public static Optional<BlockPos> position(ServerLevel level, IslandProfile profile, int slot) { return position(level,(com.gwsheridan.worldsunwritten.api.IslandLayout)profile,slot); }

    public static Optional<BlockPos> position(ServerLevel level, com.gwsheridan.worldsunwritten.api.IslandLayout profile, int slot) {
        var personal = safePosition(level, profile, slot);
        return personal.isPresent() || slot == 0 ? personal : safePosition(level, profile, 0);
    }

    private static Optional<BlockPos> safePosition(ServerLevel level, com.gwsheridan.worldsunwritten.api.IslandLayout profile, int slot) {
        BlockPos marker = profile.spawn(slot);
        if (safe(level, marker)) {
            return Optional.of(marker);
        }
        BlockPos origin = profile.origin(slot);
        return profile.floors(slot).stream().map(BlockPos::above)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(marker)))
                .filter(pos -> safe(level, pos)).findFirst();
    }

    private static boolean safe(ServerLevel level, BlockPos pos) {
        return SafeSpawns.safe(level, pos);
    }
}

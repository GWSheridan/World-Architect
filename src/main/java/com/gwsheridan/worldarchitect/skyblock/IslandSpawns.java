package com.gwsheridan.worldarchitect.skyblock;

import com.gwsheridan.worldarchitect.config.IslandProfile;
import com.gwsheridan.worldarchitect.config.IslandTemplate;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.gwsheridan.worldarchitect.spawn.SafeSpawns;
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
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import org.jspecify.annotations.Nullable;

public final class IslandSpawns {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private IslandSpawns() {}

    public static Vec3 initial(ServerLevel level, VoidGenerator generator, UUID player) {
        if (generator.island().isEmpty()) {
            return Vec3.atBottomCenterOf(new BlockPos(8, level.getMinY() + 4, 8));
        }
        IslandProfile profile = generator.island().orElseThrow();
        int slot = profile.personalSlots() == 0 ? 0 : IslandAllocations.claim(
                level.getServer().getWorldPath(LevelResource.DATA), profile.personalSlots(), player);
        return position(level, profile, slot).map(Vec3::atBottomCenterOf).orElseThrow(() ->
                new SkyblockJoinException("World Architect could not find a safe Skyblock spawn. Contact the world administrator."));
    }

    public static void respawn(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.getRespawnConfig() != null && !event.getOriginalTeleportTransition().missingRespawnBlock()) {
            return;
        }
        ServerLevel level = player.level().getServer().overworld();
        if (!(level.getChunkSource().getGenerator() instanceof VoidGenerator generator) || generator.island().isEmpty()) {
            return;
        }
        var old = event.getOriginalTeleportTransition();
        try {
            IslandProfile profile = generator.island().orElseThrow();
            var position = position(level, profile, assignedSlot(level, profile, player.getUUID()));
            if (position.isPresent()) {
                event.setTeleportTransition(new TeleportTransition(level, Vec3.atBottomCenterOf(position.orElseThrow()), Vec3.ZERO,
                        0.0F, 0.0F, old.missingRespawnBlock(), false, old.relatives(), old.postTeleportTransition()));
                return;
            }
            LOGGER.warn("No safe Skyblock respawn for {}", player.getPlainTextName());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not resolve the Skyblock respawn for {}", player.getPlainTextName(), exception);
        }
        player.connection.disconnect(Component.literal("World Architect could not find a safe Skyblock respawn. Contact the world administrator."));
    }

    public static @Nullable TeleportTransition portalEntry(ServerLevel level, VoidGenerator generator, Entity entity) {
        if (generator.island().isEmpty()) {
            return null;
        }
        IslandProfile profile = generator.island().orElseThrow();
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

    private static int assignedSlot(ServerLevel level, IslandProfile profile, UUID player) {
        return profile.personalSlots() == 0 ? 0 : IslandAllocations.assignedSlot(
                level.getServer().getWorldPath(LevelResource.DATA), profile.personalSlots(), player);
    }

    public static Optional<BlockPos> position(ServerLevel level, IslandProfile profile, int slot) {
        var personal = safePosition(level, profile, slot);
        return personal.isPresent() || slot == 0 ? personal : safePosition(level, profile, 0);
    }

    private static Optional<BlockPos> safePosition(ServerLevel level, IslandProfile profile, int slot) {
        BlockPos marker = profile.spawn(slot);
        if (safe(level, marker)) {
            return Optional.of(marker);
        }
        BlockPos origin = profile.origin(slot);
        return profile.template().blocks().stream().map(cell -> origin.offset(cell.pos()).above())
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(marker)))
                .filter(pos -> safe(level, pos)).findFirst();
    }

    private static boolean safe(ServerLevel level, BlockPos pos) {
        return SafeSpawns.safe(level, pos);
    }
}

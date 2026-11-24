package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.compatibility.mixin.PlayerDataAccess;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Player inventory and territory rewards use a durable receipt, never a client-provided item count.
 */
public final class CellDeliveries {
    private static final String RECEIPT = "worldsunwritten_delivery";

    private CellDeliveries() {}

    private static void server(ServerLevel level, ServerPlayer player) {
        if (level.getServer() != player.level().getServer() || !level.getServer().isSameThread())
            throw new IllegalStateException(
                    "Deliveries require the player's logical server thread.");
    }

    private static Path file(ServerPlayer player) {
        return ((PlayerDataAccess) player.level().getServer().getPlayerList())
                .worldsunwritten$playerData()
                .getPlayerDir()
                .toPath()
                .resolve(player.getStringUUID() + ".dat");
    }

    private static CompoundTag read(ServerPlayer player) throws IOException {
        return NbtIo.readCompressed(file(player), NbtAccounter.create(16 * 1024 * 1024));
    }

    private static CompoundTag receipt(CompoundTag data) {
        return data.getCompoundOrEmpty("NeoForgeData").getCompoundOrEmpty(RECEIPT);
    }

    private static String inventory(CompoundTag data) throws IOException {
        if (!(data.get("Inventory") instanceof ListTag list))
            throw new IOException("The saved player inventory is missing or malformed.");
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(list.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static CompoundTag save(ServerPlayer player) throws IOException {
        var expected = snapshot(player);
        ((PlayerDataAccess) player.level().getServer().getPlayerList())
                .worldsunwritten$playerData()
                .save(player);
        try (var channel = FileChannel.open(file(player), StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        var stored = read(player);
        var current = snapshot(player);
        if (!inventory(expected).equals(inventory(stored))
                || !inventory(expected).equals(inventory(current))
                || !receipt(expected).equals(receipt(stored))
                || !receipt(expected).equals(receipt(current))) {
            throw new IOException(
                    "The player inventory or receipt changed while saving the delivery.");
        }
        return stored;
    }

    private static CompoundTag snapshot(ServerPlayer player) {
        var output =
                net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(output);
        return output.buildResult();
    }

    static void recoverOffline(ServerLevel level, CellLedger ledger) throws IOException {
        var dir =
                ((PlayerDataAccess) level.getServer().getPlayerList())
                        .worldsunwritten$playerData()
                        .getPlayerDir()
                        .toPath();
        for (var claim : ledger.pendingDeliveries()) {
            var data =
                    NbtIo.readCompressed(
                            dir.resolve(UUID.fromString(claim.player()) + ".dat"),
                            NbtAccounter.create(16 * 1024 * 1024));
            if (receipt(data).getStringOr("transaction", "").equals(claim.transaction()))
                ledger.completeDelivery(claim);
            else if (inventory(data).equals(claim.beforeInventory())) ledger.abortDelivery(claim);
            else
                throw new IOException(
                        "Cannot reconcile delivery "
                                + claim.goal()
                                + " for player "
                                + claim.player()
                                + ". Restore the complete world backup.");
        }
    }

    public static List<String> submit(ServerLevel level, ServerPlayer player, String objective)
            throws IOException {
        server(level, player);
        recover(player);
        var state = CellWorlds.state(level);
        if (state == null)
            throw new IllegalArgumentException("This dimension has no progressive cells.");
        var delivery =
                state.generator.cells().campaign().orElse(CellCampaign.EMPTY).deliveries().stream()
                        .filter(d -> d.goal().equals(objective))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "This objective has no item turn-in."));
        var item = BuiltInRegistries.ITEM.getValue(delivery.item());
        if (item == null || item == net.minecraft.world.item.Items.AIR)
            throw new IllegalArgumentException("The delivery item is missing.");
        int slot = CellWorlds.territory(state, player, true);
        if (slot == 0 && state.generator.cells().growth().scope() != GrowthRules.Scope.SHARED)
            throw new IllegalArgumentException("Join a territory first.");
        var goal =
                state.generator.cells().growth().goals().stream()
                        .filter(g -> g.id().equals(objective))
                        .findFirst()
                        .orElseThrow();
        if (state.ledger.completed(slot).contains(objective))
            throw new IllegalArgumentException("This objective is already completed.");
        if (!goal.prerequisites(state.ledger.completed(slot)))
            throw new IllegalArgumentException("Complete the prerequisites first.");
        var items = player.getInventory().getNonEquipmentItems();
        int available = items.stream().filter(s -> s.is(item)).mapToInt(s -> s.getCount()).sum();
        if (available < delivery.count())
            throw new IllegalArgumentException(
                    "Bring "
                            + delivery.count()
                            + " "
                            + delivery.item()
                            + " in your main inventory.");
        var before = save(player);
        var claim =
                state.ledger.beginDelivery(slot, objective, player.getUUID(), inventory(before));
        var marker = new CompoundTag();
        marker.putString("transaction", claim.transaction());
        marker.putString("dimension", level.dimension().identifier().toString());
        player.getPersistentData().put(RECEIPT, marker);
        int remaining = delivery.count();
        for (var stack : items)
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (remaining == 0) break;
            }
        player.getInventory().setChanged();
        try {
            var stored = save(player);
            if (!receipt(stored).getStringOr("transaction", "").equals(claim.transaction()))
                throw new IOException("The player save did not retain its delivery receipt.");
            var awarded = state.ledger.completeDelivery(claim);
            player.getPersistentData().remove(RECEIPT);
            save(player);
            player.containerMenu.broadcastChanges();
            return awarded;
        } catch (IOException error) {
            player.connection.disconnect(
                    Component.literal(
                            "Your delivery is safely pending. Reconnect after server storage is"
                                    + " available."));
            throw error;
        }
    }

    public static void recover(ServerPlayer player) throws IOException {
        if (!player.level().getServer().isSameThread())
            throw new IllegalStateException("Delivery recovery requires the server thread.");
        var pending = new ArrayList<Map.Entry<ServerLevel, CellLedger.DeliveryClaim>>();
        for (var level : player.level().getServer().getAllLevels()) {
            var state = CellWorlds.state(level);
            if (state != null)
                for (var claim : state.ledger.deliveries(player.getUUID()))
                    if (claim.state().equals("PREPARED")
                            || player.getPersistentData()
                                    .getCompoundOrEmpty(RECEIPT)
                                    .getStringOr("transaction", "")
                                    .equals(claim.transaction()))
                        pending.add(Map.entry(level, claim));
        }
        if (pending.isEmpty()) {
            if (player.getPersistentData().contains(RECEIPT))
                throw new IOException(
                        "A delivery receipt has no matching saved claim. Restore the complete world"
                                + " backup.");
            return;
        }
        var stored = read(player);
        String tx = receipt(stored).getStringOr("transaction", "");
        boolean cleared = false;
        for (var entry : pending) {
            var claim = entry.getValue();
            var state = CellWorlds.state(entry.getKey());
            if (claim.transaction().equals(tx)) {
                state.ledger.completeDelivery(claim);
                player.getPersistentData().remove(RECEIPT);
                cleared = true;
            } else if (claim.state().equals("PREPARED")
                    && inventory(stored).equals(claim.beforeInventory()))
                state.ledger.abortDelivery(claim);
            else
                throw new IOException(
                        "The pending delivery inventory cannot be reconciled. Restore the complete"
                                + " world backup.");
        }
        if (cleared) save(player);
    }
}

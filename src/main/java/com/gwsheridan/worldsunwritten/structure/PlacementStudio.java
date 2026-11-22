package com.gwsheridan.worldsunwritten.structure;

import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

final class PlacementStudio {
    private record Staged(UUID token, Identifier template, Identifier dimension, TemplatePlacement placement, CompoundTag version, long expires) {}
    private static final Map<MinecraftServer, Map<UUID, Staged>> STAGED = new WeakHashMap<>();
    private PlacementStudio() {}

    static int stage(ServerPlayer player, Identifier id, TemplatePlacement placement, boolean viewer) throws IOException {
        var level = player.level(); var entry = StructureLibrary.load(level.getServer(), id);
        String problem = "Placement checks passed. Confirmation rechecks the current world.";
        boolean valid = !viewer;
        if (!viewer) try { placement.plan(level, entry, player); } catch (IOException e) { problem = e.getMessage(); valid = false; }
        UUID token = UUID.randomUUID();
        var stages = STAGED.computeIfAbsent(level.getServer(), unused -> new HashMap<>());
        stages.remove(player.getUUID());
        if (!viewer) stages.put(player.getUUID(), new Staged(token, id, level.dimension().identifier(), placement, entry.toTag(), System.nanoTime() + 60_000_000_000L));
        CompoundTag data = new CompoundTag(); data.putString("id", id.toString()); data.putString("token", token.toString());
        data.putString("dimension", level.dimension().identifier().toString());
        data.putString("rotation", switch(placement.rotation()) { case NONE -> "none"; case CLOCKWISE_90 -> "cw90"; case CLOCKWISE_180 -> "cw180"; case COUNTERCLOCKWISE_90 -> "ccw90"; });
        data.putString("mirror", placement.mirror().getSerializedName()); data.putString("status", viewer ? "Template viewer · no world changes" : problem);
        data.putBoolean("valid", valid); data.putBoolean("viewer", viewer);
        data.putIntArray("origin", new int[]{placement.origin().getX(), placement.origin().getY(), placement.origin().getZ()});
        var shape = placement.size(entry.template().size()); data.putIntArray("size", new int[]{shape.getX(), shape.getY(), shape.getZ()});
        var zero = new TemplatePlacement(BlockPos.ZERO, placement.rotation(), placement.mirror());
        var cells = entry.template().blocks().stream().filter(c -> !c.state().isAir()).toList();
        int[] visual = new int[cells.size() * 2];
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i); var pos = zero.position(entry.template().size(), cell.pos());
            visual[i * 2] = pos.getX() | pos.getY() << 5 | pos.getZ() << 10;
            visual[i * 2 + 1] = cell.state().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).col;
        }
        data.putIntArray("cells", visual);
        var markers = new ListTag();
        entry.markers().forEach((name, marker) -> {
            var pos = zero.position(entry.template().size(), marker.pos()); var tag = new CompoundTag(); tag.putString("name", name);
            tag.putIntArray("pos", new int[]{pos.getX(), pos.getY(), pos.getZ()}); tag.putString("facing", placement.facing(marker.facing()).getSerializedName()); markers.add(tag);
        }); data.put("markers", markers);
        PacketDistributor.sendToPlayer(player, new PlacementPreviewPayload(data)); return 1;
    }

    static int confirm(ServerPlayer player, String token) throws IOException {
        var stages = STAGED.get(player.level().getServer()); var staged = stages == null ? null : stages.remove(player.getUUID());
        if (staged == null || !staged.token.toString().equals(token) || System.nanoTime() > staged.expires)
            throw new IOException("The placement preview expired or was replaced. Stage it again.");
        if (!staged.dimension.equals(player.level().dimension().identifier())) throw new IOException("The preview belongs to another dimension.");
        var entry = StructureLibrary.load(player.level().getServer(), staged.template);
        if (!entry.toTag().equals(staged.version)) throw new IOException("The template changed after preview. Stage the new revision.");
        int placed = staged.placement.place(player.level(), entry, player);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Placed " + placed + " blocks. Use /worldsunwritten structure undo to undo this manual placement."));
        return placed;
    }

    static void logout(ServerPlayer player) { var stages = STAGED.get(player.level().getServer()); if (stages != null) stages.remove(player.getUUID()); }
}

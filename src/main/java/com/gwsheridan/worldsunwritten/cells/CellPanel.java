package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;

final class CellPanel {
    private CellPanel() {}

    static void open(ServerPlayer player) throws IOException {
        var state = CellWorlds.state(player.level());
        if (state == null)
            throw new IllegalArgumentException("This dimension has no progressive cells.");
        int slot = CellWorlds.territory(state, player, true);
        var root = new CompoundTag();
        root.putString("kind", "cell_progress");
        root.putString("dimension", player.level().dimension().identifier().toString());
        root.putInt("territory", slot);
        root.putInt("credits", state.ledger.credits(slot));
        root.putBoolean("choice", state.generator.cells().growth().chooseFrontier());
        var frontier = new ListTag();
        for (var cell : state.ledger.frontier(slot)) {
            var row = new CompoundTag();
            row.putInt("q", cell.q());
            row.putInt("r", cell.r());
            row.putString(
                    "type",
                    state.generator.cells().tile(state.generator.seed(), slot, cell).title());
            row.putInt("cost", state.ledger.cost(slot, cell));
            frontier.add(row);
        }
        root.put("frontier", frontier);
        var completed = state.ledger.completed(slot);
        var goals = new ListTag();
        for (var goal : state.generator.cells().growth().goals()) {
            var row = new CompoundTag();
            row.putString("id", goal.id());
            row.putString("title", goal.title());
            row.putBoolean("complete", completed.contains(goal.id()));
            row.putString("trigger", goal.trigger().getSerializedName() + " · " + goal.source());
            row.putString("prerequisites", String.join(", ", goal.requires()));
            row.putString("reason", ProgressionGraph.reason(goal, completed));
            for (var d : state.generator.cells().campaign().orElse(CellCampaign.EMPTY).deliveries())
                if (d.goal().equals(goal.id())) {
                    row.putString("item", d.item().toString());
                    row.putInt("count", d.count());
                    row.putBoolean(
                            "eligible",
                            !completed.contains(goal.id()) && goal.prerequisites(completed));
                }
            goals.add(row);
        }
        root.put("goals", goals);
        var connections = new ListTag();
        state.connections.report(slot).forEach(line -> connections.add(StringTag.valueOf(line)));
        root.put("connections", connections);
        var queue = new ListTag();
        state.ledger
                .territoryCells(slot)
                .forEach(
                        (cell, status) -> {
                            if (status != CellLedger.Status.READY)
                                queue.add(
                                        StringTag.valueOf(
                                                cell.key()
                                                        + " · "
                                                        + status.name()
                                                                .toLowerCase(
                                                                        java.util.Locale.ROOT)));
                        });
        root.put("queue", queue);
        PacketDistributor.sendToPlayer(player, new StudioViewPayload(root));
    }
}

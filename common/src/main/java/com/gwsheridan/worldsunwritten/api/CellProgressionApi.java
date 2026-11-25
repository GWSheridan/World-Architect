package com.gwsheridan.worldsunwritten.api;

import com.gwsheridan.worldsunwritten.cells.*;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Version-one quest adapter: invoke from the logical server thread after validating the quest
 * reward.
 */
public final class CellProgressionApi {
    public static final int VERSION = 1;

    private CellProgressionApi() {}

    /**
     * Repeated signals are harmless. Goal definitions and terrain rewards come from the saved
     * preset.
     */
    public static List<String> signal(ServerPlayer player, Identifier objective) {
        return CellWorlds.signal(player, GrowthRules.Trigger.SIGNAL, objective);
    }

    /** Explicit target for quests completed while the player is visiting another dimension. */
    public static List<String> signal(
            ServerLevel territoryDimension, ServerPlayer player, Identifier objective) {
        return CellWorlds.signal(territoryDimension, player, GrowthRules.Trigger.SIGNAL, objective);
    }

    public record Objective(String id, String title, boolean completed, String reason) {}

    public record Frontier(CellLayout.Cell cell, String type, int cost) {}

    public record Progress(
            int territory,
            boolean assigned,
            int credits,
            List<Objective> objectives,
            List<Frontier> frontier) {
        public Progress {
            objectives = List.copyOf(objectives);
            frontier = List.copyOf(frontier);
        }
    }

    /** A detached view of this player's current territory; querying never allocates a territory. */
    public static Progress progress(ServerLevel level, ServerPlayer player) {
        if (level.getServer() != player.level().getServer() || !level.getServer().isSameThread())
            throw new IllegalStateException("Progress queries require the player's server thread.");
        var state = CellWorlds.state(level);
        if (state == null)
            throw new IllegalArgumentException("This dimension has no progressive cells.");
        try {
            int slot = CellWorlds.territory(state, player, false);
            boolean assigned =
                    slot != 0
                            || state.generator.cells().growth().scope() == GrowthRules.Scope.SHARED;
            var done = assigned ? state.ledger.completed(slot) : java.util.Set.<String>of();
            return new Progress(
                    slot,
                    assigned,
                    assigned ? state.ledger.credits(slot) : 0,
                    state.generator.cells().growth().goals().stream()
                            .map(
                                    g ->
                                            new Objective(
                                                    g.id(),
                                                    g.title(),
                                                    done.contains(g.id()),
                                                    assigned
                                                            ? ProgressionGraph.reason(g, done)
                                                            : "Join an assigned territory first."))
                            .toList(),
                    assigned
                            ? state.ledger.frontier(slot).stream()
                                    .map(
                                            c ->
                                                    new Frontier(
                                                            c,
                                                            state.generator
                                                                    .cells()
                                                                    .tile(
                                                                            state.generator.seed(),
                                                                            slot,
                                                                            c)
                                                                    .title(),
                                                            state.ledger.cost(slot, c)))
                                    .toList()
                            : List.of());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * Explicit item turn-in. The server consumes only the items in the saved objective definition.
     */
    public static List<String> submit(ServerLevel level, ServerPlayer player, String objective) {
        try {
            return CellDeliveries.submit(level, player, objective);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}

package com.gwsheridan.worldsunwritten.cells;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;

import static net.minecraft.commands.Commands.*;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;

public final class CellCommands {
    private CellCommands() {}

    public static void register(RegisterCommandsEvent event) {
        var root = literal("cells").executes(c -> run(c, () -> status(c)));
        root.then(
                literal("panel")
                        .executes(
                                c ->
                                        run(
                                                c,
                                                () ->
                                                        CellPanel.open(
                                                                c.getSource()
                                                                        .getPlayerOrException()))));
        root.then(
                literal("home")
                        .executes(
                                c ->
                                        run(
                                                c,
                                                () ->
                                                        CellWorlds.home(
                                                                c.getSource()
                                                                        .getPlayerOrException()))));
        root.then(
                literal("submit")
                        .then(
                                argument("objective", word())
                                        .executes(
                                                c ->
                                                        run(
                                                                c,
                                                                () ->
                                                                        CellDeliveries.submit(
                                                                                c.getSource()
                                                                                        .getLevel(),
                                                                                c.getSource()
                                                                                        .getPlayerOrException(),
                                                                                getString(
                                                                                        c,
                                                                                        "objective"))))));
        root.then(
                literal("submit_confirm")
                        .then(
                                argument("dimension", IdentifierArgument.id())
                                        .then(
                                                argument("territory", integer(0, 255))
                                                        .then(
                                                                argument("objective", word())
                                                                        .then(
                                                                                argument(
                                                                                                "item",
                                                                                                IdentifierArgument
                                                                                                        .id())
                                                                                        .then(
                                                                                                argument(
                                                                                                                "count",
                                                                                                                integer(
                                                                                                                        1,
                                                                                                                        4096))
                                                                                                        .executes(
                                                                                                                c ->
                                                                                                                        run(
                                                                                                                                c,
                                                                                                                                () -> {
                                                                                                                                    var
                                                                                                                                            player =
                                                                                                                                                    c.getSource()
                                                                                                                                                            .getPlayerOrException();
                                                                                                                                    var
                                                                                                                                            s =
                                                                                                                                                    state(
                                                                                                                                                            c);
                                                                                                                                    if (!player.level()
                                                                                                                                                    .dimension()
                                                                                                                                                    .identifier()
                                                                                                                                                    .equals(
                                                                                                                                                            IdentifierArgument
                                                                                                                                                                    .getId(
                                                                                                                                                                            c,
                                                                                                                                                                            "dimension"))
                                                                                                                                            || CellWorlds
                                                                                                                                                            .territory(
                                                                                                                                                                    s,
                                                                                                                                                                    player,
                                                                                                                                                                    false)
                                                                                                                                                    != getInteger(
                                                                                                                                                            c,
                                                                                                                                                            "territory"))
                                                                                                                                        throw new IllegalArgumentException(
                                                                                                                                                "Your territory"
                                                                                                                                                    + " changed;"
                                                                                                                                                    + " refresh"
                                                                                                                                                    + " the panel.");
                                                                                                                                    var
                                                                                                                                            d =
                                                                                                                                                    s
                                                                                                                                                            .generator
                                                                                                                                                            .cells()
                                                                                                                                                            .campaign()
                                                                                                                                                            .orElse(
                                                                                                                                                                    CellCampaign
                                                                                                                                                                            .EMPTY)
                                                                                                                                                            .deliveries()
                                                                                                                                                            .stream()
                                                                                                                                                            .filter(
                                                                                                                                                                    v ->
                                                                                                                                                                            v.goal()
                                                                                                                                                                                    .equals(
                                                                                                                                                                                            getString(
                                                                                                                                                                                                    c,
                                                                                                                                                                                                    "objective")))
                                                                                                                                                            .findFirst()
                                                                                                                                                            .orElseThrow();
                                                                                                                                    if (!d.item()
                                                                                                                                                    .equals(
                                                                                                                                                            IdentifierArgument
                                                                                                                                                                    .getId(
                                                                                                                                                                            c,
                                                                                                                                                                            "item"))
                                                                                                                                            || d
                                                                                                                                                            .count()
                                                                                                                                                    != getInteger(
                                                                                                                                                            c,
                                                                                                                                                            "count"))
                                                                                                                                        throw new IllegalArgumentException(
                                                                                                                                                "The saved"
                                                                                                                                                    + " delivery"
                                                                                                                                                    + " differs;"
                                                                                                                                                    + " refresh"
                                                                                                                                                    + " the panel.");
                                                                                                                                    CellDeliveries
                                                                                                                                            .submit(
                                                                                                                                                    player
                                                                                                                                                            .level(),
                                                                                                                                                    player,
                                                                                                                                                    d
                                                                                                                                                            .goal());
                                                                                                                                }))))))));
        root.then(
                literal("choose_confirm")
                        .then(
                                argument("dimension", IdentifierArgument.id())
                                        .then(
                                                argument("territory", integer(0, 255))
                                                        .then(
                                                                argument("q", integer(-30, 30))
                                                                        .then(
                                                                                argument(
                                                                                                "r",
                                                                                                integer(
                                                                                                        -30,
                                                                                                        30))
                                                                                        .then(
                                                                                                argument(
                                                                                                                "cost",
                                                                                                                integer(
                                                                                                                        1,
                                                                                                                        64))
                                                                                                        .executes(
                                                                                                                c ->
                                                                                                                        run(
                                                                                                                                c,
                                                                                                                                () -> {
                                                                                                                                    var
                                                                                                                                            s =
                                                                                                                                                    state(
                                                                                                                                                            c);
                                                                                                                                    int
                                                                                                                                            slot =
                                                                                                                                                    CellWorlds
                                                                                                                                                            .territory(
                                                                                                                                                                    s,
                                                                                                                                                                    c.getSource()
                                                                                                                                                                            .getPlayerOrException(),
                                                                                                                                                                    false);
                                                                                                                                    var
                                                                                                                                            cell =
                                                                                                                                                    new Cell(
                                                                                                                                                            getInteger(
                                                                                                                                                                    c,
                                                                                                                                                                    "q"),
                                                                                                                                                            getInteger(
                                                                                                                                                                    c,
                                                                                                                                                                    "r"));
                                                                                                                                    if (!c.getSource()
                                                                                                                                                    .getLevel()
                                                                                                                                                    .dimension()
                                                                                                                                                    .identifier()
                                                                                                                                                    .equals(
                                                                                                                                                            IdentifierArgument
                                                                                                                                                                    .getId(
                                                                                                                                                                            c,
                                                                                                                                                                            "dimension"))
                                                                                                                                            || slot
                                                                                                                                                    != getInteger(
                                                                                                                                                            c,
                                                                                                                                                            "territory")
                                                                                                                                            || s
                                                                                                                                                            .ledger
                                                                                                                                                            .cost(
                                                                                                                                                                    slot,
                                                                                                                                                                    cell)
                                                                                                                                                    != getInteger(
                                                                                                                                                            c,
                                                                                                                                                            "cost"))
                                                                                                                                        throw new IllegalArgumentException(
                                                                                                                                                "Your territory"
                                                                                                                                                    + " or cost"
                                                                                                                                                    + " changed;"
                                                                                                                                                    + " refresh"
                                                                                                                                                    + " the panel.");
                                                                                                                                    s
                                                                                                                                            .ledger
                                                                                                                                            .choose(
                                                                                                                                                    slot,
                                                                                                                                                    cell);
                                                                                                                                }))))))));
        root.then(
                literal("choose")
                        .then(
                                argument("q", integer(-30, 30))
                                        .then(
                                                argument("r", integer(-30, 30))
                                                        .executes(
                                                                c ->
                                                                        run(
                                                                                c,
                                                                                () -> {
                                                                                    var s =
                                                                                            state(
                                                                                                    c);
                                                                                    var p =
                                                                                            c.getSource()
                                                                                                    .getPlayerOrException();
                                                                                    s.ledger.choose(
                                                                                            CellWorlds
                                                                                                    .territory(
                                                                                                            s,
                                                                                                            p,
                                                                                                            true),
                                                                                            new Cell(
                                                                                                    getInteger(
                                                                                                            c,
                                                                                                            "q"),
                                                                                                    getInteger(
                                                                                                            c,
                                                                                                            "r")));
                                                                                })))));
        var admin = literal("admin").requires(hasPermission(LEVEL_ADMINS));
        admin.then(
                literal("connections")
                        .then(
                                argument("territory", integer(0, 255))
                                        .executes(
                                                c ->
                                                        run(
                                                                c,
                                                                () ->
                                                                        c.getSource()
                                                                                .sendSuccess(
                                                                                        () ->
                                                                                                Component
                                                                                                        .literal(
                                                                                                                String
                                                                                                                        .join(
                                                                                                                                "\n",
                                                                                                                                state(
                                                                                                                                                c)
                                                                                                                                        .connections
                                                                                                                                        .report(
                                                                                                                                                getInteger(
                                                                                                                                                        c,
                                                                                                                                                        "territory")))),
                                                                                        false)))));
        admin.then(
                literal("retry_connection")
                        .then(
                                argument("territory", integer(0, 255))
                                        .then(
                                                argument("connection", word())
                                                        .executes(
                                                                c ->
                                                                        run(
                                                                                c,
                                                                                () ->
                                                                                        state(c)
                                                                                                .connections
                                                                                                .retry(
                                                                                                        getInteger(
                                                                                                                c,
                                                                                                                "territory"),
                                                                                                        getString(
                                                                                                                c,
                                                                                                                "connection")))))));
        admin.then(
                literal("unlock")
                        .then(
                                argument("territory", integer(0, 255))
                                        .then(
                                                argument("q", integer(-30, 30))
                                                        .then(
                                                                argument("r", integer(-30, 30))
                                                                        .executes(
                                                                                c ->
                                                                                        run(
                                                                                                c,
                                                                                                () ->
                                                                                                        state(
                                                                                                                        c)
                                                                                                                .ledger
                                                                                                                .queue(
                                                                                                                        key(
                                                                                                                                c))))))));
        admin.then(
                literal("retry")
                        .then(
                                argument("territory", integer(0, 255))
                                        .then(
                                                argument("q", integer(-30, 30))
                                                        .then(
                                                                argument("r", integer(-30, 30))
                                                                        .executes(
                                                                                c ->
                                                                                        run(
                                                                                                c,
                                                                                                () ->
                                                                                                        state(
                                                                                                                        c)
                                                                                                                .ledger
                                                                                                                .retry(
                                                                                                                        key(
                                                                                                                                c))))))));
        admin.then(
                literal("signal")
                        .then(
                                argument("player", EntityArgument.player())
                                        .then(
                                                argument("signal", IdentifierArgument.id())
                                                        .executes(
                                                                c ->
                                                                        run(
                                                                                c,
                                                                                () ->
                                                                                        CellWorlds
                                                                                                .signal(
                                                                                                        c.getSource()
                                                                                                                .getLevel(),
                                                                                                        EntityArgument
                                                                                                                .getPlayer(
                                                                                                                        c,
                                                                                                                        "player"),
                                                                                                        GrowthRules
                                                                                                                .Trigger
                                                                                                                .SIGNAL,
                                                                                                        IdentifierArgument
                                                                                                                .getId(
                                                                                                                        c,
                                                                                                                        "signal")))))));
        admin.then(
                literal("reassign")
                        .then(
                                argument("territory", integer(1, 255))
                                        .then(
                                                argument("owner", greedyString())
                                                        .executes(
                                                                c ->
                                                                        run(
                                                                                c,
                                                                                () -> {
                                                                                    var state =
                                                                                            state(
                                                                                                    c);
                                                                                    var scope =
                                                                                            state.generator
                                                                                                    .cells()
                                                                                                    .growth()
                                                                                                    .scope();
                                                                                    String owner =
                                                                                            getString(
                                                                                                            c,
                                                                                                            "owner")
                                                                                                    .trim();
                                                                                    if (scope
                                                                                            == GrowthRules
                                                                                                    .Scope
                                                                                                    .PLAYER) {
                                                                                        if (!owner
                                                                                                .startsWith(
                                                                                                        "player:"))
                                                                                            throw new IllegalArgumentException(
                                                                                                    "Use player:UUID"
                                                                                                        + " in this"
                                                                                                        + " world.");
                                                                                        UUID
                                                                                                .fromString(
                                                                                                        owner
                                                                                                                .substring(
                                                                                                                        7));
                                                                                    } else if (scope
                                                                                            == GrowthRules
                                                                                                    .Scope
                                                                                                    .TEAM) {
                                                                                        if (!owner
                                                                                                        .startsWith(
                                                                                                                "team:")
                                                                                                || c.getSource()
                                                                                                                .getServer()
                                                                                                                .getScoreboard()
                                                                                                                .getPlayerTeam(
                                                                                                                        owner
                                                                                                                                .substring(
                                                                                                                                        5))
                                                                                                        == null)
                                                                                            throw new IllegalArgumentException(
                                                                                                    "Use team:NAME"
                                                                                                        + " for an"
                                                                                                        + " existing"
                                                                                                        + " scoreboard"
                                                                                                        + " team.");
                                                                                    } else
                                                                                        throw new IllegalArgumentException(
                                                                                                "Shared"
                                                                                                    + " territory"
                                                                                                    + " cannot"
                                                                                                    + " be reassigned.");
                                                                                    state.ledger
                                                                                            .reassign(
                                                                                                    owner,
                                                                                                    getInteger(
                                                                                                            c,
                                                                                                            "territory"));
                                                                                })))));
        root.then(admin);
        com.gwsheridan.worldsunwritten.commands.ModCommands.register(event, root);
    }

    private static Key key(CommandContext<CommandSourceStack> c) {
        return new Key(
                getInteger(c, "territory"), new Cell(getInteger(c, "q"), getInteger(c, "r")));
    }

    private static CellWorlds.State state(CommandContext<CommandSourceStack> c) {
        var state = CellWorlds.state(c.getSource().getLevel());
        if (state == null)
            throw new IllegalArgumentException("This dimension has no progressive cells.");
        return state;
    }

    private static void status(CommandContext<CommandSourceStack> c) throws Exception {
        var s = state(c);
        int slot =
                c.getSource().getPlayer() == null
                        ? 0
                        : CellWorlds.territory(s, c.getSource().getPlayer(), false);
        long ready =
                s.ledger.territoryCells(slot).values().stream()
                        .filter(status -> status == CellLedger.Status.READY)
                        .count();
        c.getSource()
                .sendSuccess(
                        () ->
                                Component.literal(
                                        "Territory "
                                                + slot
                                                + " · "
                                                + ready
                                                + " open cells · "
                                                + s.ledger.credits(slot)
                                                + " expansion credits\nCompleted: "
                                                + String.join(", ", s.ledger.completed(slot))
                                                + "\nFrontier: "
                                                + s.ledger.frontier(slot).stream()
                                                        .map(Cell::key)
                                                        .limit(20)
                                                        .toList()),
                        false);
    }

    private static int run(CommandContext<CommandSourceStack> c, Action action) {
        try {
            action.run();
            return 1;
        } catch (Exception error) {
            c.getSource()
                    .sendFailure(
                            Component.literal(
                                    Objects.toString(error.getMessage(), "Cell action failed")));
            return 0;
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }
}

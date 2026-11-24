package com.gwsheridan.worldsunwritten.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Server-provided view only. Every selection is checked against current ownership and credits. */
final class CellProgressScreen extends Screen {
    private final CompoundTag view;

    CellProgressScreen(CompoundTag view) {
        super(Component.literal("World Expansion"));
        this.view = view.copy();
    }

    @Override
    protected void init() {
        int w = Math.min(400, width - 24), x = (width - w) / 2;
        addRenderableWidget(
                                Button.builder(
                                                Component.literal("Choose frontier cell"),
                                                b -> {
                                                    var cells = new LinkedHashMap<String, String>();
                                                    for (var value :
                                                            view.getListOrEmpty("frontier")) {
                                                        var c = (CompoundTag) value;
                                                        int cost = c.getIntOr("cost", 1);
                                                        String coords =
                                                                view.getStringOr("dimension", "")
                                                                        + " "
                                                                        + view.getIntOr(
                                                                                "territory", 0)
                                                                        + " "
                                                                        + c.getIntOr("q", 0)
                                                                        + " "
                                                                        + c.getIntOr("r", 0)
                                                                        + " "
                                                                        + cost;
                                                        if (cost <= view.getIntOr("credits", 0))
                                                            cells.put(
                                                                    c.getIntOr("q", 0)
                                                                            + ", "
                                                                            + c.getIntOr("r", 0)
                                                                            + " · "
                                                                            + c.getStringOr(
                                                                                    "type", "Cell")
                                                                            + " · "
                                                                            + cost
                                                                            + " credits",
                                                                    coords);
                                                    }
                                                    minecraft.gui.setScreen(
                                                            new StudioChoiceScreen(
                                                                    this,
                                                                    "Choose an affordable cell",
                                                                    List.copyOf(cells.keySet()),
                                                                    label ->
                                                                            confirm(
                                                                                    label,
                                                                                    "worldsunwritten"
                                                                                        + " cells"
                                                                                        + " choose_confirm"
                                                                                        + " "
                                                                                            + cells
                                                                                                    .get(
                                                                                                            label))));
                                                })
                                        .bounds(x, 64, w, 20)
                                        .build())
                        .active =
                view.getBooleanOr("choice", false) && view.getIntOr("credits", 0) > 0;
        addRenderableWidget(
                Button.builder(
                                Component.literal("Objectives"),
                                b -> {
                                    var lines = new ArrayList<String>();
                                    for (var value : view.getListOrEmpty("goals")) {
                                        var goal = (CompoundTag) value;
                                        lines.add(
                                                (goal.getBooleanOr("complete", false) ? "✓ " : "○ ")
                                                        + goal.getStringOr("title", "")
                                                        + " · "
                                                        + goal.getStringOr("reason", ""));
                                        if (!goal.getStringOr("prerequisites", "").isBlank())
                                            lines.add(
                                                    "Requires: "
                                                            + goal.getStringOr(
                                                                    "prerequisites", ""));
                                    }
                                    minecraft.gui.setScreen(
                                            new StudioReportScreen(
                                                    this, "Expansion objectives", lines));
                                })
                        .bounds(x, 89, w, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Item turn-ins"),
                                b -> {
                                    var choices = new LinkedHashMap<String, String>();
                                    for (var value : view.getListOrEmpty("goals")) {
                                        var goal = (CompoundTag) value;
                                        if (goal.getBooleanOr("eligible", false)) {
                                            String label =
                                                    goal.getStringOr("title", "")
                                                            + " · "
                                                            + goal.getIntOr("count", 0)
                                                            + " "
                                                            + goal.getStringOr("item", "");
                                            choices.put(
                                                    label,
                                                    "worldsunwritten cells submit_confirm "
                                                            + view.getStringOr("dimension", "")
                                                            + " "
                                                            + view.getIntOr("territory", 0)
                                                            + " "
                                                            + goal.getStringOr("id", "")
                                                            + " "
                                                            + goal.getStringOr("item", "")
                                                            + " "
                                                            + goal.getIntOr("count", 0));
                                        }
                                    }
                                    minecraft.gui.setScreen(
                                            new StudioChoiceScreen(
                                                    this,
                                                    "Choose an item turn-in",
                                                    List.copyOf(choices.keySet()),
                                                    label -> confirm(label, choices.get(label))));
                                })
                        .bounds(x, 114, w, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Generation status"),
                                b -> {
                                    var lines = new ArrayList<String>();
                                    for (var value : view.getListOrEmpty("queue"))
                                        lines.add(value.asString().orElse(""));
                                    for (var value : view.getListOrEmpty("connections"))
                                        lines.add(value.asString().orElse(""));
                                    if (lines.isEmpty())
                                        lines.add("No generation jobs are pending.");
                                    minecraft.gui.setScreen(
                                            new StudioReportScreen(
                                                    this, "Generation status", lines));
                                })
                        .bounds(x, 164, w, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Return home"),
                                b -> send("worldsunwritten cells home"))
                        .bounds(x, 139, (w - 6) / 2, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Refresh"),
                                b -> send("worldsunwritten cells panel"))
                        .bounds(x + (w + 6) / 2, 139, (w - 6) / 2, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Close"), b -> onClose())
                        .bounds(x, height - 26, w, 20)
                        .build());
    }

    private void confirm(String title, String command) {
        minecraft.gui.setScreen(
                new StudioChoiceScreen(
                        this,
                        title,
                        List.of("Confirm", "Cancel"),
                        choice -> {
                            if (choice.equals("Confirm")) send(command);
                            else minecraft.gui.setScreen(this);
                        }));
    }

    private void send(String command) {
        if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(command);
        minecraft.gui.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        super.extractRenderState(g, x, y, tick);
        g.centeredText(font, title, width / 2, 12, UnwrittenTheme.TEXT);
        g.centeredText(
                font,
                Component.literal(
                        "Territory "
                                + view.getIntOr("territory", 0)
                                + " · "
                                + view.getIntOr("credits", 0)
                                + " expansion credits"),
                width / 2,
                39,
                UnwrittenTheme.MUTED);
    }
}

package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Consumer;

final class ProgressionGraphScreen extends Screen {
    private record Node(String id, int x, int y, int width) {}

    private final List<Node> nodes = new ArrayList<>();
    private final CellWorldProfile initial;
    private final Screen parent;
    private final Consumer<CellWorldProfile> save;
    private CellWorldProfile profile;
    private int layer, page;
    private String status = "Select an objective to edit requirements and rewards.";

    ProgressionGraphScreen(
            Screen parent, CellWorldProfile profile, Consumer<CellWorldProfile> save) {
        super(Component.literal("Progression Designer"));
        this.parent = parent;
        this.initial = profile;
        this.profile = profile;
        this.save = save;
    }

    private CellCampaign campaign() {
        return profile.campaign().orElse(CellCampaign.EMPTY);
    }

    @Override
    protected void init() {
        int w = width - 24, half = (w - 6) / 2;
        button(
                "Add objective",
                12,
                30,
                half,
                () ->
                        act(
                                () -> {
                                    if (!profile.progressive())
                                        throw new IllegalArgumentException(
                                                "Choose Shared, Player or Team progression first.");
                                    int n = 1;
                                    while (has("objective_" + n)) n++;
                                    var id = "objective_" + n;
                                    edit(
                                            new GrowthRules.Goal(
                                                    id,
                                                    "New objective",
                                                    GrowthRules.Trigger.SIGNAL,
                                                    Identifier.parse("modpack:" + id),
                                                    List.of(),
                                                    false,
                                                    List.of(),
                                                    1),
                                            true);
                                }));
        button("Cell costs", 15 + w / 2, 30, half, this::costs);
        var layers = ProgressionGraph.layers(profile.growth());
        layer = Math.clamp(layer, 0, Math.max(0, layers.size() - 1));
        var goals = layers.isEmpty() ? List.<GrowthRules.Goal>of() : layers.get(layer);
        int rows = Math.max(1, (height - 155) / 28), count = rows;
        page = Math.clamp(page, 0, Math.max(0, (goals.size() - 1) / count));
        nodes.clear();
        int nodeWidth = (w - 32) / 2;
        var visible = goals.subList(page * count, Math.min(goals.size(), (page + 1) * count));
        var required = new LinkedHashSet<String>();
        visible.forEach(goal -> required.addAll(goal.requires()));
        int index = 0;
        for (var id : required) {
            if (index >= rows) break;
            var goal =
                    profile.growth().goals().stream()
                            .filter(g -> g.id().equals(id))
                            .findFirst()
                            .orElseThrow();
            var node = new Node(id, 12, 74 + index++ * 28, nodeWidth);
            nodes.add(node);
            button(goal.title(), node.x(), node.y(), nodeWidth, () -> edit(goal, false));
        }
        if (required.isEmpty())
            button(
                    "No prerequisites",
                    12,
                    74,
                    nodeWidth,
                    () ->
                            minecraft.gui.setScreen(
                                    new StudioReportScreen(
                                            this,
                                            "Objective prerequisites",
                                            List.of(
                                                    "These objectives have no prerequisite"
                                                            + " links."))));
        index = 0;
        for (var goal : visible) {
            var node = new Node(goal.id(), 12 + nodeWidth + 32, 74 + index++ * 28, nodeWidth);
            nodes.add(node);
            button(goal.title(), node.x(), node.y(), nodeWidth, () -> edit(goal, false));
        }
        if (required.size() > rows)
            status =
                    "Showing "
                            + rows
                            + " prerequisite nodes; open an objective to inspect all links.";
        int nav = height - 73;
        button(
                "< Layer",
                12,
                nav,
                70,
                () -> {
                    layer--;
                    page = 0;
                    rebuildWidgets();
                });
        button(
                "Layer >",
                87,
                nav,
                70,
                () -> {
                    layer++;
                    page = 0;
                    rebuildWidgets();
                });
        button(
                "Page",
                162,
                nav,
                55,
                () -> {
                    page = page * count + count < goals.size() ? page + 1 : 0;
                    rebuildWidgets();
                });
        button("Simulate", 222, nav, Math.max(55, width - 234), this::simulate);
        button(
                "Apply progression",
                12,
                height - 26,
                half,
                () ->
                        act(
                                () -> {
                                    save.accept(profile);
                                    minecraft.gui.setScreen(parent);
                                }));
        button("Cancel", 15 + w / 2, height - 26, half, this::onClose);
    }

    private boolean has(String id) {
        return profile.growth().goals().stream().anyMatch(g -> g.id().equals(id));
    }

    private void button(String title, int x, int y, int w, Runnable action) {
        addRenderableWidget(
                Button.builder(Component.literal(title), b -> action.run())
                        .bounds(x, y, w, 20)
                        .build());
    }

    private void choice(String title, List<String> values, Consumer<String> action) {
        minecraft.gui.setScreen(
                new StudioChoiceScreen(
                        this,
                        title,
                        values,
                        v -> {
                            minecraft.gui.setScreen(this);
                            act(() -> action.accept(v));
                        }));
    }

    private void form(
            String title,
            String help,
            List<StudioFormScreen.Field> fields,
            Consumer<List<String>> action) {
        minecraft.gui.setScreen(new StudioFormScreen(this, title, help, fields, v -> "", action));
    }

    private static StudioFormScreen.Field f(String label, Object value) {
        return new StudioFormScreen.Field(label, value.toString());
    }

    private void replace(GrowthRules.Goal before, GrowthRules.Goal after, CellCampaign campaign) {
        var g = profile.growth();
        var values = new ArrayList<>(g.goals());
        values.removeIf(v -> v.id().equals(before.id()));
        if (after != null) values.add(after);
        var next =
                new CellWorldProfile(
                        profile.layout(),
                        profile.salt(),
                        profile.start(),
                        profile.tiles(),
                        profile.assignments(),
                        profile.border(),
                        profile.borderToFloor(),
                        new GrowthRules(
                                g.scope(),
                                g.radius(),
                                g.territories(),
                                g.blocksPerTick(),
                                g.chooseFrontier(),
                                values),
                        Optional.of(campaign));
        next.validate().getOrThrow();
        profile = next;
        status = "Progression draft updated.";
    }

    private void edit(GrowthRules.Goal goal, boolean fresh) {
        if (fresh) {
            replace(goal, goal, campaign());
            fresh = false;
        }
        choice(
                goal.title(),
                List.of(
                        "Title and trigger",
                        "Prerequisites",
                        "Rewards and cell groups",
                        "Item turn-in",
                        "Remove objective"),
                action -> {
                    switch (action) {
                        case "Title and trigger" ->
                                form(
                                        "Objective trigger",
                                        "Use advancement, signal, dimension_visit or site_discovery."
                                                + " A visit uses a dimension ID; discovery uses worldsunwritten:<marker>.",
                                        List.of(
                                                f("Title", goal.title()),
                                                f(
                                                        "Trigger",
                                                        goal.trigger().getSerializedName()),
                                                f("Source ID", goal.source())),
                                        v ->
                                                replace(
                                                        goal,
                                                        new GrowthRules.Goal(
                                                                goal.id(),
                                                                v.get(0),
                                                                GrowthRules.Trigger.valueOf(
                                                                        v.get(1)
                                                                                .toUpperCase(
                                                                                        Locale
                                                                                                .ROOT)),
                                                                Identifier.parse(v.get(2)),
                                                                goal.requires(),
                                                                goal.any(),
                                                                goal.targets(),
                                                                goal.credits()),
                                                        campaign()));
                        case "Prerequisites" ->
                                form(
                                        "Objective prerequisites",
                                        "Separate IDs with spaces. All links appear in the"
                                                + " dependency layers.",
                                        List.of(
                                                f(
                                                        "Required objective IDs",
                                                        String.join(" ", goal.requires())),
                                                f("Require any · true/false", goal.any())),
                                        v -> {
                                            if (!v.get(1).matches("true|false"))
                                                throw new IllegalArgumentException(
                                                        "Use true or false.");
                                            replace(
                                                    goal,
                                                    new GrowthRules.Goal(
                                                            goal.id(),
                                                            goal.title(),
                                                            goal.trigger(),
                                                            goal.source(),
                                                            v.get(0).isBlank()
                                                                    ? List.of()
                                                                    : List.of(
                                                                            v.get(0)
                                                                                    .trim()
                                                                                    .split("\\s+")),
                                                            Boolean.parseBoolean(v.get(1)),
                                                            goal.targets(),
                                                            goal.credits()),
                                                    campaign());
                                        });
                        case "Rewards and cell groups" ->
                                form(
                                        "Expansion rewards",
                                        "Targets accept q,r;q,r or @group. A reward contains at"
                                                + " most 64 cells and never removes terrain.",
                                        List.of(
                                                f("Credits · 0–64", goal.credits()),
                                                f(
                                                        "Cell coordinates or @group",
                                                        String.join(
                                                                ";",
                                                                goal.targets().stream()
                                                                        .map(Cell::key)
                                                                        .toList()))),
                                        v -> {
                                            List<Cell> targets =
                                                    v.get(1).isBlank()
                                                            ? List.of()
                                                            : v.get(1).startsWith("@")
                                                                    ? campaign().groups().stream()
                                                                            .filter(
                                                                                    g ->
                                                                                            g.id().equals(
                                                                                                            v.get(
                                                                                                                            1)
                                                                                                                    .substring(
                                                                                                                            1)))
                                                                            .findFirst()
                                                                            .orElseThrow(
                                                                                    () ->
                                                                                            new IllegalArgumentException(
                                                                                                    "Unknown"
                                                                                                        + " cell"
                                                                                                        + " group"))
                                                                            .cells()
                                                                    : Arrays.stream(
                                                                                    v.get(1)
                                                                                            .split(
                                                                                                    ";"))
                                                                            .map(String::trim)
                                                                            .map(Cell::parse)
                                                                            .toList();
                                            replace(
                                                    goal,
                                                    new GrowthRules.Goal(
                                                            goal.id(),
                                                            goal.title(),
                                                            goal.trigger(),
                                                            goal.source(),
                                                            goal.requires(),
                                                            goal.any(),
                                                            targets,
                                                            Integer.parseInt(v.get(0))),
                                                    campaign());
                                        });
                        case "Item turn-in" ->
                                form(
                                        "Item turn-in",
                                        "Only an explicit server-verified submission consumes"
                                            + " items. Quest signals cannot complete a delivery.",
                                        List.of(
                                                f(
                                                        "Item registry ID",
                                                        campaign().deliveries().stream()
                                                                .filter(
                                                                        d ->
                                                                                d.goal()
                                                                                        .equals(
                                                                                                goal
                                                                                                        .id()))
                                                                .map(d -> d.item().toString())
                                                                .findFirst()
                                                                .orElse("minecraft:iron_ingot")),
                                                f(
                                                        "Count · 1–4096",
                                                        campaign().deliveries().stream()
                                                                .filter(
                                                                        d ->
                                                                                d.goal()
                                                                                        .equals(
                                                                                                goal
                                                                                                        .id()))
                                                                .map(CellCampaign.Delivery::count)
                                                                .findFirst()
                                                                .orElse(16))),
                                        v -> {
                                            var delivery =
                                                    new CellCampaign.Delivery(
                                                            goal.id(),
                                                            Identifier.parse(v.get(0)),
                                                            Integer.parseInt(v.get(1)));
                                            var values = new ArrayList<>(campaign().deliveries());
                                            values.removeIf(d -> d.goal().equals(goal.id()));
                                            values.add(delivery);
                                            replace(
                                                    goal,
                                                    new GrowthRules.Goal(
                                                            goal.id(),
                                                            goal.title(),
                                                            GrowthRules.Trigger.SIGNAL,
                                                            delivery.signal(),
                                                            goal.requires(),
                                                            goal.any(),
                                                            goal.targets(),
                                                            goal.credits()),
                                                    campaign().withDeliveries(values));
                                        });
                        default -> {
                            var values =
                                    campaign().deliveries().stream()
                                            .filter(d -> !d.goal().equals(goal.id()))
                                            .toList();
                            replace(goal, null, campaign().withDeliveries(values));
                            minecraft.gui.setScreen(this);
                        }
                    }
                });
    }

    private void costs() {
        choice(
                "Expansion costs",
                profile.tiles().stream().map(CellWorldProfile.Tile::id).toList(),
                id ->
                        form(
                                "Cell expansion cost",
                                "Costs apply to credits spent on frontier choices. Fixed objective"
                                        + " rewards remain direct grants.",
                                List.of(
                                        f(
                                                id + " · 1–64 credits",
                                                campaign().costs().getOrDefault(id, 1))),
                                v -> {
                                    var values = new TreeMap<>(campaign().costs());
                                    values.put(id, Integer.parseInt(v.getFirst()));
                                    var next = profile.withCampaign(campaign().withCosts(values));
                                    next.validate().getOrThrow();
                                    profile = next;
                                }));
    }

    private void simulate() {
        form(
                "Simulate objectives",
                "List observed objective IDs. This is a detached graph check; it grants no terrain"
                        + " or items.",
                List.of(
                        f(
                                "Observed objective IDs",
                                String.join(
                                        " ",
                                        profile.growth().goals().stream()
                                                .map(GrowthRules.Goal::id)
                                                .toList()))),
                v -> {
                    var seen =
                            v.getFirst().isBlank()
                                    ? Set.<String>of()
                                    : Set.copyOf(List.of(v.getFirst().trim().split("\\s+")));
                    var done = ProgressionGraph.simulate(profile.growth(), seen);
                    status =
                            done.size()
                                    + " / "
                                    + profile.growth().goals().size()
                                    + " objectives reachable in this simulation.";
                });
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            status = Objects.toString(e.getMessage(), "Progression edit failed");
        }
    }

    @Override
    public void onClose() {
        if (!initial.equals(profile))
            minecraft.gui.setScreen(
                    new StudioConfirmScreen(
                            answer -> minecraft.gui.setScreen(answer ? parent : this),
                            Component.literal("Discard progression changes?"),
                            Component.literal(
                                    "The parent project keeps its last applied objectives."),
                            Component.literal("Discard changes"),
                            Component.literal("Keep editing")));
        else minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        super.extractRenderState(g, x, y, tick);
        for (var target : nodes) {
            var goal =
                    profile.growth().goals().stream()
                            .filter(v -> v.id().equals(target.id()))
                            .findFirst()
                            .orElseThrow();
            for (var source : nodes)
                if (source.x() < target.x() && goal.requires().contains(source.id())) {
                    int start = source.x() + source.width(),
                            end = target.x(),
                            middle = (start + end) / 2,
                            sy = source.y() + 10,
                            ty = target.y() + 10;
                    g.fill(start, sy, middle + 1, sy + 1, UnwrittenTheme.MUTED);
                    g.fill(
                            middle,
                            Math.min(sy, ty),
                            middle + 1,
                            Math.max(sy, ty) + 1,
                            UnwrittenTheme.MUTED);
                    g.fill(middle, ty, end, ty + 1, UnwrittenTheme.MUTED);
                    g.fill(end - 3, ty - 2, end, ty + 3, UnwrittenTheme.MUTED);
                }
        }
        g.centeredText(font, title, width / 2, 9, UnwrittenTheme.TEXT);
        g.text(
                font,
                "Dependency layer " + (layer + 1) + " · page " + (page + 1),
                12,
                55,
                UnwrittenTheme.MUTED);
        g.text(
                font,
                font.plainSubstrByWidth(status, width - 24),
                12,
                height - 48,
                UnwrittenTheme.MUTED);
    }
}

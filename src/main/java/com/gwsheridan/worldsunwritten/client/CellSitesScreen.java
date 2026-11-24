package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;
import com.gwsheridan.worldsunwritten.config.TerrainMaterial;
import com.gwsheridan.worldsunwritten.structure.ModularStructure;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.level.LevelHeightAccessor;

import java.util.*;
import java.util.function.Consumer;

final class CellSitesScreen extends Screen {
    private final Screen parent;
    private final CellWorldProfile initial;
    private CellGenerator generator;
    private final RegistryAccess registries;
    private final long seed;
    private final Consumer<CellWorldProfile> save;
    private String status = "Connection footprints remain reserved until both cells are ready.";

    CellSitesScreen(
            Screen parent,
            CellGenerator generator,
            RegistryAccess registries,
            long seed,
            Consumer<CellWorldProfile> save) {
        super(Component.literal("Connections and Adventure Sites"));
        this.parent = parent;
        this.initial = generator.cells();
        this.generator = generator;
        this.registries = registries;
        this.seed = seed;
        this.save = save;
    }

    private CellCampaign campaign() {
        return generator.cells().campaign().orElse(CellCampaign.EMPTY);
    }

    private void update(CellCampaign value) {
        var cells = generator.cells().withCampaign(value);
        cells.validate().getOrThrow();
        var next = new CellGenerator(generator.dimension(), cells);
        next.profileProblem()
                .ifPresent(
                        p -> {
                            throw new IllegalArgumentException(p);
                        });
        try {
            next.validateAssets(registries);
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
        generator = next;
        status = "Draft updated. Review placement before applying.";
    }

    @Override
    protected void init() {
        int w = Math.min(420, width - 24), x = (width - w) / 2;
        button("Add or edit connection", x, 44, w, this::connection);
        button("Add procedural site", x, 69, w, this::site);
        button("Remove definition", x, 94, w, this::remove);
        button("Review placement", x, 119, w, this::review);
        button(
                "Apply definitions",
                x,
                height - 26,
                (w - 6) / 2,
                () ->
                        act(
                                () -> {
                                    save.accept(generator.cells());
                                    minecraft.gui.setScreen(parent);
                                }));
        button("Cancel", x + (w + 6) / 2, height - 26, (w - 6) / 2, this::onClose);
    }

    private void button(String name, int x, int y, int w, Runnable action) {
        addRenderableWidget(
                Button.builder(Component.literal(name), b -> act(action))
                        .bounds(x, y, w, 20)
                        .build());
    }

    private void choice(String title, List<String> rows, Consumer<String> action) {
        minecraft.gui.setScreen(
                new StudioChoiceScreen(
                        this,
                        title,
                        rows,
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

    private static StudioFormScreen.Field f(String name, Object value) {
        return new StudioFormScreen.Field(name, value.toString());
    }

    private void connection() {
        var rows =
                new ArrayList<>(
                        campaign().connections().stream()
                                .map(CellCampaign.Connection::id)
                                .toList());
        rows.add("New connection");
        choice(
                "Connection",
                rows,
                id -> {
                    var old =
                            campaign().connections().stream()
                                    .filter(c -> c.id().equals(id))
                                    .findFirst();
                    var value =
                            old.orElse(
                                    new CellCampaign.Connection(
                                            "bridge",
                                            Cell.ORIGIN,
                                            new Cell(1, 0),
                                            "bridge",
                                            generator
                                                            .spawn(
                                                                    new CellGenerator.Key(
                                                                            0, Cell.ORIGIN),
                                                                    LevelHeightAccessor.create(
                                                                            generator.getMinY(),
                                                                            generator
                                                                                    .getGenDepth()))
                                                            .getY()
                                                    - 1,
                                            3,
                                            net.minecraft.world.level.block.Blocks.OAK_PLANKS
                                                    .defaultBlockState(),
                                            "",
                                            ""));
                    choice(
                            value.id(),
                            List.of("Geometry and material", "Template ports"),
                            part -> {
                                if (part.equals("Template ports"))
                                    form(
                                            "Template ports",
                                            "Use saved marker names for the two ends. Empty ports"
                                                    + " use cell centres and the configured floor.",
                                            List.of(
                                                    f("From port", value.fromPort()),
                                                    f("To port", value.toPort())),
                                            v ->
                                                    saveConnection(
                                                            value,
                                                            new CellCampaign.Connection(
                                                                    value.id(),
                                                                    value.from(),
                                                                    value.to(),
                                                                    value.kind(),
                                                                    value.floor(),
                                                                    value.width(),
                                                                    value.material(),
                                                                    v.get(0),
                                                                    v.get(1))));
                                else
                                    form(
                                            "Connection layout",
                                            "Only neighbouring cells can connect. Occupied terrain"
                                                + " and the native End reservation are preserved.",
                                            List.of(
                                                    f("ID", value.id()),
                                                    f("From q,r", value.from().key()),
                                                    f("To q,r", value.to().key()),
                                                    f("Kind: bridge / path / tunnel", value.kind()),
                                                    f("Floor Y", value.floor()),
                                                    f("Width · 1 / 3 / 5", value.width()),
                                                    f(
                                                            "Material",
                                                            net.minecraft.commands.arguments.blocks
                                                                    .BlockStateParser.serialize(
                                                                    value.material()))),
                                            v ->
                                                    saveConnection(
                                                            value,
                                                            new CellCampaign.Connection(
                                                                    v.get(0),
                                                                    Cell.parse(v.get(1)),
                                                                    Cell.parse(v.get(2)),
                                                                    v.get(3),
                                                                    Integer.parseInt(v.get(4)),
                                                                    Integer.parseInt(v.get(5)),
                                                                    TerrainMaterial.parse(v.get(6))
                                                                            .getOrThrow(),
                                                                    value.fromPort(),
                                                                    value.toPort())));
                            });
                });
    }

    private void saveConnection(CellCampaign.Connection before, CellCampaign.Connection next) {
        var values = new ArrayList<>(campaign().connections());
        values.removeIf(c -> c.id().equals(before.id()));
        values.add(next);
        update(campaign().withConnections(values));
    }

    private void site() {
        var available =
                registries
                        .lookupOrThrow(Registries.STRUCTURE)
                        .listElements()
                        .filter(h -> h.value() instanceof ModularStructure)
                        .map(h -> h.key().identifier().toString())
                        .sorted()
                        .toList();
        if (available.isEmpty())
            throw new IllegalArgumentException(
                    "Load an exported Assembly Studio datapack to make its modular structures"
                            + " available here.");
        choice(
                "Modular assembly",
                available,
                id ->
                        form(
                                "Cell adventure site",
                                "The complete sampled assembly must fit the cell. Decorative"
                                    + " metadata and markers are retained; containers are empty.",
                                List.of(
                                        f("Cell type", generator.cells().tiles().getFirst().id()),
                                        f("Chance · 1–100", 100)),
                                v -> {
                                    var structure =
                                            registries
                                                    .lookupOrThrow(Registries.STRUCTURE)
                                                    .getOrThrow(
                                                            ResourceKey.create(
                                                                    Registries.STRUCTURE,
                                                                    Identifier.parse(id)))
                                                    .value();
                                    var values = new ArrayList<>(campaign().sites());
                                    values.removeIf(s -> s.type().equals(v.get(0)));
                                    values.add(
                                            new CellCampaign.Site(
                                                    v.get(0),
                                                    structure,
                                                    Integer.parseInt(v.get(1))));
                                    update(campaign().withSites(values));
                                }));
    }

    private void remove() {
        var rows = new ArrayList<String>();
        campaign().connections().forEach(c -> rows.add("Connection: " + c.id()));
        campaign().sites().forEach(s -> rows.add("Site: " + s.type()));
        choice(
                "Remove definition",
                rows,
                id -> {
                    if (id.startsWith("Connection: "))
                        update(
                                campaign()
                                        .withConnections(
                                                campaign().connections().stream()
                                                        .filter(
                                                                c ->
                                                                        !c.id().equals(
                                                                                        id
                                                                                                .substring(
                                                                                                        12)))
                                                        .toList()));
                    else
                        update(
                                campaign()
                                        .withSites(
                                                campaign().sites().stream()
                                                        .filter(
                                                                s ->
                                                                        !s.type()
                                                                                .equals(
                                                                                        id
                                                                                                .substring(
                                                                                                        6)))
                                                        .toList()));
                });
    }

    private void review() {
        var rows = new ArrayList<String>();
        var height = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
        var occupied =
                new HashMap<
                        net.minecraft.core.BlockPos,
                        net.minecraft.world.level.block.state.BlockState>();
        for (var link : campaign().connections()) {
            var plan = CellConnectionPlan.create(generator, link, 0, height, seed);
            boolean overlap =
                    plan.blocks().entrySet().stream()
                            .anyMatch(
                                    e ->
                                            occupied.containsKey(e.getKey())
                                                    && occupied.get(e.getKey()) != e.getValue());
            occupied.putAll(plan.blocks());
            rows.add(
                    link.id()
                            + " · "
                            + (plan.problem().isEmpty() && !overlap
                                    ? "eligible · " + plan.blocks().size() + " reserved positions"
                                    : overlap
                                            ? "junction materials or headroom disagree"
                                            : plan.problem()));
        }
        for (var site : campaign().sites()) {
            int complete = 0;
            var reasons = new TreeSet<String>();
            for (int n = 0; n < 8; n++)
                try {
                    var sample =
                            com.gwsheridan.worldsunwritten.structure.CellSites.inspect(
                                    site.assembly(), seed + n);
                    if (sample.build().isEmpty()) {
                        reasons.add(sample.reason());
                        continue;
                    }
                    var size = sample.build().orElseThrow().size();
                    var layout = generator.cells().layout();
                    var centre = layout.centre(Cell.ORIGIN);
                    boolean fits = true;
                    for (int dx : new int[] {-size.getX() / 2, (size.getX() - 1) / 2})
                        for (int dz : new int[] {-size.getZ() / 2, (size.getZ() - 1) / 2})
                            fits &=
                                    layout.contains(
                                            Cell.ORIGIN,
                                            (int) Math.floor(centre.x()) + dx,
                                            (int) Math.floor(centre.z()) + dz);
                    if (fits) complete++;
                    else
                        reasons.add("Complete assembly does not fit this cell's geometry and gap.");
                } catch (java.io.IOException error) {
                    reasons.add(error.getMessage());
                }
            rows.add(
                    site.type()
                            + " · "
                            + complete
                            + " / 8 layouts fit · chance "
                            + site.chance()
                            + "%");
            reasons.forEach(reason -> rows.add("  " + reason));
        }
        minecraft.gui.setScreen(new StudioReportScreen(this, "Placement review", rows));
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            status = Objects.toString(e.getMessage(), "Definition edit failed");
        }
    }

    @Override
    public void onClose() {
        if (!initial.equals(generator.cells()))
            minecraft.gui.setScreen(
                    new StudioConfirmScreen(
                            answer -> minecraft.gui.setScreen(answer ? parent : this),
                            Component.literal("Discard site changes?"),
                            Component.literal(
                                    "The parent project keeps its last applied definitions."),
                            Component.literal("Discard changes"),
                            Component.literal("Keep editing")));
        else minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        super.extractRenderState(g, x, y, tick);
        g.centeredText(font, title, width / 2, 12, UnwrittenTheme.TEXT);
        g.text(
                font,
                font.plainSubstrByWidth(status, width - 24),
                12,
                height - 44,
                UnwrittenTheme.MUTED);
    }
}

package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.api.CellLayout;
import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.modpack.BundleManifest.Feature;
import com.gwsheridan.worldsunwritten.structure.IslandBuild;
import com.gwsheridan.worldsunwritten.worldgen.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.*;
import java.util.function.Consumer;

/**
 * Detached preset editing. Applying a revision never changes an already-created world's snapshot.
 */
final class CellStudioScreen extends Screen implements AuthoringContext {
    private final Screen parent;
    private final RegistryAccess registries;
    private final long seed;
    private final LevelStem stem;
    private final Consumer<CellGenerator> save;
    private CellGenerator generator;
    private String status = "Cell definitions become permanent when a world is created.";

    CellStudioScreen(
            Screen parent,
            RegistryAccess registries,
            LevelStem stem,
            long seed,
            Consumer<CellGenerator> save) {
        super(Component.literal("Cell World Studio"));
        this.parent = parent;
        this.registries = registries;
        this.stem = stem;
        this.generator = (CellGenerator) stem.generator();
        this.seed = seed;
        this.save = save;
    }

    @Override
    public String authoringKey() {
        return parent instanceof AuthoringContext a ? a.authoringKey() : "cell-world";
    }

    @Override
    protected void init() {
        int w = Math.min(430, width - 24), x = (width - w) / 2;
        int half = (w - 8) / 2;
        button("Geometry and borders", x, 43, half, this::geometry);
        button("Cell types and islands", x + half + 8, 43, half, this::types);
        button("Progression and ownership", x, 68, half, this::growth);
        button("Objectives and prerequisites", x + half + 8, 68, half, this::objectives);
        button("Fixed cell assignments", x, 93, half, this::assignments);
        button(
                "Preview saved layout",
                x + half + 8,
                93,
                half,
                () ->
                        GenerationPreview.forStem(new LevelStem(stem.type(), generator), seed)
                                .ifPresent(
                                        p ->
                                                minecraft.gui.setScreen(
                                                        new WorldPreviewScreen(
                                                                this,
                                                                Identifier.fromNamespaceAndPath(
                                                                        "worldsunwritten", "cells"),
                                                                seed,
                                                                p))));
        button(
                "World Layout Designer",
                x,
                118,
                w,
                () ->
                        minecraft.gui.setScreen(
                                new CellCanvasScreen(this, p(), seed, next -> update(next))));
        button(
                "Progression Designer",
                x,
                143,
                w,
                () ->
                        minecraft.gui.setScreen(
                                new ProgressionGraphScreen(this, p(), next -> update(next))));
        button(
                "Connections and adventure sites",
                x,
                168,
                stem.type().value().hasEnderDragonFight() ? half : w,
                () ->
                        minecraft.gui.setScreen(
                                new CellSitesScreen(
                                        this, generator, registries, seed, next -> update(next))));
        if (stem.type().value().hasEnderDragonFight())
            button("Shared End progression", x + half + 8, 168, half, this::nativeEnd);
        button(
                "Apply to project",
                x,
                height - 26,
                (w - 8) / 2,
                () ->
                        act(
                                () -> {
                                    generator
                                            .profileProblem()
                                            .ifPresent(
                                                    p -> {
                                                        throw new IllegalArgumentException(p);
                                                    });
                                    save.accept(generator);
                                    minecraft.gui.setScreen(parent);
                                }));
        button("Cancel", x + (w + 8) / 2, height - 26, (w - 8) / 2, this::onClose);
    }

    private void button(String title, int x, int y, int w, Runnable action) {
        addRenderableWidget(
                Button.builder(Component.literal(title), b -> action.run())
                        .bounds(x, y, w, 20)
                        .build());
    }

    private CellWorldProfile p() {
        return generator.cells();
    }

    private void update(CellWorldProfile next) {
        next.validate().getOrThrow();
        var candidate = new CellGenerator(generator.dimension(), next);
        if (next.progressive()
                && stem.type().value().hasEnderDragonFight()
                && !candidate.campaignEnd())
            throw new IllegalArgumentException(
                    "Choose Shared End progression to retain the native dragon fight.");
        candidate
                .profileProblem()
                .ifPresent(
                        problem -> {
                            throw new IllegalArgumentException(problem);
                        });
        generator = candidate;
        status = "Draft updated. Apply to project to include this revision.";
    }

    private void nativeEnd() {
        form(
                "Shared End progression",
                "The native arena and gateway islands stay available. Outer cells use shared"
                        + " objectives and saved expansion costs.",
                List.of(
                        field("Cell size · multiple of 4, up to 64", 64),
                        field("Outer territory radius · 1–15", 12)),
                v -> {
                    var old = p();
                    var g = old.growth();
                    var cells =
                            new CellWorldProfile(
                                    new CellLayout(
                                            old.layout().shape(),
                                            integer(v, 0),
                                            old.layout().gap(),
                                            old.layout().rim()),
                                    old.salt(),
                                    old.start(),
                                    old.tiles(),
                                    old.assignments(),
                                    old.border(),
                                    old.borderToFloor(),
                                    new GrowthRules(
                                            GrowthRules.Scope.SHARED,
                                            integer(v, 1),
                                            g.territories(),
                                            g.blocksPerTick(),
                                            g.chooseFrontier(),
                                            g.goals()),
                                    Optional.of(old.campaign().orElse(CellCampaign.EMPTY)));
                    cells.validate().getOrThrow();
                    var next =
                            new CellGenerator(
                                    generator
                                            .dimension()
                                            .withEndProgression(
                                                    Optional.of(
                                                            EndProgression.vanillaArena(
                                                                    registries))),
                                    cells);
                    next.profileProblem()
                            .ifPresent(
                                    p -> {
                                        throw new IllegalArgumentException(p);
                                    });
                    generator = next;
                    status = "Shared End draft configured; the native fight remains available.";
                });
    }

    private CellWorldProfile copy(
            CellLayout layout,
            String start,
            List<CellWorldProfile.Tile> tiles,
            List<CellWorldProfile.Assignment> assignments,
            GrowthRules growth) {
        return new CellWorldProfile(
                layout,
                p().salt(),
                start,
                tiles,
                assignments,
                p().border(),
                p().borderToFloor(),
                growth,
                p().campaign());
    }

    private void geometry() {
        var p = p();
        var l = p.layout();
        form(
                "Geometry and borders",
                "Square size 16 is one Minecraft chunk. Hex size is the centre-to-corner radius."
                        + " Gaps remain void.",
                List.of(
                        field(
                                "Shape: square / pointy_hex / flat_hex",
                                l.shape().getSerializedName()),
                        field("Size · multiple of 4", l.size()),
                        field("Void gap", l.gap()),
                        field("Rim width", l.rim()),
                        field("Seed salt", p.salt()),
                        field(
                                "Border block state",
                                net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(
                                        p.border())),
                        field("Border to floor · true/false", p.borderToFloor())),
                v -> {
                    var state = TerrainMaterial.parse(v.get(5)).getOrThrow();
                    update(
                            new CellWorldProfile(
                                    new CellLayout(
                                            CellLayout.Shape.valueOf(
                                                    v.get(0).toUpperCase(Locale.ROOT)),
                                            integer(v, 1),
                                            integer(v, 2),
                                            integer(v, 3)),
                                    Long.parseLong(v.get(4)),
                                    p.start(),
                                    p.tiles(),
                                    p.assignments(),
                                    state,
                                    bool(v.get(6)),
                                    p.growth(),
                                    p.campaign()));
                });
    }

    private void types() {
        choose(
                "Cell types",
                concat(p().tiles().stream().map(CellWorldProfile.Tile::id).toList(), "Add type"),
                id -> {
                    if (id.equals("Add type")) {
                        if (p().tiles().size() >= 16)
                            throw new IllegalArgumentException("At most 16 cell types.");
                        var base = p().tiles().getFirst();
                        editType(
                                new CellWorldProfile.Tile(
                                        "custom",
                                        "Custom terrain",
                                        1,
                                        base.biome(),
                                        CellWorldProfile.Kind.TERRAIN,
                                        base.terrain(),
                                        base.content(),
                                        Optional.empty()),
                                false);
                    } else {
                        var tile =
                                p().tiles().stream()
                                        .filter(t -> t.id().equals(id))
                                        .findFirst()
                                        .orElseThrow();
                        choose(
                                tile.title(),
                                List.of(
                                        "Details and biome",
                                        "Terrain and layers",
                                        "Ores, caves and fluids",
                                        "Edit embedded island",
                                        "Use as starting type",
                                        "Remove type"),
                                action -> {
                                    switch (action) {
                                        case "Details and biome" -> editType(tile, true);
                                        case "Terrain and layers" ->
                                                minecraft.gui.setScreen(
                                                        new LayeredScreen(
                                                                this,
                                                                delegate(tile),
                                                                registries,
                                                                () -> seed,
                                                                edited ->
                                                                        replace(
                                                                                tile,
                                                                                new CellWorldProfile
                                                                                        .Tile(
                                                                                        tile.id(),
                                                                                        tile
                                                                                                .title(),
                                                                                        tile
                                                                                                .weight(),
                                                                                        tile
                                                                                                .biome(),
                                                                                        tile.kind(),
                                                                                        edited
                                                                                                .profile(),
                                                                                        tile
                                                                                                .content(),
                                                                                        tile
                                                                                                .template()),
                                                                                true),
                                                                true));
                                        case "Ores, caves and fluids" ->
                                                minecraft.gui.setScreen(
                                                        new DimensionFeaturesScreen(
                                                                this,
                                                                Component.literal(tile.title()),
                                                                delegate(tile),
                                                                delegate(tile)
                                                                        .dimensionProfile()
                                                                        .orElseThrow(),
                                                                registries,
                                                                seed,
                                                                f ->
                                                                        f != Feature.STRUCTURES
                                                                                && f
                                                                                        != Feature
                                                                                                .NATIVE_FEATURES
                                                                                && f
                                                                                        != Feature
                                                                                                .BIOMES,
                                                                edited ->
                                                                        replace(
                                                                                tile,
                                                                                new CellWorldProfile
                                                                                        .Tile(
                                                                                        tile.id(),
                                                                                        tile
                                                                                                .title(),
                                                                                        tile
                                                                                                .weight(),
                                                                                        tile
                                                                                                .biome(),
                                                                                        tile.kind(),
                                                                                        tile
                                                                                                .terrain(),
                                                                                        edited
                                                                                                .content(),
                                                                                        tile
                                                                                                .template()),
                                                                                true)));
                                        case "Edit embedded island" ->
                                                minecraft.gui.setScreen(
                                                        new IslandStudioScreen(
                                                                this,
                                                                registries,
                                                                tile.template()
                                                                        .orElseGet(
                                                                                IslandBuild
                                                                                        ::classic),
                                                                build ->
                                                                        replace(
                                                                                tile,
                                                                                new CellWorldProfile
                                                                                        .Tile(
                                                                                        tile.id(),
                                                                                        tile
                                                                                                .title(),
                                                                                        tile
                                                                                                .weight(),
                                                                                        tile
                                                                                                .biome(),
                                                                                        tile.kind(),
                                                                                        tile
                                                                                                .terrain(),
                                                                                        tile
                                                                                                .content(),
                                                                                        Optional.of(
                                                                                                build)),
                                                                                true)));
                                        case "Use as starting type" -> {
                                            update(
                                                    copy(
                                                            p().layout(),
                                                            tile.id(),
                                                            p().tiles(),
                                                            p().assignments(),
                                                            p().growth()));
                                            minecraft.gui.setScreen(this);
                                        }
                                        default -> {
                                            if (tile.id().equals(p().start())
                                                    || p().assignments().stream()
                                                            .anyMatch(
                                                                    a ->
                                                                            a.type()
                                                                                    .equals(
                                                                                            tile
                                                                                                    .id())))
                                                throw new IllegalArgumentException(
                                                        "Choose another start and remove fixed"
                                                                + " assignments first.");
                                            update(
                                                    copy(
                                                            p().layout(),
                                                            p().start(),
                                                            p().tiles().stream()
                                                                    .filter(
                                                                            t ->
                                                                                    !t.id().equals(
                                                                                                    tile
                                                                                                            .id()))
                                                                    .toList(),
                                                            p().assignments(),
                                                            p().growth()));
                                            minecraft.gui.setScreen(this);
                                        }
                                    }
                                });
                    }
                });
    }

    private LayeredGenerator delegate(CellWorldProfile.Tile tile) {
        return new LayeredGenerator(
                tile.biome(),
                tile.terrain(),
                Optional.of(
                        new DimensionProfile(
                                        generator.getMinY(),
                                        generator.getGenDepth(),
                                        Optional.empty())
                                .withContent(tile.content())));
    }

    private void editType(CellWorldProfile.Tile tile, boolean existing) {
        form(
                "Cell type",
                "Island cells contain only the embedded template. Cavern cells add a safe starting"
                        + " chamber.",
                List.of(
                        field("Type ID", tile.id()),
                        field("Title", tile.title()),
                        field("Weight · 0–100 (0 = assigned only)", tile.weight()),
                        field(
                                "Biome registry ID",
                                tile.biome().unwrapKey().orElseThrow().identifier()),
                        field("Kind: terrain / island / cavern", tile.kind().getSerializedName())),
                v -> {
                    var kind = CellWorldProfile.Kind.valueOf(v.get(4).toUpperCase(Locale.ROOT));
                    var biome =
                            registries
                                    .lookupOrThrow(Registries.BIOME)
                                    .getOrThrow(
                                            ResourceKey.create(
                                                    Registries.BIOME, Identifier.parse(v.get(3))));
                    replace(
                            tile,
                            new CellWorldProfile.Tile(
                                    v.get(0),
                                    v.get(1),
                                    integer(v, 2),
                                    biome,
                                    kind,
                                    tile.terrain(),
                                    tile.content(),
                                    kind == CellWorldProfile.Kind.ISLAND
                                            ? Optional.of(
                                                    tile.template().orElseGet(IslandBuild::classic))
                                            : tile.template()),
                            existing);
                });
    }

    private void replace(CellWorldProfile.Tile old, CellWorldProfile.Tile next, boolean existing) {
        var types = new ArrayList<>(p().tiles());
        if (existing) types.removeIf(t -> t.id().equals(old.id()));
        types.add(next);
        var assignments =
                p().assignments().stream()
                        .map(
                                a ->
                                        a.type().equals(old.id())
                                                ? new CellWorldProfile.Assignment(
                                                        a.cell(), next.id())
                                                : a)
                        .toList();
        update(
                copy(
                        p().layout(),
                        p().start().equals(old.id()) ? next.id() : p().start(),
                        types,
                        assignments,
                        p().growth()));
    }

    private void growth() {
        var g = p().growth();
        form(
                "Progression and ownership",
                "Shared uses one physical territory. Player and team modes allocate separate saved"
                        + " regions. Static has no objectives.",
                List.of(
                        field(
                                "Scope: static / shared / player / team",
                                g.scope().getSerializedName()),
                        field("Territory radius · 1–15 cells", g.radius()),
                        field("Personal/team capacity · 1–255", g.territories()),
                        field("Activation positions per tick · 256–16384", g.blocksPerTick()),
                        field("Players choose frontier · true/false", g.chooseFrontier())),
                v -> {
                    var scope = GrowthRules.Scope.valueOf(v.get(0).toUpperCase(Locale.ROOT));
                    update(
                            copy(
                                    p().layout(),
                                    p().start(),
                                    p().tiles(),
                                    p().assignments(),
                                    new GrowthRules(
                                            scope,
                                            integer(v, 1),
                                            integer(v, 2),
                                            integer(v, 3),
                                            bool(v.get(4)),
                                            scope == GrowthRules.Scope.STATIC
                                                    ? List.of()
                                                    : g.goals())));
                });
    }

    private void objectives() {
        var labels =
                new ArrayList<>(p().growth().goals().stream().map(GrowthRules.Goal::id).toList());
        labels.add("Add objective");
        labels.add("Dependency report");
        choose(
                "Objectives",
                labels,
                id -> {
                    if (id.equals("Dependency report")) {
                        minecraft.gui.setScreen(
                                new StudioReportScreen(
                                        this,
                                        "Progression dependencies",
                                        p().growth().goals().stream()
                                                .map(
                                                        g ->
                                                                g.id()
                                                                        + " ← "
                                                                        + (g.requires().isEmpty()
                                                                                ? "no prerequisites"
                                                                                : (g.any()
                                                                                                ? "any "
                                                                                                : "all ")
                                                                                        + String
                                                                                                .join(
                                                                                                        ", ",
                                                                                                        g
                                                                                                                .requires()))
                                                                        + " · "
                                                                        + g.trigger()
                                                                                .getSerializedName()
                                                                        + " "
                                                                        + g.source()
                                                                        + " · "
                                                                        + g.credits()
                                                                        + " credits, "
                                                                        + g.targets().size()
                                                                        + " fixed cells")
                                                .toList()));
                        return;
                    }
                    var goal =
                            id.equals("Add objective")
                                    ? new GrowthRules.Goal(
                                            "custom",
                                            "Custom objective",
                                            GrowthRules.Trigger.SIGNAL,
                                            Identifier.fromNamespaceAndPath("modpack", "objective"),
                                            List.of(),
                                            false,
                                            List.of(),
                                            1)
                                    : p().growth().goals().stream()
                                            .filter(g -> g.id().equals(id))
                                            .findFirst()
                                            .orElseThrow();
                    choose(
                            goal.title(),
                            List.of(
                                    "Details and trigger",
                                    "Prerequisites",
                                    "Terrain rewards",
                                    "Delete objective"),
                            action -> {
                                switch (action) {
                                    case "Details and trigger" ->
                                            form(
                                                    "Objective details",
                                                    "Triggers: advancement, signal, dimension_visit, site_discovery."
                                                        + " For a site use worldsunwritten:<marker>.",
                                                    List.of(
                                                            field("Objective ID", goal.id()),
                                                            field("Title", goal.title()),
                                                            field(
                                                                    "Trigger",
                                                                    goal.trigger()
                                                                            .getSerializedName()),
                                                            field(
                                                                    "Trigger source ID",
                                                                    goal.source())),
                                                    v ->
                                                            goal(
                                                                    goal,
                                                                    new GrowthRules.Goal(
                                                                            v.get(0),
                                                                            v.get(1),
                                                                            GrowthRules.Trigger
                                                                                    .valueOf(
                                                                                            v.get(2)
                                                                                                    .toUpperCase(
                                                                                                            Locale
                                                                                                                    .ROOT)),
                                                                            Identifier.parse(
                                                                                    v.get(3)),
                                                                            goal.requires(),
                                                                            goal.any(),
                                                                            goal.targets(),
                                                                            goal.credits())));
                                    case "Prerequisites" ->
                                            form(
                                                    "Objective prerequisites",
                                                    "Separate objective IDs with spaces. A cycle or"
                                                        + " unknown prerequisite prevents saving.",
                                                    List.of(
                                                            field(
                                                                    "Required objective IDs",
                                                                    String.join(
                                                                            " ", goal.requires())),
                                                            field(
                                                                    "Require any · true/false",
                                                                    goal.any())),
                                                    v ->
                                                            goal(
                                                                    goal,
                                                                    new GrowthRules.Goal(
                                                                            goal.id(),
                                                                            goal.title(),
                                                                            goal.trigger(),
                                                                            goal.source(),
                                                                            words(v.get(0)),
                                                                            bool(v.get(1)),
                                                                            goal.targets(),
                                                                            goal.credits())));
                                    case "Terrain rewards" ->
                                            form(
                                                    "Terrain rewards",
                                                    "Credits open neighbouring cells. Fixed rewards"
                                                            + " use q,r coordinates separated by"
                                                            + " semicolons. Rewards never remove"
                                                            + " terrain.",
                                                    List.of(
                                                            field(
                                                                    "Expansion credits · 0–64",
                                                                    goal.credits()),
                                                            field(
                                                                    "Fixed cell coordinates",
                                                                    String.join(
                                                                            ";",
                                                                            goal.targets().stream()
                                                                                    .map(Cell::key)
                                                                                    .toList()))),
                                                    v ->
                                                            goal(
                                                                    goal,
                                                                    new GrowthRules.Goal(
                                                                            goal.id(),
                                                                            goal.title(),
                                                                            goal.trigger(),
                                                                            goal.source(),
                                                                            goal.requires(),
                                                                            goal.any(),
                                                                            v.get(1).isBlank()
                                                                                    ? List.of()
                                                                                    : Arrays.stream(
                                                                                                    v.get(
                                                                                                                    1)
                                                                                                            .split(
                                                                                                                    ";"))
                                                                                            .map(
                                                                                                    String
                                                                                                            ::trim)
                                                                                            .map(
                                                                                                    Cell
                                                                                                            ::parse)
                                                                                            .toList(),
                                                                            integer(v, 0))));
                                    default -> {
                                        goal(goal, null);
                                        minecraft.gui.setScreen(this);
                                    }
                                }
                            });
                });
    }

    private void goal(GrowthRules.Goal previous, GrowthRules.Goal next) {
        var goals = new ArrayList<>(p().growth().goals());
        goals.removeIf(g -> g.id().equals(previous.id()));
        if (next != null) {
            if (goals.stream().anyMatch(g -> g.id().equals(next.id())))
                throw new IllegalArgumentException("Objective ID already exists.");
            goals.add(next);
        }
        var g = p().growth();
        update(
                copy(
                        p().layout(),
                        p().start(),
                        p().tiles(),
                        p().assignments(),
                        new GrowthRules(
                                g.scope(),
                                g.radius(),
                                g.territories(),
                                g.blocksPerTick(),
                                g.chooseFrontier(),
                                goals)));
    }

    private void assignments() {
        form(
                "Fixed cell assignments",
                "Enter q,r=type entries separated by semicolons. Everything else uses seed-stable"
                        + " weights. Origin uses the starting type.",
                List.of(
                        field(
                                "Assignments",
                                String.join(
                                        ";",
                                        p().assignments().stream()
                                                .map(a -> a.cell().key() + "=" + a.type())
                                                .toList()))),
                v -> {
                    var values = new ArrayList<CellWorldProfile.Assignment>();
                    if (!v.getFirst().isBlank())
                        for (var entry : v.getFirst().split(";")) {
                            var parts = entry.trim().split("=", -1);
                            if (parts.length != 2)
                                throw new IllegalArgumentException("Use q,r=type.");
                            values.add(
                                    new CellWorldProfile.Assignment(
                                            Cell.parse(parts[0]), parts[1]));
                        }
                    update(copy(p().layout(), p().start(), p().tiles(), values, p().growth()));
                });
    }

    private void form(
            String title,
            String help,
            List<StudioFormScreen.Field> fields,
            Consumer<List<String>> action) {
        minecraft.gui.setScreen(
                new StudioFormScreen(this, title, help, fields, v -> "", v -> action.accept(v)));
    }

    private void choose(String title, List<String> values, Consumer<String> action) {
        minecraft.gui.setScreen(
                new StudioChoiceScreen(
                        this, title, values, value -> act(() -> action.accept(value))));
    }

    private static StudioFormScreen.Field field(String label, Object value) {
        return new StudioFormScreen.Field(label, value.toString());
    }

    private static int integer(List<String> values, int i) {
        return Integer.parseInt(values.get(i));
    }

    private static boolean bool(String value) {
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalArgumentException("Use true or false.");
        return Boolean.parseBoolean(value);
    }

    private static List<String> words(String value) {
        return value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
    }

    private static List<String> concat(List<String> values, String value) {
        var list = new ArrayList<>(values);
        list.add(value);
        return list;
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            status = Objects.toString(error.getMessage(), "Cell edit failed");
            if (minecraft.gui.screen() instanceof StudioChoiceScreen choice)
                choice.showError(status);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        super.extractRenderState(g, x, y, tick);
        g.centeredText(font, title, width / 2, 10, UnwrittenTheme.TEXT);
        g.text(
                font,
                font.plainSubstrByWidth(status, width - 30),
                15,
                height - 44,
                UnwrittenTheme.MUTED);
    }
}

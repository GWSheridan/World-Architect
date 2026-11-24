package com.gwsheridan.worldsunwritten.client;

import com.google.gson.JsonElement;
import com.gwsheridan.worldsunwritten.authoring.*;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.dimension.DimensionCatalog;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.gwsheridan.worldsunwritten.spawn.*;
import com.gwsheridan.worldsunwritten.structure.IslandBuild;
import com.gwsheridan.worldsunwritten.worldgen.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.*;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;

/** Guided detached project editing; every saved/exported revision uses production validation. */
final class ModpackBuilderScreen extends Screen implements AuthoringContext, ArchitectPanelLayout {
    private static final List<String> STEPS =
            List.of(
                    "Project",
                    "Dimensions",
                    "Islands",
                    "Server policies",
                    "Locks",
                    "Review / export");
    private final Screen parent;
    private final RegistryAccess registries;
    private final BiConsumer<String, PresetStudio.Draft> use;
    private final ClientDraftJournal journal;
    private PresetStudio.Draft draft;
    private String name, status = "Start with a recipe or customize your current world settings.";
    private ResourceKey<LevelStem> selected = LevelStem.OVERWORLD;
    private int step, left, span, contentX, contentWidth, navWidth;
    private boolean applied;
    private final JsonElement initial;

    ModpackBuilderScreen(
            Screen parent,
            RegistryAccess registries,
            String name,
            PresetStudio.Draft draft,
            BiConsumer<String, PresetStudio.Draft> use) {
        super(Component.literal("Modpack Builder"));
        this.parent = parent;
        this.registries = registries;
        this.name = name;
        this.draft = draft;
        this.use = use;
        initial = snapshot();
        journal = new ClientDraftJournal("modpack-builder:" + draft.manifest().preset(), initial);
    }

    @Override
    public String authoringKey() {
        return "modpack-builder:" + draft.manifest().preset();
    }

    private JsonElement snapshot() {
        return PackedDraft.pack(PresetDraftDocument.encode(name, draft));
    }

    private void restore(JsonElement json) {
        try {
            var decoded = PackedDraft.unpack(json);
            var next =
                    PresetStudio.validate(PresetDraftDocument.decode(decoded), registries).draft();
            String restored = PresetDraftDocument.name(decoded);
            PackedDraft.pack(PresetDraftDocument.encode(restored, next));
            name = restored;
            draft = next;
        } catch (IOException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
    }

    private PresetStudio.Checked checked() throws IOException {
        return PresetStudio.validate(draft, registries);
    }

    private void update(PresetStudio.Draft next) throws IOException {
        update(name, next);
    }

    private void update(String nextName, PresetStudio.Draft next) throws IOException {
        var validated = PresetStudio.validate(next, registries).draft();
        var document = PackedDraft.pack(PresetDraftDocument.encode(nextName, validated));
        draft = validated;
        name = nextName;
        journal.observe(document);
        status = "Project updated. Export creates a new immutable revision.";
    }

    private boolean canDimension(ResourceKey<LevelStem> key) {
        return draft.manifest()
                .inheritance()
                .map(
                        i ->
                                i.policy().lock() == BundleManifest.Lock.UNLOCKED
                                        || i.policy().dimensions().contains(key.identifier()))
                .orElse(true);
    }

    private boolean canFeature(ResourceKey<LevelStem> key, BundleManifest.Feature feature) {
        return canDimension(key)
                || draft.manifest()
                        .inheritance()
                        .map(
                                i ->
                                        i.policy()
                                                .features()
                                                .getOrDefault(key.identifier(), List.of())
                                                .contains(feature))
                        .orElse(true);
    }

    @Override
    protected void init() {
        span = Math.min(780, width - 24);
        left = (width - span) / 2;
        navWidth = Math.max(104, Math.min(164, span / 4));
        contentX = left + navWidth + 16;
        contentWidth = span - navWidth - 16;
        journal.observe(snapshot());
        for (int i = 0; i < STEPS.size(); i++) {
            int next = i;
            button(
                    (i == step ? "› " : "") + STEPS.get(i),
                    left,
                    38 + i * 25,
                    navWidth,
                    () -> {
                        step = next;
                        rebuildWidgets();
                    });
        }
        switch (step) {
            case 0 -> project();
            case 1 -> dimensions();
            case 2 -> islands();
            case 3 -> policies();
            case 4 -> locks();
            case 5 -> review();
            default -> throw new IllegalStateException();
        }
        int half = (span - 8) / 2;
        button(
                "Use project",
                left,
                height - 26,
                half,
                () ->
                        act(
                                () -> {
                                    if (journal.pending())
                                        throw new IOException("Resolve the recovery draft first.");
                                    var value = checked().draft();
                                    use.accept(name, value);
                                    applied = true;
                                    journal.close(this, () -> minecraft.gui.setScreen(parent));
                                }));
        button("Cancel", left + half + 8, height - 26, half, this::onClose);
        if (journal.offer()) history();
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        var button =
                addRenderableWidget(
                        Button.builder(Component.literal(label), b -> action.run())
                                .bounds(x, y, w, 20)
                                .build());
        if (font.width(label) > w - 12)
            button.setTooltip(
                    net.minecraft.client.gui.components.Tooltip.create(Component.literal(label)));
        return button;
    }

    private Button action(String label, int row, Runnable run) {
        return button(label, contentX, 61 + row * 25, contentWidth, run);
    }

    private void project() {
        int art = contentWidth >= 340 && height >= 310 ? Math.min(180, contentWidth / 2) : 0;
        int controls = contentWidth - (art > 0 ? art + 12 : 0);
        button("Choose starter", contentX, 61, controls, this::starter);
        button("Project details", contentX, 86, controls, this::metadata);
        button("World defaults", contentX, 111, controls, this::worldDefaults);
        button("Saved presets / parents", contentX, 136, controls, this::library);
        button("History / recovery", contentX, 161, controls, this::history);
    }

    private void starter() {
        minecraft.gui.setScreen(
                new StudioChoiceScreen(
                        this,
                        "Choose a project starter",
                        java.util.stream.Stream.concat(
                                        java.util.stream.Stream.concat(
                                                java.util.stream.Stream.of(
                                                        "Shared Skyblock",
                                                        "Personal islands",
                                                        "Team islands",
                                                        "Empty void",
                                                        "Mixed dimensions"),
                                                com
                                                        .gwsheridan
                                                        .worldsunwritten
                                                        .cells
                                                        .CellRecipes
                                                        .NAMES
                                                        .stream()),
                                        com.gwsheridan.worldsunwritten.cells.CampaignRecipes.NAMES
                                                .stream())
                                .toList(),
                        style -> {
                            Runnable apply =
                                    () ->
                                            act(
                                                    () -> {
                                                        update(
                                                                ModpackProject.starter(
                                                                        draft, style, registries));
                                                        step =
                                                                com.gwsheridan.worldsunwritten.cells
                                                                                        .CellRecipes
                                                                                        .NAMES
                                                                                        .contains(
                                                                                                style)
                                                                                || com.gwsheridan
                                                                                        .worldsunwritten
                                                                                        .cells
                                                                                        .CampaignRecipes
                                                                                        .NAMES
                                                                                        .contains(
                                                                                                style)
                                                                        ? 1
                                                                        : 2;
                                                        minecraft.gui.setScreen(this);
                                                    });
                            if (style.equals("Empty void"))
                                minecraft.gui.setScreen(
                                        new StudioConfirmScreen(
                                                yes -> {
                                                    if (yes) apply.run();
                                                    else minecraft.gui.setScreen(this);
                                                },
                                                Component.literal("Create an empty Overworld?"),
                                                Component.literal(
                                                        "There is no safe spawn. Add an island"
                                                            + " before distributing the pack, or"
                                                            + " deliberately accept an"
                                                            + " uninhabitable world."),
                                                Component.literal("Use empty void"),
                                                Component.literal("Keep editing")));
                            else apply.run();
                        }));
    }

    private void metadata() {
        var m = draft.manifest();
        minecraft.gui.setScreen(
                new StudioFormScreen(
                        this,
                        "Project details",
                        "Give each changed preset a new release. The project name identifies its"
                                + " local revision library.",
                        List.of(
                                new StudioFormScreen.Field("Project name", name),
                                new StudioFormScreen.Field("Preset ID", m.preset().toString()),
                                new StudioFormScreen.Field("Title", m.title()),
                                new StudioFormScreen.Field("Author", m.author()),
                                new StudioFormScreen.Field("Release", m.release()),
                                new StudioFormScreen.Field("Description", m.description())),
                        v ->
                                v.getFirst().matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")
                                        ? ""
                                        : "Choose a simple project name.",
                        v ->
                                act(
                                        () -> {
                                            var next =
                                                    new BundleManifest(
                                                            Identifier.parse(v.get(1)),
                                                            v.get(2),
                                                            v.get(5),
                                                            v.get(3),
                                                            v.get(4),
                                                            m.lock(),
                                                            m.options(),
                                                            m.editableDimensions(),
                                                            m.editableFeatures(),
                                                            m.editableOptions(),
                                                            m.requiredMods(),
                                                            m.allowStructureLibrary(),
                                                            m.inheritance(),
                                                            m.pack());
                                            update(v.getFirst(), draft.withManifest(next));
                                        })));
    }

    private void worldDefaults() {
        var m = draft.manifest();
        minecraft.gui.setScreen(
                new StudioFormScreen(
                        this,
                        "World defaults",
                        "These defaults apply to new worlds. Existing saves retain their original"
                                + " generation settings.",
                        List.of(
                                new StudioFormScreen.Field(
                                        "Seed", Long.toString(m.options().seed())),
                                new StudioFormScreen.Field(
                                        "Generate structures · true/false",
                                        Boolean.toString(m.options().generateStructures())),
                                new StudioFormScreen.Field(
                                        "Bonus chest · true/false",
                                        Boolean.toString(m.options().generateBonusChest()))),
                        v -> {
                            Long.parseLong(v.getFirst());
                            return bool(v.get(1)) && bool(v.get(2))
                                    ? ""
                                    : "Use true or false for the toggles.";
                        },
                        v ->
                                act(
                                        () ->
                                                update(
                                                        draft.withManifest(
                                                                new BundleManifest(
                                                                        m.preset(),
                                                                        m.title(),
                                                                        m.description(),
                                                                        m.author(),
                                                                        m.release(),
                                                                        m.lock(),
                                                                        new WorldOptions(
                                                                                Long.parseLong(
                                                                                        v
                                                                                                .getFirst()),
                                                                                Boolean
                                                                                        .parseBoolean(
                                                                                                v
                                                                                                        .get(
                                                                                                                1)),
                                                                                Boolean
                                                                                        .parseBoolean(
                                                                                                v
                                                                                                        .get(
                                                                                                                2))),
                                                                        m.editableDimensions(),
                                                                        m.editableFeatures(),
                                                                        m.editableOptions(),
                                                                        m.requiredMods(),
                                                                        m.allowStructureLibrary(),
                                                                        m.inheritance(),
                                                                        m.pack()))))));
    }

    private void library() {
        act(
                () -> {
                    var revisions = PresetStudio.list(ModpackRuntime.directory());
                    minecraft.gui.setScreen(
                            new PresetLibraryScreen(
                                    this,
                                    revisions,
                                    revision -> {
                                        minecraft.gui.setScreen(
                                                new StudioChoiceScreen(
                                                        this,
                                                        revision.label(),
                                                        List.of(
                                                                "Load saved preset",
                                                                "Derive a pinned child"),
                                                        choice ->
                                                                act(
                                                                        () -> {
                                                                            if (choice.startsWith(
                                                                                    "Load")) {
                                                                                Runnable load =
                                                                                        () ->
                                                                                                act(
                                                                                                        () -> {
                                                                                                            var
                                                                                                                    loaded =
                                                                                                                            PresetStudio
                                                                                                                                    .validate(
                                                                                                                                            PresetStudio
                                                                                                                                                    .load(
                                                                                                                                                            ModpackRuntime
                                                                                                                                                                    .directory(),
                                                                                                                                                            revision
                                                                                                                                                                    .name(),
                                                                                                                                                            revision
                                                                                                                                                                    .release()),
                                                                                                                                            registries)
                                                                                                                                    .draft();
                                                                                                            update(
                                                                                                                    revision
                                                                                                                            .name(),
                                                                                                                    loaded);
                                                                                                            minecraft
                                                                                                                    .gui
                                                                                                                    .setScreen(
                                                                                                                            this);
                                                                                                        });
                                                                                if (!initial.equals(
                                                                                        snapshot()))
                                                                                    minecraft.gui
                                                                                            .setScreen(
                                                                                                    new StudioConfirmScreen(
                                                                                                            yes -> {
                                                                                                                if (yes)
                                                                                                                    load
                                                                                                                            .run();
                                                                                                                else
                                                                                                                    minecraft
                                                                                                                            .gui
                                                                                                                            .setScreen(
                                                                                                                                    this);
                                                                                                            },
                                                                                                            Component
                                                                                                                    .literal(
                                                                                                                            "Replace"
                                                                                                                                + " project"
                                                                                                                                + " draft?"),
                                                                                                            Component
                                                                                                                    .literal(
                                                                                                                            "Unsaved"
                                                                                                                                + " project"
                                                                                                                                + " changes"
                                                                                                                                + " will"
                                                                                                                                + " be replaced"
                                                                                                                                + " by the"
                                                                                                                                + " selected"
                                                                                                                                + " revision."),
                                                                                                            Component
                                                                                                                    .literal(
                                                                                                                            "Load revision"),
                                                                                                            Component
                                                                                                                    .literal(
                                                                                                                            "Keep editing")));
                                                                                else load.run();
                                                                            } else
                                                                                minecraft.gui
                                                                                        .setScreen(
                                                                                                new StudioFormScreen(
                                                                                                        this,
                                                                                                        "Derive"
                                                                                                            + " a child"
                                                                                                            + " preset",
                                                                                                        "The exact"
                                                                                                            + " parent"
                                                                                                            + " revision"
                                                                                                            + " is pinned."
                                                                                                            + " Exported"
                                                                                                            + " children"
                                                                                                            + " include"
                                                                                                            + " all their"
                                                                                                            + " resources.",
                                                                                                        List
                                                                                                                .of(
                                                                                                                        new StudioFormScreen
                                                                                                                                .Field(
                                                                                                                                "Child"
                                                                                                                                    + " name",
                                                                                                                                revision
                                                                                                                                                .name()
                                                                                                                                        + "-child"),
                                                                                                                        new StudioFormScreen
                                                                                                                                .Field(
                                                                                                                                "Release",
                                                                                                                                "1.0.0")),
                                                                                                        v ->
                                                                                                                "",
                                                                                                        v ->
                                                                                                                act(
                                                                                                                        () -> {
                                                                                                                            update(
                                                                                                                                    v
                                                                                                                                            .getFirst(),
                                                                                                                                    PresetStudio
                                                                                                                                            .derive(
                                                                                                                                                    ModpackRuntime
                                                                                                                                                            .directory(),
                                                                                                                                                    revision
                                                                                                                                                            .name(),
                                                                                                                                                    revision
                                                                                                                                                            .release(),
                                                                                                                                                    PresetStudio
                                                                                                                                                            .fingerprint(
                                                                                                                                                                    ModpackRuntime
                                                                                                                                                                            .directory(),
                                                                                                                                                                    revision
                                                                                                                                                                            .name(),
                                                                                                                                                                    revision
                                                                                                                                                                            .release()),
                                                                                                                                                    v
                                                                                                                                                            .getFirst(),
                                                                                                                                                    v
                                                                                                                                                            .get(
                                                                                                                                                                    1),
                                                                                                                                                    registries));
                                                                                                                        })));
                                                                        })));
                                    }));
                });
    }

    private void dimensions() {
        action("Select dimension: " + selected.identifier(), 0, this::selectDimension);
        action("Terrain / generator", 1, this::terrain).active = canDimension(selected);
        action(
                "Biomes, features and cells",
                2,
                () ->
                        act(
                                () -> {
                                    var stem =
                                            checked()
                                                    .preset()
                                                    .createWorldDimensions()
                                                    .dimensions()
                                                    .get(selected);
                                    if (stem.generator() instanceof CellGenerator) {
                                        cells();
                                        return;
                                    }
                                    if (!(stem.generator() instanceof SolidColumnGenerator solid))
                                        throw new IOException(
                                                "Void islands have no natural features. Choose a"
                                                        + " solid generator to edit these groups.");
                                    minecraft.gui.setScreen(
                                            new DimensionFeaturesScreen(
                                                    this,
                                                    Component.literal(
                                                            selected.identifier().toString()),
                                                    solid,
                                                    solid.dimensionProfile().orElseThrow(),
                                                    registries,
                                                    draft.manifest().options().seed(),
                                                    f -> canFeature(selected, f),
                                                    edited ->
                                                            act(
                                                                    () ->
                                                                            replace(
                                                                                    selected,
                                                                                    new LevelStem(
                                                                                            stem
                                                                                                    .type(),
                                                                                            edited)))));
                                }));
        action("Native End progression", 3, this::endProgression);
        action(
                "Preview terrain",
                4,
                () ->
                        act(
                                () -> {
                                    var stem =
                                            checked()
                                                    .preset()
                                                    .createWorldDimensions()
                                                    .dimensions()
                                                    .get(selected);
                                    var preview =
                                            GenerationPreview.forStem(
                                                            stem, draft.manifest().options().seed())
                                                    .orElseThrow(
                                                            () ->
                                                                    new IOException(
                                                                            "The native generator"
                                                                                + " has no managed"
                                                                                + " preview."));
                                    minecraft.gui.setScreen(
                                            new WorldPreviewScreen(
                                                    this,
                                                    selected.identifier(),
                                                    draft.manifest().options().seed(),
                                                    preview));
                                }));
    }

    private void cells() {
        act(
                () -> {
                    var stem =
                            checked().preset().createWorldDimensions().dimensions().get(selected);
                    if (!(stem.generator() instanceof CellGenerator))
                        throw new IOException(
                                "Choose a cell-world recipe from Project > Choose starter first.");
                    if (!canDimension(selected))
                        throw new IOException("The parent locks this dimension.");
                    minecraft.gui.setScreen(
                            new CellStudioScreen(
                                    this,
                                    registries,
                                    stem,
                                    draft.manifest().options().seed(),
                                    edited ->
                                            act(
                                                    () ->
                                                            replace(
                                                                    selected,
                                                                    new LevelStem(
                                                                            stem.type(),
                                                                            edited)))));
                });
    }

    private void selectDimension() {
        act(
                () -> {
                    var keys =
                            checked()
                                    .preset()
                                    .createWorldDimensions()
                                    .dimensions()
                                    .keySet()
                                    .stream()
                                    .sorted(Comparator.comparing(k -> k.identifier().toString()))
                                    .toList();
                    minecraft.gui.setScreen(
                            new StudioChoiceScreen(
                                    this,
                                    "Choose dimension",
                                    keys.stream().map(k -> k.identifier().toString()).toList(),
                                    id -> {
                                        selected =
                                                ResourceKey.create(
                                                        Registries.LEVEL_STEM,
                                                        Identifier.parse(id));
                                        minecraft.gui.setScreen(this);
                                    }));
                });
    }

    private void replace(ResourceKey<LevelStem> key, LevelStem stem) throws IOException {
        update(ModpackProject.dimension(draft, key, stem, registries));
    }

    private void terrain() {
        act(
                () -> {
                    if (!canDimension(selected))
                        throw new IOException("The parent locks this dimension.");
                    var stem =
                            checked().preset().createWorldDimensions().dimensions().get(selected);
                    var support = DimensionCatalog.inspect(registries, selected, stem);
                    minecraft.gui.setScreen(
                            new StudioChoiceScreen(
                                    this,
                                    "Dimension generator",
                                    List.of(
                                            "Native generation",
                                            "Single Block",
                                            "Layered",
                                            "Void",
                                            "Skyblock",
                                            "Cell worlds"),
                                    choice ->
                                            act(
                                                    () -> {
                                                        if (choice.equals("Native generation")) {
                                                            var nativeStem =
                                                                    registries
                                                                            .lookupOrThrow(
                                                                                    Registries
                                                                                            .WORLD_PRESET)
                                                                            .getOrThrow(
                                                                                    WorldPresets
                                                                                            .NORMAL)
                                                                            .value()
                                                                            .createWorldDimensions()
                                                                            .dimensions()
                                                                            .get(selected);
                                                            if (nativeStem == null)
                                                                throw new IOException(
                                                                        "This custom dimension must"
                                                                            + " use its registered"
                                                                            + " adapter.");
                                                            replace(selected, nativeStem);
                                                            minecraft.gui.setScreen(this);
                                                            return;
                                                        }
                                                        if (support.biome().isEmpty())
                                                            throw new IOException(
                                                                    "This dimension needs a"
                                                                        + " compatible registered"
                                                                        + " adapter.");
                                                        var requested =
                                                                switch (choice) {
                                                                    case "Single Block" ->
                                                                            com.gwsheridan
                                                                                    .worldsunwritten
                                                                                    .api
                                                                                    .DimensionAdapter
                                                                                    .TerrainMode
                                                                                    .SINGLE_BLOCK;
                                                                    case "Layered" ->
                                                                            com.gwsheridan
                                                                                    .worldsunwritten
                                                                                    .api
                                                                                    .DimensionAdapter
                                                                                    .TerrainMode
                                                                                    .LAYERED;
                                                                    case "Skyblock" ->
                                                                            com.gwsheridan
                                                                                    .worldsunwritten
                                                                                    .api
                                                                                    .DimensionAdapter
                                                                                    .TerrainMode
                                                                                    .SKYBLOCK;
                                                                    case "Cell worlds" ->
                                                                            com.gwsheridan
                                                                                    .worldsunwritten
                                                                                    .api
                                                                                    .DimensionAdapter
                                                                                    .TerrainMode
                                                                                    .CELLULAR;
                                                                    default ->
                                                                            com.gwsheridan
                                                                                    .worldsunwritten
                                                                                    .api
                                                                                    .DimensionAdapter
                                                                                    .TerrainMode
                                                                                    .VOID;
                                                                };
                                                        if (!support.modes().contains(requested))
                                                            throw new IOException(
                                                                    "The dimension adapter does not"
                                                                        + " support this generation"
                                                                        + " mode.");
                                                        var type = stem.type().value();
                                                        var profile =
                                                                new DimensionProfile(
                                                                        type.minY(),
                                                                        type.height(),
                                                                        support.adapter());
                                                        if (selected.equals(LevelStem.END)
                                                                && type.hasEnderDragonFight()
                                                                && type.minY() == 0
                                                                && type.height() == 256
                                                                && support.adapter().isEmpty())
                                                            profile =
                                                                    profile.withEndProgression(
                                                                            Optional.of(
                                                                                    EndProgression
                                                                                            .vanillaArena(
                                                                                                    registries)));
                                                        var material =
                                                                selected.equals(LevelStem.NETHER)
                                                                        ? Blocks.NETHERRACK
                                                                                .defaultBlockState()
                                                                        : selected.equals(
                                                                                        LevelStem
                                                                                                .END)
                                                                                ? Blocks.END_STONE
                                                                                        .defaultBlockState()
                                                                                : Blocks.STONE
                                                                                        .defaultBlockState();
                                                        if (choice.equals("Single Block")) {
                                                            var generator =
                                                                    stem.generator()
                                                                                    instanceof
                                                                                    SingleBlockGenerator
                                                                                            old
                                                                            ? old
                                                                            : new SingleBlockGenerator(
                                                                                    support.biome()
                                                                                            .orElseThrow(),
                                                                                    new SingleBlockProfile(
                                                                                            material,
                                                                                            new TerrainShape(
                                                                                                    64,
                                                                                                    0)),
                                                                                    Optional.of(
                                                                                            profile));
                                                            minecraft.gui.setScreen(
                                                                    new SingleBlockScreen(
                                                                            this,
                                                                            Component.literal(
                                                                                    selected.identifier()
                                                                                            .toString()),
                                                                            generator,
                                                                            registries,
                                                                            edited ->
                                                                                    act(
                                                                                            () ->
                                                                                                    replace(
                                                                                                            selected,
                                                                                                            new LevelStem(
                                                                                                                    stem
                                                                                                                            .type(),
                                                                                                                    edited)))));
                                                        } else if (choice.equals("Layered")) {
                                                            var generator =
                                                                    stem.generator()
                                                                                    instanceof
                                                                                    LayeredGenerator
                                                                                            old
                                                                            ? old
                                                                            : new LayeredGenerator(
                                                                                    support.biome()
                                                                                            .orElseThrow(),
                                                                                    new LayeredProfile(
                                                                                            material,
                                                                                            List
                                                                                                    .of(),
                                                                                            new TerrainShape(
                                                                                                    64,
                                                                                                    0),
                                                                                            List
                                                                                                    .of()),
                                                                                    Optional.of(
                                                                                            profile));
                                                            minecraft.gui.setScreen(
                                                                    new LayeredScreen(
                                                                            this,
                                                                            generator,
                                                                            registries,
                                                                            () ->
                                                                                    draft.manifest()
                                                                                            .options()
                                                                                            .seed(),
                                                                            edited ->
                                                                                    act(
                                                                                            () ->
                                                                                                    replace(
                                                                                                            selected,
                                                                                                            new LevelStem(
                                                                                                                    stem
                                                                                                                            .type(),
                                                                                                                    edited)))));
                                                        } else if (choice.equals("Cell worlds")) {
                                                            var cellular =
                                                                    stem.generator()
                                                                                    instanceof
                                                                                    CellGenerator
                                                                                            existing
                                                                            ? existing
                                                                            : new CellGenerator(
                                                                                    profile,
                                                                                    com.gwsheridan
                                                                                            .worldsunwritten
                                                                                            .cells
                                                                                            .CellRecipes
                                                                                            .create(
                                                                                                    "Hex Continents",
                                                                                                    registries));
                                                            minecraft.gui.setScreen(
                                                                    new CellStudioScreen(
                                                                            this,
                                                                            registries,
                                                                            new LevelStem(
                                                                                    stem.type(),
                                                                                    cellular),
                                                                            draft.manifest()
                                                                                    .options()
                                                                                    .seed(),
                                                                            edited ->
                                                                                    act(
                                                                                            () ->
                                                                                                    replace(
                                                                                                            selected,
                                                                                                            new LevelStem(
                                                                                                                    stem
                                                                                                                            .type(),
                                                                                                                    edited)))));
                                                        } else if (choice.equals("Skyblock")) {
                                                            var catalog =
                                                                    new IslandCatalog(
                                                                            List.of(
                                                                                    new IslandCatalog
                                                                                            .Entry(
                                                                                            "classic",
                                                                                            "Classic"
                                                                                                + " island",
                                                                                            "A safe"
                                                                                                + " shared"
                                                                                                + " starting"
                                                                                                + " island.",
                                                                                            "minecraft:grass_block",
                                                                                            1,
                                                                                            IslandBuild
                                                                                                    .classic())),
                                                                            "classic",
                                                                            "fixed",
                                                                            false,
                                                                            64,
                                                                            128,
                                                                            0);
                                                            replace(
                                                                    selected,
                                                                    new LevelStem(
                                                                            stem.type(),
                                                                            new SkyblockGenerator(
                                                                                    support.biome()
                                                                                            .orElseThrow(),
                                                                                    profile,
                                                                                    catalog)));
                                                            step = 2;
                                                            minecraft.gui.setScreen(this);
                                                        } else {
                                                            var generator =
                                                                    new VoidGenerator(
                                                                            support.biome()
                                                                                    .orElseThrow(),
                                                                            profile,
                                                                            Optional.empty(),
                                                                            selected.equals(
                                                                                    LevelStem
                                                                                            .OVERWORLD));
                                                            Runnable apply =
                                                                    () ->
                                                                            act(
                                                                                    () -> {
                                                                                        replace(
                                                                                                selected,
                                                                                                new LevelStem(
                                                                                                        stem
                                                                                                                .type(),
                                                                                                        generator));
                                                                                        minecraft
                                                                                                .gui
                                                                                                .setScreen(
                                                                                                        this);
                                                                                    });
                                                            if (selected.equals(
                                                                    LevelStem.OVERWORLD))
                                                                minecraft.gui.setScreen(
                                                                        new StudioConfirmScreen(
                                                                                yes -> {
                                                                                    if (yes)
                                                                                        apply.run();
                                                                                    else
                                                                                        minecraft
                                                                                                .gui
                                                                                                .setScreen(
                                                                                                        this);
                                                                                },
                                                                                Component.literal(
                                                                                        "Accept an"
                                                                                            + " empty"
                                                                                            + " Overworld?"),
                                                                                Component.literal(
                                                                                        "There is"
                                                                                            + " no safe"
                                                                                            + " starting"
                                                                                            + " position"
                                                                                            + " without"
                                                                                            + " an island.")));
                                                            else apply.run();
                                                        }
                                                    })));
                });
    }

    private void endProgression() {
        act(
                () -> {
                    var key = LevelStem.END;
                    var stem = checked().preset().createWorldDimensions().dimensions().get(key);
                    if (!(stem.generator() instanceof ManagedGenerator managed)) {
                        status = "The native End already provides vanilla dragon progression.";
                        return;
                    }
                    if (!canDimension(key))
                        throw new IOException("The parent locks End progression.");
                    if (stem.type().value().minY() != 0
                            || stem.type().value().height() != 256
                            || !stem.type().value().hasEnderDragonFight())
                        throw new IOException(
                                "Native End progression requires the native 0..255 End dimension"
                                        + " type.");
                    var profile =
                            managed.dimensionProfile()
                                    .orElseThrow()
                                    .withEndProgression(
                                            Optional.of(EndProgression.vanillaArena(registries)));
                    var next =
                            stem.generator() instanceof SolidColumnGenerator solid
                                    ? solid.withDimensionProfile(profile)
                                    : stem.generator() instanceof CellGenerator cells
                                            ? cells.withDimensionProfile(profile)
                                            : ((VoidGenerator) stem.generator())
                                                    .withDimensionProfile(profile);
                    replace(key, new LevelStem(stem.type(), next));
                    status = "Native central End arena and dragon progression enabled.";
                });
    }

    private void islands() {
        action("Select dimension: " + selected.identifier(), 0, this::selectDimension);
        action("Edit island catalog", 1, this::catalog).active = canDimension(selected);
        action("Allocation and spacing", 2, this::allocation).active = canDimension(selected);
        action(
                "Starting island overview",
                3,
                () ->
                        act(
                                () -> {
                                    var rich = rich();
                                    var layout = rich.layout(draft.manifest().options().seed());
                                    var counts = new TreeMap<String, Integer>();
                                    for (int slot = 1; slot <= layout.personalSlots(); slot++)
                                        counts.merge(layout.type(slot), 1, Integer::sum);
                                    var rows = new ArrayList<String>();
                                    rows.add("Shared lobby: " + rich.catalog().lobby());
                                    rows.add(
                                            "Mode: "
                                                    + rich.catalog().mode()
                                                    + " · capacity "
                                                    + layout.personalSlots());
                                    counts.forEach(
                                            (id, n) ->
                                                    rows.add(
                                                            id
                                                                    + ": "
                                                                    + n
                                                                    + " pre-generated personal"
                                                                    + " slots"));
                                    rows.add(
                                            "Choices claim matching vacant slots; they never"
                                                    + " replace explored terrain.");
                                    minecraft.gui.setScreen(
                                            new StudioReportScreen(this, "Island layout", rows));
                                }));
    }

    private SkyblockGenerator rich() throws IOException {
        var stem = checked().preset().createWorldDimensions().dimensions().get(selected);
        if (stem.generator() instanceof SkyblockGenerator rich) return rich;
        throw new IOException("Choose a Skyblock starter or generator for this dimension first.");
    }

    private void changeCatalog(IslandCatalog next) throws IOException {
        var stem = checked().preset().createWorldDimensions().dimensions().get(selected);
        var old = rich();
        replace(
                selected,
                new LevelStem(
                        stem.type(),
                        new SkyblockGenerator(
                                old.biome(), old.dimensionProfile().orElseThrow(), next)));
    }

    private void catalog() {
        act(
                () -> {
                    var rich = rich();
                    var labels = new ArrayList<String>();
                    labels.add("Add island type");
                    rich.catalog().entries().forEach(e -> labels.add(e.id() + " · " + e.title()));
                    minecraft.gui.setScreen(
                            new StudioChoiceScreen(
                                    this,
                                    "Island catalog",
                                    labels,
                                    choice -> {
                                        if (choice.startsWith("Add")) typeDetails(null);
                                        else {
                                            String id = choice.substring(0, choice.indexOf(" · "));
                                            minecraft.gui.setScreen(
                                                    new StudioChoiceScreen(
                                                            this,
                                                            id,
                                                            List.of(
                                                                    "Open Island Studio",
                                                                    "Details and weight",
                                                                    "Use as shared lobby",
                                                                    "Duplicate type",
                                                                    "Remove type"),
                                                            action ->
                                                                    act(
                                                                            () -> {
                                                                                var c =
                                                                                        rich().catalog();
                                                                                var entry =
                                                                                        c
                                                                                                .entries()
                                                                                                .stream()
                                                                                                .filter(
                                                                                                        e ->
                                                                                                                e.id().equals(
                                                                                                                                id))
                                                                                                .findFirst()
                                                                                                .orElseThrow();
                                                                                if (action
                                                                                        .startsWith(
                                                                                                "Open"))
                                                                                    minecraft.gui
                                                                                            .setScreen(
                                                                                                    new IslandStudioScreen(
                                                                                                            this,
                                                                                                            registries,
                                                                                                            entry
                                                                                                                    .build(),
                                                                                                            build -> {
                                                                                                                try {
                                                                                                                    var
                                                                                                                            entries =
                                                                                                                                    new ArrayList<>(
                                                                                                                                            c
                                                                                                                                                    .entries());
                                                                                                                    entries
                                                                                                                            .replaceAll(
                                                                                                                                    e ->
                                                                                                                                            e.id().equals(
                                                                                                                                                                    id)
                                                                                                                                                    ? new IslandCatalog
                                                                                                                                                            .Entry(
                                                                                                                                                            e
                                                                                                                                                                    .id(),
                                                                                                                                                            e
                                                                                                                                                                    .title(),
                                                                                                                                                            e
                                                                                                                                                                    .description(),
                                                                                                                                                            e
                                                                                                                                                                    .icon(),
                                                                                                                                                            e
                                                                                                                                                                    .weight(),
                                                                                                                                                            build)
                                                                                                                                                    : e);
                                                                                                                    changeCatalog(
                                                                                                                            new IslandCatalog(
                                                                                                                                    entries,
                                                                                                                                    c
                                                                                                                                            .lobby(),
                                                                                                                                    c
                                                                                                                                            .mode(),
                                                                                                                                    c
                                                                                                                                            .teams(),
                                                                                                                                    c
                                                                                                                                            .originY(),
                                                                                                                                    c
                                                                                                                                            .spacing(),
                                                                                                                                    c
                                                                                                                                            .personalSlots()));
                                                                                                                } catch (
                                                                                                                        IOException
                                                                                                                                error) {
                                                                                                                    throw new IllegalArgumentException(
                                                                                                                            error
                                                                                                                                    .getMessage(),
                                                                                                                            error);
                                                                                                                }
                                                                                                            }));
                                                                                else if (action
                                                                                        .startsWith(
                                                                                                "Details"))
                                                                                    typeDetails(
                                                                                            entry);
                                                                                else if (action
                                                                                        .startsWith(
                                                                                                "Use")) {
                                                                                    changeCatalog(
                                                                                            new IslandCatalog(
                                                                                                    c
                                                                                                            .entries(),
                                                                                                    id,
                                                                                                    c
                                                                                                            .mode(),
                                                                                                    c
                                                                                                            .teams(),
                                                                                                    c
                                                                                                            .originY(),
                                                                                                    c
                                                                                                            .spacing(),
                                                                                                    c
                                                                                                            .personalSlots()));
                                                                                    minecraft.gui
                                                                                            .setScreen(
                                                                                                    this);
                                                                                } else if (action
                                                                                        .startsWith(
                                                                                                "Duplicate"))
                                                                                    typeDetails(
                                                                                            new IslandCatalog
                                                                                                    .Entry(
                                                                                                    id
                                                                                                            + "_copy",
                                                                                                    entry
                                                                                                                    .title()
                                                                                                            + " copy",
                                                                                                    entry
                                                                                                            .description(),
                                                                                                    entry
                                                                                                            .icon(),
                                                                                                    entry
                                                                                                            .weight(),
                                                                                                    entry
                                                                                                            .build()));
                                                                                else {
                                                                                    if (c.lobby()
                                                                                            .equals(
                                                                                                    id))
                                                                                        throw new IOException(
                                                                                                "Choose"
                                                                                                    + " another"
                                                                                                    + " lobby"
                                                                                                    + " type"
                                                                                                    + " before"
                                                                                                    + " removing"
                                                                                                    + " this"
                                                                                                    + " one.");
                                                                                    changeCatalog(
                                                                                            new IslandCatalog(
                                                                                                    c
                                                                                                            .entries()
                                                                                                            .stream()
                                                                                                            .filter(
                                                                                                                    e ->
                                                                                                                            !e.id().equals(
                                                                                                                                            id))
                                                                                                            .toList(),
                                                                                                    c
                                                                                                            .lobby(),
                                                                                                    c
                                                                                                            .mode(),
                                                                                                    c
                                                                                                            .teams(),
                                                                                                    c
                                                                                                            .originY(),
                                                                                                    c
                                                                                                            .spacing(),
                                                                                                    c
                                                                                                            .personalSlots()));
                                                                                    minecraft.gui
                                                                                            .setScreen(
                                                                                                    this);
                                                                                }
                                                                            })));
                                        }
                                    }));
                });
    }

    private void typeDetails(IslandCatalog.Entry source) {
        var e =
                source == null
                        ? new IslandCatalog.Entry(
                                "custom",
                                "Custom island",
                                "",
                                "minecraft:grass_block",
                                1,
                                IslandBuild.classic())
                        : source;
        minecraft.gui.setScreen(
                new StudioFormScreen(
                        this,
                        "Island type details",
                        "Weights determine the saved slot layout. The overview shows each type's"
                                + " actual capacity for this seed.",
                        List.of(
                                new StudioFormScreen.Field("Type ID", e.id()),
                                new StudioFormScreen.Field("Title", e.title()),
                                new StudioFormScreen.Field("Description", e.description()),
                                new StudioFormScreen.Field("Icon item ID", e.icon()),
                                new StudioFormScreen.Field(
                                        "Weight · 1–100", Integer.toString(e.weight()))),
                        v ->
                                v.getFirst().matches("[a-z][a-z0-9_-]{0,31}")
                                                && Integer.parseInt(v.get(4)) >= 1
                                                && Integer.parseInt(v.get(4)) <= 100
                                        ? ""
                                        : "Use a short lowercase ID and a weight from 1 to 100.",
                        v ->
                                act(
                                        () -> {
                                            var c = rich().catalog();
                                            var entries = new ArrayList<>(c.entries());
                                            boolean existing =
                                                    source != null
                                                            && c.entries().stream()
                                                                    .anyMatch(
                                                                            t ->
                                                                                    t.id().equals(
                                                                                                    source
                                                                                                            .id()));
                                            if (entries.stream()
                                                    .anyMatch(
                                                            t ->
                                                                    t.id().equals(v.getFirst())
                                                                            && (!existing
                                                                                    || !t.id().equals(
                                                                                                    source
                                                                                                            .id()))))
                                                throw new IOException(
                                                        "That island type ID already exists.");
                                            if (existing)
                                                entries.removeIf(t -> t.id().equals(source.id()));
                                            entries.add(
                                                    new IslandCatalog.Entry(
                                                            v.getFirst(),
                                                            v.get(1),
                                                            v.get(2),
                                                            v.get(3),
                                                            Integer.parseInt(v.get(4)),
                                                            e.build()));
                                            String lobby =
                                                    existing && c.lobby().equals(source.id())
                                                            ? v.getFirst()
                                                            : c.lobby();
                                            changeCatalog(
                                                    new IslandCatalog(
                                                            entries,
                                                            lobby,
                                                            c.mode(),
                                                            c.teams(),
                                                            c.originY(),
                                                            c.spacing(),
                                                            c.personalSlots()));
                                        })));
    }

    private void allocation() {
        act(
                () -> {
                    var c = rich().catalog();
                    minecraft.gui.setScreen(
                            new StudioFormScreen(
                                    this,
                                    "Island allocation",
                                    "Personal and team slots require the Overworld. Modes: fixed,"
                                        + " seeded or choice. Capacity is fixed before creation.",
                                    List.of(
                                            new StudioFormScreen.Field("Mode", c.mode()),
                                            new StudioFormScreen.Field(
                                                    "Team islands · true/false",
                                                    Boolean.toString(c.teams())),
                                            new StudioFormScreen.Field(
                                                    "Island bottom Y",
                                                    Integer.toString(c.originY())),
                                            new StudioFormScreen.Field(
                                                    "Spacing · multiple of 16",
                                                    Integer.toString(c.spacing())),
                                            new StudioFormScreen.Field(
                                                    "Personal slots · 0 shared only",
                                                    Integer.toString(c.personalSlots()))),
                                    v -> {
                                        new IslandCatalog(
                                                        c.entries(),
                                                        c.lobby(),
                                                        v.getFirst(),
                                                        Boolean.parseBoolean(v.get(1)),
                                                        Integer.parseInt(v.get(2)),
                                                        Integer.parseInt(v.get(3)),
                                                        Integer.parseInt(v.get(4)))
                                                .validate()
                                                .getOrThrow();
                                        return bool(v.get(1))
                                                ? ""
                                                : "Use true or false for team allocation.";
                                    },
                                    v ->
                                            act(
                                                    () ->
                                                            changeCatalog(
                                                                    new IslandCatalog(
                                                                            c.entries(),
                                                                            c.lobby(),
                                                                            v.getFirst(),
                                                                            Boolean.parseBoolean(
                                                                                    v.get(1)),
                                                                            Integer.parseInt(
                                                                                    v.get(2)),
                                                                            Integer.parseInt(
                                                                                    v.get(3)),
                                                                            Integer.parseInt(
                                                                                    v.get(4)))))));
                });
    }

    private PackPolicy policy() {
        return draft.manifest().pack().orElse(PackPolicy.DEFAULT);
    }

    private void policy(PackPolicy next) throws IOException {
        update(draft.withManifest(draft.manifest().withPack(Optional.of(next))));
    }

    private void policies() {
        action("Initial spawn", 0, () -> spawn("initial"));
        action("Death respawn", 1, () -> spawn("respawn"));
        action("Portal arrivals", 2, this::portal);
        action("Island protection", 3, this::protection);
        action(
                "Runtime edit permissions",
                4,
                () ->
                        minecraft.gui.setScreen(
                                new StudioLockScreen(
                                        this,
                                        draft.manifest().withPack(Optional.of(policy())),
                                        draft.preset(),
                                        m -> act(() -> update(draft.withManifest(m))))));
    }

    private void spawn(String event) {
        minecraft.gui.setScreen(
                new StudioChoiceScreen(
                        this,
                        event + " spawn",
                        List.of(
                                "World default",
                                "Assigned island",
                                "Team island",
                                "Safe fixed position"),
                        choice ->
                                act(
                                        () -> {
                                            var old = policy();
                                            if (choice.equals("Safe fixed position")) {
                                                fixed(event, LevelStem.OVERWORLD.identifier());
                                                return;
                                            }
                                            Optional<SpawnPolicy> selected =
                                                    choice.equals("World default")
                                                            ? Optional.empty()
                                                            : Optional.of(
                                                                    SpawnPolicy.simple(
                                                                            choice.equals(
                                                                                            "Team island")
                                                                                    ? SpawnPolicy
                                                                                            .Kind
                                                                                            .TEAM
                                                                                    : SpawnPolicy
                                                                                            .Kind
                                                                                            .ISLAND,
                                                                            LevelStem.OVERWORLD
                                                                                    .identifier(),
                                                                            BlockPos.ZERO));
                                            policy(
                                                    new PackPolicy(
                                                            old.defaults()
                                                                    .withPolicy(
                                                                            event,
                                                                            selected,
                                                                            LevelStem.OVERWORLD
                                                                                    .identifier()),
                                                            old.editable()));
                                            minecraft.gui.setScreen(this);
                                        })));
    }

    private void fixed(String event, Identifier dimension) {
        minecraft.gui.setScreen(
                new StudioFormScreen(
                        this,
                        "Safe fixed " + event,
                        "The server checks live support and headroom. Test this destination in a"
                                + " clean world before distributing it.",
                        List.of(
                                new StudioFormScreen.Field("Dimension", dimension.toString()),
                                new StudioFormScreen.Field("X", "0"),
                                new StudioFormScreen.Field("Feet Y", "68"),
                                new StudioFormScreen.Field("Z", "0")),
                        v -> {
                            Identifier.parse(v.getFirst());
                            for (int i = 1; i < 4; i++) Integer.parseInt(v.get(i));
                            return "";
                        },
                        v ->
                                act(
                                        () -> {
                                            var p = policy();
                                            var id = Identifier.parse(v.getFirst());
                                            policy(
                                                    new PackPolicy(
                                                            p.defaults()
                                                                    .withPolicy(
                                                                            event,
                                                                            Optional.of(
                                                                                    SpawnPolicy
                                                                                            .simple(
                                                                                                    SpawnPolicy
                                                                                                            .Kind
                                                                                                            .FIXED,
                                                                                                    id,
                                                                                                    new BlockPos(
                                                                                                            Integer
                                                                                                                    .parseInt(
                                                                                                                            v
                                                                                                                                    .get(
                                                                                                                                            1)),
                                                                                                            Integer
                                                                                                                    .parseInt(
                                                                                                                            v
                                                                                                                                    .get(
                                                                                                                                            2)),
                                                                                                            Integer
                                                                                                                    .parseInt(
                                                                                                                            v
                                                                                                                                    .get(
                                                                                                                                            3))))),
                                                                            id),
                                                            p.editable()));
                                        })));
    }

    private void portal() {
        act(
                () -> {
                    var keys =
                            checked()
                                    .preset()
                                    .createWorldDimensions()
                                    .dimensions()
                                    .keySet()
                                    .stream()
                                    .map(k -> k.identifier().toString())
                                    .sorted()
                                    .toList();
                    minecraft.gui.setScreen(
                            new StudioChoiceScreen(
                                    this,
                                    "Portal destination dimension",
                                    keys,
                                    id ->
                                            minecraft.gui.setScreen(
                                                    new StudioChoiceScreen(
                                                            this,
                                                            id,
                                                            List.of(
                                                                    "Native arrival",
                                                                    "Shared or assigned island",
                                                                    "Safe fixed position"),
                                                            choice ->
                                                                    act(
                                                                            () -> {
                                                                                var dimension =
                                                                                        Identifier
                                                                                                .parse(
                                                                                                        id);
                                                                                if (choice
                                                                                        .startsWith(
                                                                                                "Safe")) {
                                                                                    fixed(
                                                                                            "portal",
                                                                                            dimension);
                                                                                    return;
                                                                                }
                                                                                var p = policy();
                                                                                policy(
                                                                                        new PackPolicy(
                                                                                                p.defaults()
                                                                                                        .withPolicy(
                                                                                                                "portal",
                                                                                                                choice
                                                                                                                                .startsWith(
                                                                                                                                        "Native")
                                                                                                                        ? Optional
                                                                                                                                .empty()
                                                                                                                        : Optional
                                                                                                                                .of(
                                                                                                                                        SpawnPolicy
                                                                                                                                                .simple(
                                                                                                                                                        SpawnPolicy
                                                                                                                                                                .Kind
                                                                                                                                                                .ISLAND,
                                                                                                                                                        dimension,
                                                                                                                                                        BlockPos
                                                                                                                                                                .ZERO)),
                                                                                                                dimension),
                                                                                                p
                                                                                                        .editable()));
                                                                                minecraft.gui
                                                                                        .setScreen(
                                                                                                this);
                                                                            })))));
                });
    }

    private void protection() {
        var p = policy();
        var old = p.defaults().protection();
        minecraft.gui.setScreen(
                new StudioFormScreen(
                        this,
                        "Island protection defaults",
                        "These settings initialize new worlds only. Player protection does not"
                                + " claim against automation or environmental damage.",
                        List.of(
                                new StudioFormScreen.Field(
                                        "Protect breaking", Boolean.toString(old.breaking())),
                                new StudioFormScreen.Field(
                                        "Protect placement", Boolean.toString(old.placement())),
                                new StudioFormScreen.Field(
                                        "Protect containers", Boolean.toString(old.containers())),
                                new StudioFormScreen.Field(
                                        "Protect shared lobby", Boolean.toString(old.shared())),
                                new StudioFormScreen.Field(
                                        "Container block IDs",
                                        String.join(
                                                ",",
                                                old.containerBlocks().stream()
                                                        .map(Object::toString)
                                                        .toList()))),
                        v ->
                                v.subList(0, 4).stream().allMatch(ModpackBuilderScreen::bool)
                                        ? ""
                                        : "Use true or false for protection flags.",
                        v ->
                                act(
                                        () -> {
                                            var blocks =
                                                    Arrays.stream(v.get(4).split(","))
                                                            .map(String::strip)
                                                            .filter(s -> !s.isEmpty())
                                                            .map(Identifier::parse)
                                                            .toList();
                                            policy(
                                                    new PackPolicy(
                                                            p.defaults()
                                                                    .withProtection(
                                                                            new WorldAdministration
                                                                                    .Protection(
                                                                                    Boolean
                                                                                            .parseBoolean(
                                                                                                    v
                                                                                                            .get(
                                                                                                                    0)),
                                                                                    Boolean
                                                                                            .parseBoolean(
                                                                                                    v
                                                                                                            .get(
                                                                                                                    1)),
                                                                                    Boolean
                                                                                            .parseBoolean(
                                                                                                    v
                                                                                                            .get(
                                                                                                                    2)),
                                                                                    Boolean
                                                                                            .parseBoolean(
                                                                                                    v
                                                                                                            .get(
                                                                                                                    3)),
                                                                                    blocks)),
                                                            p.editable()));
                                        })));
    }

    private void locks() {
        action(
                "Generation and runtime locks",
                0,
                () ->
                        minecraft.gui.setScreen(
                                new StudioLockScreen(
                                        this,
                                        draft.manifest(),
                                        draft.preset(),
                                        m -> act(() -> update(draft.withManifest(m))))));
        action(
                "Inherited policy report",
                1,
                () ->
                        minecraft.gui.setScreen(
                                new StudioReportScreen(
                                        this,
                                        "Inherited policy",
                                        draft.manifest()
                                                .inheritance()
                                                .map(i -> i.report(draft))
                                                .orElse(
                                                        List.of(
                                                                "This project has no parent. Save"
                                                                    + " an immutable base, then"
                                                                    + " derive a child from Saved"
                                                                    + " presets / parents.")))));
    }

    private void review() {
        action(
                "Validate and inspect dependencies",
                0,
                () ->
                        act(
                                () ->
                                        minecraft.gui.setScreen(
                                                new StudioReportScreen(
                                                        this,
                                                        "Project validation",
                                                        checked().report()))));
        action(
                                "Save version",
                                1,
                                () ->
                                        act(
                                                () -> {
                                                    status =
                                                            "Saved "
                                                                    + PresetStudio.save(
                                                                                    ModpackRuntime
                                                                                            .directory(),
                                                                                    name,
                                                                                    draft,
                                                                                    registries)
                                                                            .getFileName();
                                                }))
                        .active =
                ModpackRuntime.developerMode();
        action(
                                "Export client / server installation",
                                2,
                                () ->
                                        act(
                                                () -> {
                                                    status =
                                                            "Exported "
                                                                    + ModpackInstallations.export(
                                                                                    ModpackRuntime
                                                                                            .directory(),
                                                                                    name,
                                                                                    draft,
                                                                                    registries)
                                                                            .getFileName();
                                                }))
                        .active =
                ModpackRuntime.developerMode();
        action(
                                "Authoring setup",
                                3,
                                () ->
                                        act(
                                                () -> {
                                                    ModpackRuntime.requestAuthoring();
                                                    status =
                                                            "Authoring configuration saved. Restart"
                                                                + " this instance; draft recovery"
                                                                + " will remain available.";
                                                }))
                        .active =
                ModpackRuntime.locked().isEmpty();
        action(
                "Test kit instructions",
                4,
                () ->
                        minecraft.gui.setScreen(
                                new StudioReportScreen(
                                        this,
                                        "Test a clean installation",
                                        List.of(
                                                "Export the client/server installation first.",
                                                "Use the optional Worlds Unwritten test kit and a"
                                                        + " matching installed NeoForge server"
                                                        + " runtime.",
                                                "Select the exported project, required provider"
                                                    + " JARs and a new disposable output folder.",
                                                "The test kit checks startup, island templates,"
                                                        + " safe markers, policy enforcement and"
                                                        + " restart persistence.",
                                                "It does not modify production worlds or certify"
                                                        + " other mods' gameplay."))));
    }

    private void history() {
        minecraft.gui.setScreen(
                new AuthoringHistoryScreen(
                        this, journal, this::snapshot, this::restore, List.of()));
    }

    private static boolean bool(String value) {
        return value.equals("true") || value.equals("false");
    }

    @Override
    public void extractPanels(GuiGraphicsExtractor g) {
        UnwrittenTheme.panel(g, left - 8, 30, left + navWidth + 8, height - 36, 0xf5192429);
        UnwrittenTheme.panel(
                g, contentX - 8, 30, left + span + 8, height - 36, UnwrittenTheme.PANEL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        super.extractRenderState(g, x, y, tick);
        g.centeredText(font, title, width / 2, 8, UnwrittenTheme.TEXT);
        g.text(
                font,
                Component.literal(STEPS.get(step)).withStyle(net.minecraft.ChatFormatting.BOLD),
                contentX,
                39,
                UnwrittenTheme.ACCENT);
        if (step == 0 && contentWidth >= 340 && height >= 310) {
            int art = Math.min(180, contentWidth / 2), imageWidth = Math.round(art * 1199f / 1312f);
            int artX = contentX + contentWidth - art + (art - imageWidth) / 2;
            g.blit(UnwrittenTheme.ISLAND, artX, 55, artX + imageWidth, 55 + art, 0, 1, 0, 1);
        }
        if (step == 4) {
            var lines =
                    font.split(
                            Component.literal(
                                    "Full fixes generation and configured runtime policies. Partial"
                                            + " permits only explicitly selected edits."),
                            contentWidth);
            for (int i = 0; i < Math.min(4, lines.size()); i++)
                g.text(font, lines.get(i), contentX, 119 + i * 11, UnwrittenTheme.MUTED);
        }
        g.text(
                font,
                font.plainSubstrByWidth(status, span),
                left,
                height - 47,
                UnwrittenTheme.MUTED);
    }

    @Override
    public void onClose() {
        if (!applied && !initial.equals(snapshot())) {
            minecraft.gui.setScreen(
                    new StudioConfirmScreen(
                            answer -> {
                                if (answer)
                                    journal.close(this, () -> minecraft.gui.setScreen(parent));
                                else minecraft.gui.setScreen(this);
                            },
                            Component.literal("Close this project draft?"),
                            Component.literal(
                                    "Saved revisions and installation exports remain available."),
                            Component.literal("Close draft"),
                            Component.literal("Keep editing")));
        } else journal.close(this, () -> minecraft.gui.setScreen(parent));
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private void act(Action action) {
        try {
            action.run();
        } catch (Exception error) {
            status = Objects.toString(error.getMessage(), "Project action failed.");
            if (minecraft.gui.screen() instanceof StudioFormScreen form) form.showError(status);
            else if (minecraft.gui.screen() instanceof StudioChoiceScreen choice)
                choice.showError(status);
        }
    }
}

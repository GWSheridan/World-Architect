package com.gwsheridan.worldarchitect.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gwsheridan.worldarchitect.config.DimensionProfile;
import com.gwsheridan.worldarchitect.rules.RuleSet;
import com.gwsheridan.worldarchitect.rules.RuleSpawns;
import com.gwsheridan.worldarchitect.structure.ModularStructure;
import com.gwsheridan.worldarchitect.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import static com.gwsheridan.worldarchitect.client.RuleFormScreen.Kind.*;

final class RuleEngineScreen extends Screen {
    private final Screen parent;
    private final Identifier dimension;
    private final ChunkGenerator original;
    private final DimensionProfile profile;
    private final RegistryAccess registries;
    private final RegistryOps<JsonElement> ops;
    private final Map<ResourceKey<LevelStem>, LevelStem> dimensions;
    private final long seed;
    private final Consumer<DimensionProfile> save;
    private final List<JsonObject> drafts = new ArrayList<>();
    private int index, left, span, navigation, addY, summaryY, formY, buttonHeight;
    private Button done;
    private String status = "";

    RuleEngineScreen(Screen parent, Identifier dimension, ChunkGenerator original, DimensionProfile profile, RegistryAccess registries,
            Map<ResourceKey<LevelStem>, LevelStem> dimensions, long seed, Consumer<DimensionProfile> save) {
        super(Component.literal("World Generation Rules"));
        this.parent = parent; this.dimension = dimension; this.original = original; this.profile = profile;
        this.registries = registries; this.dimensions = Map.copyOf(dimensions); this.seed = seed; this.save = save;
        ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        profile.rules().rules().forEach(rule -> drafts.add(RuleSet.Rule.CODEC.encodeStart(ops, rule).getOrThrow().getAsJsonObject()));
    }

    @Override protected void init() {
        span = Math.min(400, width - 24); left = (width - span) / 2;
        boolean compact = height < 260;
        navigation = compact ? 22 : 38; addY = compact ? 40 : 62; summaryY = compact ? 60 : 90; formY = compact ? 74 : 110; buttonHeight = compact ? 16 : 20;
        int part = (span - 24) / 5;
        int navigationWidth = (span - 84) / 3;
        control("←", left, navigation, 32, () -> select(index - 1)).active = index > 0;
        control("→", left + 36, navigation, 32, () -> select(index + 1)).active = index + 1 < drafts.size();
        control("Move up", left + 76, navigation, navigationWidth, () -> move(-1)).active = index > 0;
        control("Move down", left + 80 + navigationWidth, navigation, navigationWidth, () -> move(1)).active = index + 1 < drafts.size();
        control("Remove", left + 84 + navigationWidth * 2, navigation, navigationWidth, () -> { drafts.remove(index); select(Math.max(0, Math.min(index, drafts.size() - 1))); }).active = !drafts.isEmpty();
        RuleSet.Kind[] kinds = RuleSet.Kind.values();
        String[] labels = {"Material", "Ore", "Decoration", "Template", "Spawn"};
        for (int i = 0; i < kinds.length; i++) {
            var kind = kinds[i];
            control(labels[i], left + i * (part + 6), addY, part, () -> add(kind)).active = drafts.size() < RuleSet.MAX_RULES && (original instanceof SolidColumnGenerator || kind == RuleSet.Kind.SPAWN);
        }
        int half = (span - 8) / 2;
        if (!drafts.isEmpty()) {
            control("Identity and priority", left, formY, half, this::identity);
            control("Dimension, biome, height", left + half + 8, formY, half, this::world);
            control("Region and distance", left, formY + buttonHeight + 4, half, this::region);
            control("Seed and dependencies", left + half + 8, formY + buttonHeight + 4, half, this::dependencies);
            control("Edit action", left, formY + 2 * (buttonHeight + 4), half, this::action);
            control("Inspect rules", left + half + 8, formY + 2 * (buttonHeight + 4), half, this::inspect);
        }
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> { var parsed = parse(); if (parsed != null) { save.accept(profile.withRules(parsed)); onClose(); } }).bounds(left, height - 24, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(left + half + 8, height - 24, half, 20).build());
        validate();
    }
    private Button control(String label, int x, int y, int width, Runnable action) { return addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, width, buttonHeight).build()); }
    private void select(int next) { index = next; rebuildWidgets(); }
    private void move(int delta) { Collections.swap(drafts, index, index + delta); select(index + delta); }

    private RuleSet parse() {
        try {
            var json = new JsonObject(); json.addProperty("version", 1); json.addProperty("dimension", dimension.toString());
            var list = new JsonArray(); drafts.forEach(list::add); json.add("rules", list);
            var decoded = RuleSet.CODEC.parse(ops, json).getOrThrow(IllegalArgumentException::new);
            var changed = copy(profile.withRules(decoded));
            if (changed instanceof com.gwsheridan.worldarchitect.worldgen.ManagedGenerator managed) managed.profileProblem().ifPresent(problem -> { throw new IllegalArgumentException(problem); });
            var all = new LinkedHashMap<>(dimensions); var key = ResourceKey.create(Registries.LEVEL_STEM, dimension);
            all.put(key, new LevelStem(all.get(key).type(), changed));
            RuleSpawns.validate(all, registries);
            status = "Phase, then higher priority, then list order. Dependencies need earlier conditions to match.";
            return decoded;
        } catch (RuntimeException e) { status = e.getMessage() == null ? "Check the rule settings." : e.getMessage(); return null; }
    }
    private ChunkGenerator copy(DimensionProfile next) {
        return original instanceof SolidColumnGenerator solid ? solid.withDimensionProfile(next)
                : new VoidGenerator(((VoidGenerator) original).biome(), next, ((VoidGenerator) original).island(), ((VoidGenerator) original).allowUnsafeSpawn());
    }
    private void validate() { done.active = parse() != null; }

    private void add(RuleSet.Kind kind) {
        int number = 1; while (contains("rule_" + number)) number++;
        var rule = new JsonObject(); rule.addProperty("id", "rule_" + number);
        var action = new JsonObject(); action.addProperty("type", kind.getSerializedName());
        switch (kind) {
            case MATERIAL -> action.addProperty("material", "minecraft:stone");
            case ORE -> action.add("ore", JsonParser.parseString("{\"material\":\"minecraft:coal_ore\",\"attempts\":4,\"size\":8,\"min_y\":" + (profile.minY() + 4) + ",\"max_y\":" + Math.min(profile.minY() + profile.height() - 1, profile.minY() + 64) + "}"));
            case DECORATION -> action.add("decoration", JsonParser.parseString("{\"material\":\"minecraft:amethyst_block\",\"attempts\":4,\"chance\":100}"));
            case TEMPLATE -> {
                var structure = registries.lookupOrThrow(Registries.STRUCTURE).listElements().filter(holder -> holder.value() instanceof ModularStructure).findFirst();
                if (structure.isEmpty()) { status = "Install a datapack containing a registered modular structure before adding a template rule."; return; }
                int separation = Math.max(3, (((ModularStructure) structure.orElseThrow().value()).horizontalSpan() + 15) / 16);
                var template = new JsonObject(); template.addProperty("structure", structure.orElseThrow().key().identifier().toString()); template.addProperty("spacing", separation + 1); template.addProperty("separation", separation); action.add("template", template);
            }
            case SPAWN -> {
                BlockPos position = original instanceof SolidColumnGenerator solid ? solid.initialSpawn(LevelHeightAccessor.create(profile.minY(), profile.height()), seed).orElse(new BlockPos(8, profile.minY() + 1, 8)) : ((VoidGenerator) original).island().map(island -> island.spawn(0)).orElse(new BlockPos(8, profile.minY() + 1, 8));
                var spawn = new JsonObject(); spawn.addProperty("kind", "fixed"); spawn.addProperty("dimension", dimension.toString());
                var pos = new JsonArray(); pos.add(position.getX()); pos.add(position.getY()); pos.add(position.getZ()); spawn.add("position", pos); action.add("spawn", spawn);
            }
        }
        rule.add("action", action); drafts.add(rule); select(drafts.size() - 1);
    }
    private void inspect() {
        try {
            var parsed = new RuleSet(dimension, drafts.stream().map(json -> RuleSet.Rule.CODEC.parse(ops, json).getOrThrow(IllegalArgumentException::new)).toList());
            var key = ResourceKey.create(Registries.LEVEL_STEM, dimension);
            var stem = new LevelStem(dimensions.get(key).type(), copy(profile.withRules(parsed)));
            var preview = com.gwsheridan.worldarchitect.worldgen.GenerationPreview.forStem(stem, seed).orElseThrow();
            minecraft.gui.setScreen(new RuleInspectorScreen(this, dimension, seed, parsed, preview));
        } catch (RuntimeException e) { status = e.getMessage() == null ? "Fix invalid rule fields before inspecting." : e.getMessage(); }
    }
    private boolean contains(String id) { return drafts.stream().anyMatch(rule -> rule.get("id").getAsString().equals(id)); }
    private RuleFormScreen.Field field(String path, String label, RuleFormScreen.Kind kind, boolean optional) { return new RuleFormScreen.Field(path, label, kind, optional); }
    private void form(String title, String help, List<RuleFormScreen.Field> fields) {
        int selected = index;
        minecraft.gui.setScreen(new RuleFormScreen(this, title, help, drafts.get(index), ops, fields, edited -> drafts.set(selected, edited)));
    }
    private void identity() { form("Rule identity and priority", "Equal priorities use editor order. IDs are lowercase names; list dependencies with commas.", List.of(
            field("id", "Rule ID", TEXT, false), field("priority", "Priority · -1000 to 1000", INTEGER, true), field("requires", "Earlier rule IDs · optional", LIST, true))); }
    private void world() { form("Dimension, biome and height", "Blank filters match any candidate. Heights are absolute block Y values.", List.of(
            field("when.dimensions", "Dimension IDs · optional", LIST, true), field("when.biomes", "Biome IDs · optional", LIST, true),
            field("when.min_y", "Minimum Y · optional", INTEGER, true), field("when.max_y", "Maximum Y · optional", INTEGER, true), field("when.seed", "Exact seed · optional", LONG, true))); }
    private void region() { form("Region and distance", "Regions include their edges. Fill all four region bounds or leave them blank; distance needs a maximum.", List.of(
            field("when.region.min_x", "Region minimum X", INTEGER, true), field("when.region.max_x", "Region maximum X", INTEGER, true),
            field("when.region.min_z", "Region minimum Z", INTEGER, true), field("when.region.max_z", "Region maximum Z", INTEGER, true),
            field("when.distance.center_x", "Distance centre X · default 0", INTEGER, true), field("when.distance.center_z", "Distance centre Z · default 0", INTEGER, true),
            field("when.distance.min", "Minimum distance · default 0", INTEGER, true), field("when.distance.max", "Maximum distance", INTEGER, true))); }
    private void dependencies() { form("Seed and dependency conditions", "The seeded coordinate hash must have the chosen remainder. Required mods and their versions are pinned.", List.of(
            field("when.seed_modulus", "Seed modulus · default 1", INTEGER, true), field("when.seed_remainder", "Seed remainder · default 0", INTEGER, true),
            field("when.seed_salt", "Seed salt · default 0", LONG, true), field("when.mods", "Required mod IDs · optional", LIST, true), field("requires", "Earlier rule IDs · optional", LIST, true))); }
    private void action() {
        String kind = drafts.get(index).getAsJsonObject("action").get("type").getAsString();
        var fields = new ArrayList<RuleFormScreen.Field>();
        switch (kind) {
            case "material" -> { fields.add(field("action.material", "Output block state", TEXT, false)); fields.add(field("action.target", "Target block state · optional", TEXT, true)); }
            case "ore" -> { for (String name : List.of("material", "target", "attempts", "size", "min_y", "max_y", "biomes")) fields.add(field("action.ore." + name, name, name.equals("biomes") ? LIST : name.equals("material") || name.equals("target") ? TEXT : INTEGER, name.equals("target") || name.equals("biomes"))); }
            case "decoration" -> { for (String name : List.of("material", "attempts", "chance", "min_y", "max_y", "biomes")) fields.add(field("action.decoration." + name, name, name.equals("biomes") ? LIST : name.equals("material") ? TEXT : INTEGER, List.of("min_y", "max_y", "biomes").contains(name))); }
            case "template" -> { fields.add(field("action.template.structure", "Modular structure ID", TEXT, false)); fields.add(field("action.template.spacing", "Spacing · chunks", INTEGER, false)); fields.add(field("action.template.separation", "Separation · chunks", INTEGER, false)); }
            case "spawn" -> { fields.add(field("action.event", "Event · initial / respawn / portal", TEXT, true)); fields.add(field("action.spawn.kind", "Kind · fixed / generated / island / team", TEXT, false)); fields.add(field("action.spawn.dimension", "Target dimension ID", TEXT, false)); fields.add(field("action.spawn.position", "Feet or inside position · X Y Z", POSITION, false)); fields.add(field("action.spawn.template", "Generated structure ID · optional", TEXT, true)); fields.add(field("action.spawn.module", "Generated module name · optional", TEXT, true)); fields.add(field("action.spawn.marker", "Generated marker name · optional", TEXT, true)); }
        }
        form("Edit " + kind + " action", "Template rules use saved modular assemblies. Generated spawns need an enabled structure and a valid marker.", fields);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, 6, 0xFFF1E7CE);
        if (height >= 260) graphics.centeredText(font, Component.literal(dimension.toString()), width / 2, 21, 0xFFCCCCCC);
        String summary = drafts.isEmpty() ? "No rules · choose an action to add one" : (index + 1) + "/" + drafts.size() + " · " + drafts.get(index).get("id").getAsString() + " · " + drafts.get(index).getAsJsonObject("action").get("type").getAsString();
        graphics.text(font, font.plainSubstrByWidth(summary, span), left, summaryY, 0xFFEAEAEA);
        var lines = font.split(Component.literal(status), span);
        for (int i = 0; i < Math.min(height < 260 ? 1 : 2, lines.size()); i++) graphics.text(font, lines.get(i), left, height - 48 + i * 9, done.active ? 0xFFCCCCCC : 0xFFFFAAAA);
    }
}

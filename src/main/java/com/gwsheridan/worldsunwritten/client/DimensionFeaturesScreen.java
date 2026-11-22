package com.gwsheridan.worldsunwritten.client;

import static com.gwsheridan.worldsunwritten.client.GenerationRuleScreen.text;

import com.google.gson.JsonElement;
import com.gwsheridan.worldsunwritten.client.GenerationRuleScreen.Field;
import com.gwsheridan.worldsunwritten.client.GenerationRuleScreen.Kind;
import com.gwsheridan.worldsunwritten.config.ContentProfile;
import com.gwsheridan.worldsunwritten.config.DimensionProfile;
import com.gwsheridan.worldsunwritten.modpack.BundleManifest.Feature;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;

final class DimensionFeaturesScreen extends Screen {
    private final Screen parent;
    private final Component dimension;
    private final SolidColumnGenerator original;
    private final DimensionProfile profile;
    private final RegistryAccess registries;
    private final RegistryOps<JsonElement> ops;
    private final long worldSeed;
    private final Consumer<SolidColumnGenerator> save;
    private final Predicate<Feature> editable;
    private final int min;
    private final int max;
    private ContentProfile draft;
    private Component status;
    private Button done;
    private int left;
    private int top;
    private int span;

    DimensionFeaturesScreen(Screen parent, Component dimension, SolidColumnGenerator original, DimensionProfile profile,
            RegistryAccess registries, long worldSeed, Predicate<Feature> editable, Consumer<SolidColumnGenerator> save) {
        super(text("title"));
        this.parent = parent;
        this.dimension = dimension;
        this.original = original;
        this.profile = profile;
        this.registries = registries;
        this.ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        this.worldSeed = worldSeed;
        this.save = save;
        this.editable = editable;
        this.draft = profile.content();
        this.min = profile.minY();
        this.max = min + profile.height() - 1;
    }

    @Override
    protected void init() {
        span = Math.min(380, width - 22);
        top = Math.max(8, (height - 178) / 2);
        left = (width - span) / 2;
        int half = (span - 8) / 2;
        category(Feature.BIOMES, draft.biomes().size(), left, 49, half,
                () -> minecraft.gui.setScreen(new BiomeRulesScreen(this, registries, draft, worldSeed, profile -> draft = profile)));
        category(Feature.ORES, draft.ores().size(), left + half + 8, 49, half, this::ores);
        category(Feature.DECORATIONS, draft.decorations().size(), left, 77, half, this::decorations);
        category(Feature.CAVES, draft.caves().isPresent() ? 1 : 0, left + half + 8, 77, half, this::caves);
        category(Feature.FLUIDS, draft.sea().isPresent() ? 1 : 0, left, 105, half, this::fluids);
        category(Feature.STRUCTURES, draft.structures().size(), left + half + 8, 105, half, this::structures);
        button(Component.literal("Structure districts"),left+half+8,129,half,()->minecraft.gui.setScreen(new StructureDistrictScreen(this,original.terrainRegions(),draft,updated->draft=updated))).active=editable.test(Feature.STRUCTURES);
        done = button(CommonComponents.GUI_DONE, left, 153, half, this::apply);
        button(CommonComponents.GUI_CANCEL, left + half + 8, 153, half, this::onClose);
        refresh();
    }

    private void category(Feature feature, int entries, int x, int y, int width, Runnable action) {
        var button = button(count(feature.getSerializedName(), entries), x, y, width, action);
        button.active = editable.test(feature);
        if (!button.active) {
            button.setTooltip(Tooltip.create(Component.translatable("worldsunwritten.modpack.option_locked")));
        }
    }

    private void ores() {
        edit("ores", ContentProfile.Ore.CODEC, List.of(
                new Field("material", Kind.TEXT, "minecraft:coal_ore"), new Field("target", Kind.TEXT, "", true, ""),
                number("min_y", min), number("max_y", min + (max - min) / 3),
                number("attempts", 8), number("size", 6), biomeFilter()), draft.ores(), ContentProfile.MAX_RULES, draft::withOres);
    }

    private void decorations() {
        edit("decorations", ContentProfile.Decoration.CODEC, List.of(new Field("material", Kind.TEXT, "minecraft:amethyst_block"),
                biomeFilter(), number("attempts", 8), number("chance", 40),
                new Field("min_y", Kind.INTEGER, "", true, ""), new Field("max_y", Kind.INTEGER, "", true, "")),
                draft.decorations(), ContentProfile.MAX_RULES, draft::withDecorations);
    }

    private void caves() {
        edit("caves", ContentProfile.Caves.CODEC, List.of(number("min_y", min + 2), number("max_y", Math.min(max - 6, min + 52)),
                number("radius", 6)), draft.caves().stream().toList(), 1, rules -> draft.withCaves(rules.stream().findFirst()));
    }

    private void fluids() {
        edit("fluids", ContentProfile.Sea.CODEC, List.of(new Field("fluid", Kind.TEXT, "water"),
                number("level", Math.clamp(62, min, max - 3)), new Field("flood_caves", Kind.BOOLEAN, "false", false, "false")),
                draft.sea().stream().toList(), 1, rules -> draft.withSea(rules.stream().findFirst()));
    }

    private void structures() {
        edit("structures", ContentProfile.StructureRule.CODEC, List.of(new Field("structure", Kind.TEXT, "minecraft:desert_pyramid"),
                number("spacing", 32), number("separation", 8)), draft.structures(), ContentProfile.MAX_RULES, draft::withStructures);
    }

    private <T> void edit(String key, Codec<T> codec, List<Field> fields, List<T> rules, int limit, Function<List<T>, ContentProfile> update) {
        minecraft.gui.setScreen(new GenerationRuleScreen<>(this, text(key), text(key + "_help"), codec, ops, fields, rules, limit,
                values -> draft = update.apply(values)));
    }

    private Optional<String> problem(SolidColumnGenerator candidate) {
        var encoded = ContentProfile.CODEC.encodeStart(ops, draft);
        if (encoded.error().isPresent()) {
            return encoded.error().map(error -> error.message());
        }
        for (var rule : draft.structures()) {
            if (candidate.getBiomeSource().possibleBiomes().stream().noneMatch(rule.structure().value().biomes()::contains)) {
                String id = rule.structure().unwrapKey().orElseThrow().identifier().toString();
                return Optional.of(text("biome_required").getString() + " " + id);
            }
        }
        return candidate.contentProblem();
    }

    private void refresh() {
        var problem = problem(original.withDimensionProfile(profile.withContent(draft)));
        done.active = problem.isEmpty();
        status = problem.<Component>map(Component::literal).orElseGet(() -> text("explicit"));
    }

    private void apply() {
        SolidColumnGenerator candidate = original.withDimensionProfile(profile.withContent(draft));
        if (problem(candidate).isEmpty()) {
            save.accept(candidate);
            onClose();
        }
    }

    private static Field number(String name, int value) {
        return new Field(name, Kind.INTEGER, Integer.toString(value));
    }

    private static Field biomeFilter() {
        return new Field("biomes", Kind.LIST, "", true, "");
    }

    private static Component count(String key, int count) {
        return Component.translatable("worldsunwritten.features.count", text(key), count);
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, top + y, width, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, top + 9, 0xFFF1E7CE);
        graphics.centeredText(font, font.plainSubstrByWidth(dimension.getString(), span), width / 2, top + 24, 0xFFCCCCCC);
        var lines = font.split(status, (span-8)/2);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), left, top + 130 + i * 9, done.active ? 0xFFCCCCCC : 0xFFFFAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

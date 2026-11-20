package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.config.LayeredProfile;
import com.gwsheridan.worldarchitect.config.ContentProfile;
import com.gwsheridan.worldarchitect.config.TerrainMaterial;
import com.gwsheridan.worldarchitect.config.TerrainShape;
import com.gwsheridan.worldarchitect.client.GenerationRuleScreen.Field;
import com.gwsheridan.worldarchitect.client.GenerationRuleScreen.Kind;
import com.gwsheridan.worldarchitect.worldgen.LayeredGenerator;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class LayeredScreen extends Screen implements AuthoringContext {
    @Override public String authoringKey() { return parent instanceof AuthoringContext context ? context.authoringKey() : "layered:" + seed.getAsLong(); }
    private static final Component MATERIAL = Component.translatable("worldarchitect.editor.material");
    private static final Component THICKNESS = Component.translatable("worldarchitect.layers.thickness");
    private final Screen parent;
    private final LayeredGenerator original;
    private final RegistryAccess registries;
    private final LongSupplier seed;
    private final Consumer<LayeredGenerator> save;
    private final List<LayerDraft> layers = new ArrayList<>();
    private String base;
    private TerrainShape shape;
    private List<LayeredProfile.Stratum> strata;
    private int selected;
    private int top;
    private int left;
    private int formWidth;
    private int span;
    private Button done;
    private Component status = CommonComponents.EMPTY;
    private @Nullable LayeredGenerator validGenerator;
    private final int[] columnColors = new int[97];

    LayeredScreen(Screen parent, LayeredGenerator generator, RegistryAccess registries, LongSupplier seed, Consumer<LayeredGenerator> save) {
        super(Component.translatable("worldarchitect.layers.title"));
        this.parent = parent;
        this.original = generator;
        this.registries = registries;
        this.seed = seed;
        this.save = save;
        this.base = BlockStateParser.serialize(original.profile().base());
        this.shape = original.profile().shape();
        this.strata = original.profile().strata();
        for (LayeredProfile.Layer layer : original.profile().layers()) {
            layers.add(new LayerDraft(BlockStateParser.serialize(layer.material()), Integer.toString(layer.depth())));
        }
        selected = layers.isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        span = Math.min(340, width - 24);
        formWidth = span - 64;
        top = Math.max(4, (height - 176) / 2);
        left = (width - span) / 2;
        button("previous", left, 26, 32, () -> select(selected - 1)).active = selected >= 0;
        button("next", left + formWidth - 32, 26, 32, () -> select(selected + 1)).active = selected < layers.size() - 1;

        int materialWidth = formWidth - 104;
        EditBox material = entry(MATERIAL, left, 55, materialWidth, selected < 0 ? base : layers.get(selected).material(), 512);
        EditBox depth = entry(THICKNESS, left + formWidth - 46, 55, 46, selected < 0 ? "" : layers.get(selected).depth(), 4);
        depth.setEditable(selected >= 0);
        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.blocks.pick"), ignored -> {
            int layer = selected;
            minecraft.gui.setScreen(new BlockPickerScreen(this, material.getValue(), picked -> updateMaterial(layer, picked)));
        }).bounds(left + materialWidth + 4, top + 55, 50, 20).build());
        int quarter = (formWidth - 12) / 4;
        button("add", left, 79, quarter, this::addLayer).active = layers.size() < LayeredProfile.MAX_LAYERS;
        button("remove", left + quarter + 4, 79, quarter, this::removeLayer).active = selected >= 0;
        button("up", left + 2 * (quarter + 4), 79, quarter, () -> moveLayer(-1)).active = selected > 0;
        button("down", left + 3 * (quarter + 4), 79, quarter, () -> moveLayer(1)).active = selected >= 0 && selected < layers.size() - 1;

        int terrainWidth = (formWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.terrain.title"), button ->
                minecraft.gui.setScreen(new TerrainShapeScreen(this, shape, original.getMinY(), original.getGenDepth(), registries, updated -> shape = updated)))
                .bounds(left, top + 103, terrainWidth, 20).build());
        Button strataEditor = button("strata", left + terrainWidth + 4, 103, formWidth - terrainWidth - 4, this::editStrata);
        strataEditor.active = shape.advanced().isPresent() || !strata.isEmpty();
        if (!strataEditor.active) {
            strataEditor.setTooltip(Tooltip.create(Component.translatable("worldarchitect.layers.strata_requires_v2")));
        }
        int half = (span - 8) / 2;
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> apply())
                .bounds(left, height - 26, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + half + 8, height - 26, half, 20).build());

        material.setResponder(text -> {
            updateMaterial(selected, text);
            validate();
        });
        depth.setResponder(text -> {
            if (selected >= 0) {
                layers.set(selected, new LayerDraft(layers.get(selected).material(), text));
                validate();
            }
        });
        validate();
        setInitialFocus(material);
    }

    private void updateMaterial(int layer, String text) {
        if (layer < 0) {
            base = text;
        } else {
            layers.set(layer, new LayerDraft(text, layers.get(layer).depth()));
        }
    }

    private void editStrata() {
        minecraft.gui.setScreen(new GenerationRuleScreen<>(this, Component.translatable("worldarchitect.layers.strata"),
                Component.translatable("worldarchitect.layers.strata_help"), LayeredProfile.Stratum.CODEC,
                RegistryOps.create(JsonOps.INSTANCE, registries), List.of(
                    new Field("material", Kind.TEXT, "minecraft:stone"),
                    new Field("anchor", Kind.TEXT, "height"),
                    new Field("from", Kind.INTEGER, Integer.toString(original.getMinY())),
                    new Field("to", Kind.INTEGER, Integer.toString(original.getMinY())),
                    new Field("biomes", Kind.LIST, "", true, ""),
                    new Field("transition", Kind.INTEGER, "0", true, "0")
                ), strata, ContentProfile.MAX_RULES, rules -> strata = rules));
    }

    private Button button(String action, int x, int y, int buttonWidth, Runnable callback) {
        return addRenderableWidget(Button.builder(Component.translatable("worldarchitect.layers." + action), button -> callback.run())
                .bounds(x, top + y, buttonWidth, 20).build());
    }

    private EditBox entry(Component label, int x, int y, int size, String value, int limit) {
        EditBox edit = addRenderableWidget(new EditBox(font, x, top + y, size, 20, label));
        edit.setMaxLength(limit);
        edit.setValue(value);
        return edit;
    }

    private void select(int index) {
        selected = index;
        rebuildWidgets();
    }

    private void addLayer() {
        layers.add(selected + 1, new LayerDraft("minecraft:stone", "1"));
        select(selected + 1);
    }

    private void removeLayer() {
        layers.remove(selected);
        select(Math.min(selected, layers.size() - 1));
    }

    private void moveLayer(int delta) {
        Collections.swap(layers, selected, selected + delta);
        select(selected + delta);
    }

    private DataResult<LayeredProfile> readDraft() {
        return TerrainMaterial.parse(base).mapError(error -> "Base: " + error).flatMap(baseState -> {
            List<LayeredProfile.Layer> resolved = new ArrayList<>();
            for (int index = 0; index < layers.size(); index++) {
                int number = index + 1;
                LayerDraft layer = layers.get(index);
                var state = TerrainMaterial.parse(layer.material());
                if (state.error().isPresent()) {
                    return DataResult.error(() -> "Layer " + number + ": " + state.error().orElseThrow().message());
                }
                int depth = parseDepth(layer.depth());
                if (depth < 1 || depth > LayeredProfile.MAX_DEPTH) {
                    return DataResult.error(() -> "Layer " + number + " thickness must be 1–" + LayeredProfile.MAX_DEPTH + ".");
                }
                resolved.add(new LayeredProfile.Layer(state.getOrThrow(), depth));
            }
            return new LayeredProfile(baseState, resolved, shape, strata).validate();
        });
    }

    private static int parseDepth(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void validate() {
        var result = readDraft().map(original::withProfile).flatMap(generator -> generator.contentProblem()
                .<DataResult<LayeredGenerator>>map(problem -> DataResult.error(() -> problem)).orElseGet(() -> DataResult.success(generator)));
        validGenerator = result.result().orElse(null);
        done.active = validGenerator != null;
        if (validGenerator != null) {
            int min = validGenerator.getMinY();
            int height = validGenerator.getGenDepth();
            var column = validGenerator.baseColumn(8, 8, LevelHeightAccessor.create(min, height), seed.getAsLong());
            for (int row = 0; row < columnColors.length; row++) {
                int y = min + height - 1 - row * height / columnColors.length;
                BlockState state = column.getBlock(y);
                columnColors[row] = state.isAir() ? 0xFF181B22 : color(state);
            }
        }
        status = result.error()
                .<Component>map(error -> Component.literal(error.message()))
                .orElseGet(() -> Component.translatable("worldarchitect.layers.valid"));
    }

    private void apply() {
        if (validGenerator != null) {
            save.accept(validGenerator);
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, top, 0xFFF1E7CE);
        Component current = selected < 0 ? Component.translatable("worldarchitect.layers.base")
                : Component.translatable("worldarchitect.layers.position", selected + 1, layers.size());
        graphics.centeredText(font, current, left + formWidth / 2, top + 32, 0xFFFFFFFF);
        graphics.text(font, MATERIAL, left, top + 45, 0xFFFFFFFF);
        graphics.text(font, THICKNESS, left + formWidth - 46, top + 45, 0xFFFFFFFF);
        var lines = font.split(status, span);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), left, top + 126 + 9 * i, done.active ? 0xFFACEAB8 : 0xFFFFAAAA);
        }
        drawColumn(graphics, left + formWidth + 10);
    }

    private void drawColumn(GuiGraphicsExtractor graphics, int x) {
        graphics.text(font, Component.translatable("worldarchitect.layers.preview"), x, top + 27, 0xFFFFFFFF);
        int y = top + 42;
        graphics.fill(x, y, x + 48, y + 99, 0xFF181B22);
        if (validGenerator != null) {
            int start = 0;
            while (start < columnColors.length) {
                int end = start + 1;
                int color = columnColors[start];
                while (end < columnColors.length && columnColors[end] == color) {
                    end++;
                }
                graphics.fill(x + 1, y + 1 + start, x + 47, y + 1 + end, color);
                start = end;
            }
        }
    }

    private static int color(BlockState state) {
        return 0xFF000000 | state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private record LayerDraft(String material, String depth) {}
}

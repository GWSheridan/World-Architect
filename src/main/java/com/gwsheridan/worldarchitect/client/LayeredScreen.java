package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.config.LayeredProfile;
import com.gwsheridan.worldarchitect.config.TerrainMaterial;
import com.gwsheridan.worldarchitect.config.TerrainShape;
import com.gwsheridan.worldarchitect.worldgen.LayeredGenerator;
import com.gwsheridan.worldarchitect.worldgen.terrain.HeightfieldV1;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class LayeredScreen extends Screen {
    private static final Component MATERIAL = Component.translatable("worldarchitect.editor.material");
    private static final Component THICKNESS = Component.translatable("worldarchitect.layers.thickness");
    private final Screen parent;
    private final LayeredGenerator original;
    private final LongSupplier seed;
    private final Consumer<LayeredGenerator> save;
    private final List<LayerDraft> layers = new ArrayList<>();
    private String base;
    private TerrainShape shape;
    private int selected;
    private int top;
    private int left;
    private int formWidth;
    private int span;
    private Button done;
    private Component status = CommonComponents.EMPTY;
    private @Nullable LayeredProfile validProfile;

    LayeredScreen(Screen parent, LayeredGenerator generator, LongSupplier seed, Consumer<LayeredGenerator> save) {
        super(Component.translatable("worldarchitect.layers.title"));
        this.parent = parent;
        this.original = generator;
        this.seed = seed;
        this.save = save;
        this.base = BlockStateParser.serialize(original.profile().base());
        this.shape = original.profile().shape();
        for (LayeredProfile.Layer layer : original.profile().layers()) {
            layers.add(new LayerDraft(BlockStateParser.serialize(layer.material()), Integer.toString(layer.depth())));
        }
        selected = layers.isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        span = Math.min(340, width - 24);
        formWidth = span - 64;
        top = Math.max(4, (height - 215) / 2);
        left = (width - span) / 2;
        button("previous", left, 26, 32, () -> select(selected - 1)).active = selected >= 0;
        button("next", left + formWidth - 32, 26, 32, () -> select(selected + 1)).active = selected < layers.size() - 1;

        int materialWidth = formWidth - 62;
        EditBox material = entry(MATERIAL, left, 65, materialWidth, selected < 0 ? base : layers.get(selected).material(), 512);
        EditBox depth = entry(THICKNESS, left + materialWidth + 8, 65, 54, selected < 0 ? "" : layers.get(selected).depth(), 4);
        depth.setEditable(selected >= 0);
        int quarter = (formWidth - 12) / 4;
        button("add", left, 91, quarter, this::addLayer).active = layers.size() < LayeredProfile.MAX_LAYERS;
        button("remove", left + quarter + 4, 91, quarter, this::removeLayer).active = selected >= 0;
        button("up", left + 2 * (quarter + 4), 91, quarter, () -> moveLayer(-1)).active = selected > 0;
        button("down", left + 3 * (quarter + 4), 91, quarter, () -> moveLayer(1)).active = selected >= 0 && selected < layers.size() - 1;

        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.terrain.title"), button ->
                minecraft.gui.setScreen(new TerrainShapeScreen(this, shape, original.getMinY(), original.getGenDepth(), updated -> shape = updated)))
                .bounds(left, top + 121, formWidth, 20).build());
        int half = (span - 8) / 2;
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> apply())
                .bounds(left, top + 180, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + half + 8, top + 180, half, 20).build());

        material.setResponder(text -> {
            if (selected < 0) {
                base = text;
            } else {
                layers.set(selected, new LayerDraft(text, layers.get(selected).depth()));
            }
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
            return new LayeredProfile(original.profile().generatorVersion(), baseState, resolved, shape.elevation(), shape.relief()).validate();
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
        var result = readDraft().flatMap(profile -> original.withProfile(profile).contentProblem()
                .<DataResult<LayeredProfile>>map(problem -> DataResult.error(() -> problem)).orElseGet(() -> DataResult.success(profile)));
        validProfile = result.result().orElse(null);
        done.active = validProfile != null;
        status = result.error()
                .<Component>map(error -> Component.literal(error.message()))
                .orElseGet(() -> Component.translatable("worldarchitect.layers.valid"));
    }

    private void apply() {
        LayeredProfile profile = validProfile;
        if (profile != null) {
            save.accept(original.withProfile(profile));
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
        graphics.text(font, MATERIAL, left, top + 53, 0xFFFFFFFF);
        graphics.text(font, THICKNESS, left + formWidth - 54, top + 53, 0xFFFFFFFF);
        var lines = font.split(status, span);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), left, top + 149 + 9 * i, done.active ? 0xFFACEAB8 : 0xFFFFAAAA);
        }
        drawColumn(graphics, left + formWidth + 10);
    }

    private void drawColumn(GuiGraphicsExtractor graphics, int x) {
        graphics.text(font, Component.translatable("worldarchitect.layers.preview"), x, top + 27, 0xFFFFFFFF);
        int y = top + 42;
        graphics.fill(x, y, x + 48, y + 99, 0xFF181B22);
        LayeredProfile profile = validProfile;
        if (profile == null) {
            return;
        }
        int surface = HeightfieldV1.surface(seed.getAsLong(), 8, 8, profile.elevation(), profile.relief());
        int maxSurface = original.getMinY() + original.getGenDepth() - 4;
        int column = Math.clamp(surface, original.getMinY(), maxSurface) - original.getMinY() + 1;
        int depth = 0;
        graphics.fill(x + 1, y + 1, x + 47, y + 98, color(profile.base()));
        for (LayeredProfile.Layer layer : profile.layers()) {
            int end = Math.min(column, depth + layer.depth());
            int upper = depth * 97 / column;
            int lower = end * 97 / column;
            graphics.fill(x + 1, y + 1 + upper, x + 47, y + 1 + Math.max(upper + 1, lower), color(layer.material()));
            if (depth > 0) {
                graphics.fill(x + 1, y + 1 + upper, x + 47, y + 2 + upper, 0xFF615645);
            }
            depth = end;
            if (depth == column) {
                break;
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

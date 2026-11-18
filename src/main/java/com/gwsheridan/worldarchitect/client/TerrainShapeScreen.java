package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.config.TerrainShape;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class TerrainShapeScreen extends Screen {
    private static final Component ELEVATION = Component.translatable("worldarchitect.terrain.elevation");
    private static final Component RELIEF = Component.translatable("worldarchitect.terrain.relief");
    private final Screen parent;
    private final Consumer<TerrainShape> save;
    private final int minimum;
    private final int maximum;
    private String elevation;
    private String relief;
    private int left;
    private int top;
    private int half;
    private EditBox elevationField;
    private EditBox reliefField;
    private Button done;

    TerrainShapeScreen(Screen parent, TerrainShape initial, int minY, int height, Consumer<TerrainShape> save) {
        super(Component.translatable("worldarchitect.terrain.title"));
        this.parent = parent;
        this.save = save;
        this.minimum = minY;
        this.maximum = minY + height - 4;
        elevation = Integer.toString(initial.elevation());
        relief = Integer.toString(initial.relief());
    }

    @Override
    protected void init() {
        int span = Math.min(300, width - 20);
        left = (width - span) / 2;
        top = Math.max(6, (height - 138) / 2);
        half = (span - 8) / 2;
        elevationField = numberField(ELEVATION, elevation, left);
        reliefField = numberField(RELIEF, relief, left + half + 8);
        int third = (span - 16) / 3;
        addPreset("flat", TerrainShape.FLAT, left, third);
        addPreset("hills", TerrainShape.HILLS, left + third + 8, third);
        addPreset("highlands", TerrainShape.HIGHLANDS, left + 2 * (third + 8), third);
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            TerrainShape shape = parse();
            if (shape != null) {
                save.accept(shape);
                onClose();
            }
        }).bounds(left, top + 110, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + half + 8, top + 110, half, 20).build());
        elevationField.setResponder(input -> { elevation = input; done.active = parse() != null; });
        reliefField.setResponder(input -> { relief = input; done.active = parse() != null; });
        done.active = parse() != null;
    }

    private EditBox numberField(Component label, String text, int x) {
        EditBox field = addRenderableWidget(new EditBox(font, x, top + 40, half, 20, label));
        field.setMaxLength(5);
        field.setValue(text);
        return field;
    }

    private void addPreset(String key, TerrainShape shape, int x, int span) {
        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.terrain." + key), button -> {
            elevationField.setValue(Integer.toString(Math.clamp(shape.elevation(), minimum, maximum)));
            reliefField.setValue(Integer.toString(shape.relief()));
        }).bounds(x, top + 66, span, 20).build());
    }

    private @Nullable TerrainShape parse() {
        try {
            int y = Integer.parseInt(elevation);
            int variation = Integer.parseInt(relief);
            return y >= minimum && y <= maximum && variation >= 0 && variation <= TerrainShape.MAX_RELIEF
                    ? new TerrainShape(y, variation) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, top, 0xFFF1E7CE);
        graphics.text(font, ELEVATION, left, top + 27, 0xFFFFFFFF);
        graphics.text(font, RELIEF, left + half + 8, top + 27, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("worldarchitect.terrain.range", minimum, maximum), left, top + 92, done.active ? 0xFFCCCCCC : 0xFFFFAAAA);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

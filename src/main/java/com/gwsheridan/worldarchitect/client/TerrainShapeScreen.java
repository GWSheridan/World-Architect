package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.config.AdvancedTerrain;
import com.gwsheridan.worldarchitect.config.AdvancedTerrain.Landform;
import com.gwsheridan.worldarchitect.config.TerrainShape;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class TerrainShapeScreen extends Screen {
    private static final Component ELEVATION = Component.translatable("worldarchitect.terrain.elevation");
    private static final Component RELIEF = Component.translatable("worldarchitect.terrain.relief");
    private static final Component SCALE = Component.translatable("worldarchitect.terrain.scale");
    private static final Component SALT = Component.translatable("worldarchitect.terrain.seed_salt");
    private static final List<Optional<Landform>> LANDFORMS = Stream.concat(Stream.of(Optional.<Landform>empty()),
            Arrays.stream(Landform.values()).map(Optional::of)).toList();
    private final Screen parent;
    private final Consumer<TerrainShape> save;
    private final int minimum;
    private final int maximum;
    private String elevation;
    private String relief;
    private String scale;
    private String seedSalt;
    private Optional<Landform> landform;
    private int left;
    private int top;
    private int half;
    private EditBox elevationField;
    private EditBox reliefField;
    private EditBox scaleField;
    private EditBox saltField;
    private CycleButton<Optional<Landform>> landformButton;
    private Button done;

    TerrainShapeScreen(Screen parent, TerrainShape initial, int minY, int height, Consumer<TerrainShape> save) {
        super(Component.translatable("worldarchitect.terrain.title"));
        this.parent = parent;
        this.save = save;
        this.minimum = minY;
        this.maximum = minY + height - 4;
        elevation = Integer.toString(initial.elevation());
        relief = Integer.toString(initial.relief());
        scale = Integer.toString(initial.advanced().map(AdvancedTerrain::scale).orElse(AdvancedTerrain.DEFAULT_SCALE));
        seedSalt = Long.toString(initial.advanced().map(AdvancedTerrain::seedSalt).orElse(0L));
        landform = initial.advanced().map(AdvancedTerrain::landform);
    }

    @Override
    protected void init() {
        int span = Math.min(300, width - 20);
        left = (width - span) / 2;
        top = Math.max(2, (height - 174) / 2);
        half = (span - 8) / 2;
        elevationField = numberField(ELEVATION, elevation, left, 32, 5);
        reliefField = numberField(RELIEF, relief, left + half + 8, 32, 5);
        landformButton = addRenderableWidget(CycleButton.builder(TerrainShapeScreen::landformName, landform).withValues(LANDFORMS)
                .create(left, top + 56, span, 20, Component.translatable("worldarchitect.terrain.landform"), (button, choice) -> {
                    landform = choice;
                    refresh();
                }));
        scaleField = numberField(SCALE, scale, left, 94, 4);
        saltField = numberField(SALT, seedSalt, left + half + 8, 94, 20);
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
        }).bounds(left, top + 150, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + half + 8, top + 150, half, 20).build());
        elevationField.setResponder(input -> { elevation = input; refresh(); });
        reliefField.setResponder(input -> { relief = input; refresh(); });
        scaleField.setResponder(input -> { scale = input; refresh(); });
        saltField.setResponder(input -> { seedSalt = input; refresh(); });
        refresh();
    }

    private EditBox numberField(Component label, String text, int x, int y, int maxLength) {
        EditBox field = addRenderableWidget(new EditBox(font, x, top + y, half, 20, label));
        field.setMaxLength(maxLength);
        field.setValue(text);
        return field;
    }

    private void addPreset(String key, TerrainShape shape, int x, int span) {
        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.terrain." + key), button -> {
            elevationField.setValue(Integer.toString(Math.clamp(shape.elevation(), minimum, maximum)));
            reliefField.setValue(Integer.toString(shape.relief()));
            landform = Optional.empty();
            landformButton.setValue(landform);
            refresh();
        }).bounds(x, top + 118, span, 20).build());
    }

    private void refresh() {
        scaleField.active = landform.isPresent();
        saltField.active = landform.isPresent();
        done.active = parse() != null;
    }

    private @Nullable TerrainShape parse() {
        try {
            int y = Integer.parseInt(elevation);
            int variation = Integer.parseInt(relief);
            if (y < minimum || y > maximum || variation < 0 || variation > TerrainShape.MAX_RELIEF) {
                return null;
            }
            if (landform.isEmpty()) {
                return new TerrainShape(y, variation);
            }
            int span = Integer.parseInt(scale);
            long salt = Long.parseLong(seedSalt);
            return span >= AdvancedTerrain.MIN_SCALE && span <= AdvancedTerrain.MAX_SCALE
                    && (landform.orElseThrow() != Landform.FLOATING_ISLANDS || y > minimum)
                    ? new TerrainShape(y, variation, landform.map(choice -> new AdvancedTerrain(choice, span, salt))) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, top, 0xFFF1E7CE);
        graphics.text(font, ELEVATION, left, top + 19, 0xFFFFFFFF);
        graphics.text(font, RELIEF, left + half + 8, top + 19, 0xFFFFFFFF);
        graphics.text(font, SCALE, left, top + 81, 0xFFFFFFFF);
        graphics.text(font, SALT, left + half + 8, top + 81, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("worldarchitect.terrain.range", minimum, maximum), left, top + 140, done.active ? 0xFFCCCCCC : 0xFFFFAAAA);
    }

    private static Component landformName(Optional<Landform> landform) {
        return Component.translatable("worldarchitect.terrain.landform." + landform.map(Landform::getSerializedName).orElse("legacy"));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

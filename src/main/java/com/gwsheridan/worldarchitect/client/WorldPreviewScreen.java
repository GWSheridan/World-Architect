package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.worldgen.GenerationPreview;
import java.awt.Color;
import java.util.BitSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class WorldPreviewScreen extends Screen {
    private static final int GRID = 48;
    private static final int WORLD_LIMIT = 29_999_984;
    private static final int MAP_TOP = 54;
    private static final int[] STEPS = {1, 2, 4, 8, 16, 32, 64};
    private final Screen parent;
    private final Identifier dimension;
    private final long seed;
    private final GenerationPreview preview;
    private final GenerationPreview.Sample[] samples = new GenerationPreview.Sample[GRID * GRID];
    private final int[] colors = new int[GRID * GRID];
    private final BitSet markers = new BitSet(GRID * GRID);
    private View view = View.HEIGHT;
    private int zoom = 2;
    private int centerX;
    private int centerZ;
    private int mapLeft;
    private int mapSide;
    private Button zoomOut;
    private Button zoomIn;

    WorldPreviewScreen(Screen parent, Identifier dimension, long seed, GenerationPreview preview) {
        super(text("open"));
        this.parent = parent;
        this.dimension = dimension;
        this.seed = seed;
        this.preview = preview;
        zoom = preview.islands().isPresent() ? 3 : 2;
        resample();
    }

    @Override
    protected void init() {
        int span = Math.min(408, width - 24);
        int left = (width - span) / 2;
        mapSide = Math.min(Math.min(280, span), height - 107);
        mapLeft = (width - mapSide) / 2;
        addRenderableWidget(CycleButton.builder(View::label, view).withValues(View.values()).displayOnlyValue()
                .create(left, 28, 108, 20, text("view"), (button, value) -> {
                    view = value;
                    colorize();
                }));
        zoomOut = control("−", "zoom_out", left + span - 85, 28, () -> zoom(1));
        zoomIn = control("+", "zoom_in", left + span - 57, 28, () -> zoom(-1));
        control("⌂", "reset", left + span - 29, 28, () -> {
            centerX = 0;
            centerZ = 0;
            zoom = preview.islands().isPresent() ? 3 : 2;
            resample();
        });
        int controls = height - 26;
        control("←", "west", left, controls, () -> move(-1, 0));
        control("↑", "north", left + 28, controls, () -> move(0, -1));
        control("↓", "south", left + 56, controls, () -> move(0, 1));
        control("→", "east", left + 84, controls, () -> move(1, 0));
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(left + span - 74, controls, 74, 20).build());
        updateZoom();
    }

    private Button control(String label, String hint, int x, int y, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, 25, 20).tooltip(Tooltip.create(text(hint))).build());
    }

    private void zoom(int direction) {
        zoom = Math.clamp(zoom + direction, 0, STEPS.length - 1);
        resample();
    }

    private void move(int dx, int dz) {
        int distance = GRID / 3 * STEPS[zoom];
        int limit = WORLD_LIMIT - GRID * STEPS[STEPS.length - 1];
        centerX = Math.clamp(centerX + distance * dx, -limit, limit);
        centerZ = Math.clamp(centerZ + distance * dz, -limit, limit);
        resample();
    }

    private int start(int center) {
        return center - GRID / 2 * STEPS[zoom];
    }

    private void resample() {
        int step = STEPS[zoom];
        int x = start(centerX);
        int z = start(centerZ);
        for (int row = 0; row < GRID; row++) {
            for (int column = 0; column < GRID; column++) {
                samples[row * GRID + column] = preview.sample(x + column * step, z + row * step);
            }
        }
        markers.clear();
        preview.islands().ifPresent(island -> {
            for (int slot = 0; slot <= island.personalSlots(); slot++) {
                var marker = island.spawn(slot);
                int column = Math.floorDiv(marker.getX() - x, step);
                int row = Math.floorDiv(marker.getZ() - z, step);
                if (row >= 0 && row < GRID && column >= 0 && column < GRID) {
                    markers.set(row * GRID + column);
                }
            }
        });
        colorize();
        updateZoom();
    }

    private void updateZoom() {
        if (zoomIn != null) {
            zoomIn.active = zoom > 0;
            zoomOut.active = zoom < STEPS.length - 1;
        }
    }

    private void colorize() {
        int range = Math.max(1, preview.maxY() - preview.minY());
        for (int index = 0; index < samples.length; index++) {
            var sample = samples[index];
            if (view == View.BIOMES) {
                int hash = sample.biome().map(Identifier::hashCode).orElse(0);
                colors[index] = 0xFF000000 | Color.HSBtoRGB(Math.floorMod(hash, 360) / 360f, 0.59f, 0.78f);
            } else if (sample.occupied()) {
                int shade = 55 + Math.clamp((sample.surfaceY() - preview.minY()) * 190 / range, 0, 190);
                colors[index] = 0xFF000000 | shade << 16 | shade << 8 | Math.min(255, shade + 26);
            } else {
                colors[index] = 0xFF141E2B;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, 7, 0xFFF1E7CE);
        graphics.centeredText(font, font.plainSubstrByWidth(dimension.toString(), width - 24), width / 2, 17, 0xFFCCCCCC);
        graphics.fill(mapLeft - 1, MAP_TOP - 1, mapLeft + mapSide + 1, MAP_TOP + mapSide + 1, 0xFF7588A0);
        for (int row = 0; row < GRID; row++) {
            int start = 0;
            while (start < GRID) {
                int color = colors[row * GRID + start];
                int end = start + 1;
                while (end < GRID && colors[row * GRID + end] == color) {
                    end++;
                }
                graphics.fill(pixelX(start), pixelZ(row), pixelX(end), pixelZ(row + 1), color);
                start = end;
            }
        }
        for (int index = markers.nextSetBit(0); index >= 0; index = markers.nextSetBit(index + 1)) {
            int x = pixelX(index % GRID);
            int y = pixelZ(index / GRID);
            graphics.fill(x, y, Math.min(x + 3, mapLeft + mapSide), Math.min(y + 3, MAP_TOP + mapSide), 0xFFFFD560);
        }
        Component status = cursor(mouseX, mouseY);
        graphics.centeredText(font, font.plainSubstrByWidth(status.getString(), width - 20), width / 2, height - 48, 0xFFFFFFFF);
        Component caption = text(preview.islands().isPresent() ? "island_caption" : "surface_caption");
        graphics.centeredText(font, font.plainSubstrByWidth(caption.getString(), width - 20), width / 2, height - 38, 0xFFCCCCCC);
    }

    private int pixelX(int column) {
        return mapLeft + column * mapSide / GRID;
    }

    private int pixelZ(int row) {
        return MAP_TOP + row * mapSide / GRID;
    }

    private Component cursor(int mouseX, int mouseY) {
        if (mouseX < mapLeft || mouseX >= mapLeft + mapSide || mouseY < MAP_TOP || mouseY >= MAP_TOP + mapSide) {
            return Component.translatable("worldarchitect.preview.center", centerX, centerZ, STEPS[zoom], seed);
        }
        int column = ((mouseX - mapLeft + 1) * GRID - 1) / mapSide;
        int row = ((mouseY - MAP_TOP + 1) * GRID - 1) / mapSide;
        var sample = samples[row * GRID + column];
        return Component.translatable("worldarchitect.preview.point", start(centerX) + column * STEPS[zoom],
                start(centerZ) + row * STEPS[zoom], sample.occupied() ? Integer.toString(sample.surfaceY()) : text("empty").getString(),
                sample.biome().map(Identifier::toString).orElse("—"));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static Component text(String key) {
        return Component.translatable("worldarchitect.preview." + key);
    }

    private enum View {
        HEIGHT, BIOMES;

        Component label() {
            return text(this == HEIGHT ? "height" : "biomes");
        }
    }
}

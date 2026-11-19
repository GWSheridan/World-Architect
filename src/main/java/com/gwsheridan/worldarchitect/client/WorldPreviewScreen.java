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
    private com.gwsheridan.worldarchitect.rules.RuleSet rules = com.gwsheridan.worldarchitect.rules.RuleSet.EMPTY;
    private final GenerationPreview.Sample[] samples = new GenerationPreview.Sample[GRID * GRID];
    private final int[] colors = new int[GRID * GRID];
    private final BitSet markers = new BitSet(GRID * GRID);
    private View view = View.HEIGHT;
    private int zoom = 2;
    private int centerX;
    private int centerZ;
    private int mapLeft;
    private int mapSide;
    private int nextSample;
    private int nextColumn;
    private final net.minecraft.world.level.NoiseColumn[] section = new net.minecraft.world.level.NoiseColumn[GRID];
    private double yaw = Math.PI / 4;
    private java.util.List<Integer> renderOrder = java.util.List.of();
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

    WorldPreviewScreen(Screen parent, Identifier dimension, long seed, GenerationPreview preview, com.gwsheridan.worldarchitect.rules.RuleSet rules) {
        this(parent, dimension, seed, preview); this.rules = rules;
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
        control("<", "rotate", left + 114, 28, () -> rotate(-1));
        control(">", "rotate", left + 142, 28, () -> rotate(1));
        addRenderableWidget(Button.builder(Component.literal("Go to"), b -> minecraft.gui.setScreen(new StudioFormScreen(this,
                "Preview coordinates", "Coordinates sample base terrain using the current draft seed.", java.util.List.of(
                new StudioFormScreen.Field("X", Integer.toString(centerX)), new StudioFormScreen.Field("Z", Integer.toString(centerZ))), values -> {
                    for (var value : values) if (Math.abs((long)Integer.parseInt(value)) > WORLD_LIMIT - GRID * 64) return "Coordinates exceed the preview boundary.";
                    return "";
                }, values -> { centerX = Integer.parseInt(values.get(0)); centerZ = Integer.parseInt(values.get(1)); resample(); })))
                .bounds(left + 114, height - 26, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rules"), b -> minecraft.gui.setScreen(new RuleInspectorScreen(this, dimension, seed, rules, preview)))
                .bounds(left + 182, height - 26, Math.max(36, span - 260), 20).build()).active = !rules.isEmpty();
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
        nextSample = 0; nextColumn = 0;
        java.util.Arrays.fill(samples, null); java.util.Arrays.fill(section, null);
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
            if (sample == null) { colors[index] = 0xFF202733; continue; }
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
        graphics.fill(mapLeft, MAP_TOP, mapLeft + mapSide, MAP_TOP + mapSide, 0xFF141E2B);
        graphics.enableScissor(mapLeft, MAP_TOP, mapLeft + mapSide, MAP_TOP + mapSide);
        if (view == View.TERRAIN) renderTerrain(graphics);
        else if (view == View.SECTION) renderSection(graphics);
        else for (int row = 0; row < GRID; row++) {
            int start = 0;
            while (start < GRID) {
                int color = arenaEdge(row * GRID + start) ? 0xFF80DFFF : colors[row * GRID + start];
                int end = start + 1;
                while (end < GRID && !arenaEdge(row * GRID + end) && colors[row * GRID + end] == color) {
                    end++;
                }
                graphics.fill(pixelX(start), pixelZ(row), pixelX(end), pixelZ(row + 1), color);
                start = end;
            }
        }
        if (view != View.TERRAIN && view != View.SECTION) for (int index = markers.nextSetBit(0); index >= 0; index = markers.nextSetBit(index + 1)) {
            int x = pixelX(index % GRID);
            int y = pixelZ(index / GRID);
            graphics.fill(x, y, Math.min(x + 3, mapLeft + mapSide), Math.min(y + 3, MAP_TOP + mapSide), 0xFFFFD560);
        }
        graphics.disableScissor();
        Component status = cursor(mouseX, mouseY);
        graphics.centeredText(font, font.plainSubstrByWidth(status.getString(), width - 20), width / 2, height - 48, 0xFFFFFFFF);
        Component caption = Component.literal("Base terrain · decorations and structures excluded" + (nextSample < samples.length ? " · sampling " + nextSample * 100 / samples.length + "%" : ""));
        graphics.centeredText(font, font.plainSubstrByWidth(caption.getString(), width - 20), width / 2, height - 38, 0xFFCCCCCC);
    }

    private int pixelX(int column) {
        return mapLeft + column * mapSide / GRID;
    }

    private int pixelZ(int row) {
        return MAP_TOP + row * mapSide / GRID;
    }

    private Component cursor(int mouseX, int mouseY) {
        if (view == View.TERRAIN || mouseX < mapLeft || mouseX >= mapLeft + mapSide || mouseY < MAP_TOP || mouseY >= MAP_TOP + mapSide) {
            return Component.translatable("worldarchitect.preview.center", centerX, centerZ, STEPS[zoom], seed);
        }
        int column = ((mouseX - mapLeft + 1) * GRID - 1) / mapSide;
        int row = ((mouseY - MAP_TOP + 1) * GRID - 1) / mapSide;
        if (view == View.SECTION) {
            if (section[column] == null) return Component.literal("Sampling cross-section…");
            int y = preview.maxY() - (mouseY - MAP_TOP) * (preview.maxY() - preview.minY() + 1) / mapSide;
            return Component.literal((start(centerX) + column * STEPS[zoom]) + ", " + y + ", " + centerZ + " · "
                    + net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(section[column].getBlock(y)));
        }
        var sample = samples[row * GRID + column];
        if (sample == null) return Component.literal("Sampling base terrain…");
        return Component.translatable("worldarchitect.preview.point", start(centerX) + column * STEPS[zoom],
                start(centerZ) + row * STEPS[zoom], sample.occupied() ? Integer.toString(sample.surfaceY()) : text("empty").getString(),
                sample.biome().map(Identifier::toString).orElse("—"));
    }

    @Override public void tick() {
        super.tick();
        long deadline = System.nanoTime() + 3_000_000;
        int count = 0;
        while (nextSample < samples.length && count++ < 64 && System.nanoTime() < deadline) {
            int index = nextSample++;
            samples[index] = preview.sample(start(centerX) + index % GRID * STEPS[zoom], start(centerZ) + index / GRID * STEPS[zoom]);
        }
        if (count > 0) colorize();
        if (view == View.SECTION) {
            count = 0;
            while (nextColumn < GRID && count++ < 4 && System.nanoTime() < deadline) {
                section[nextColumn] = preview.column(start(centerX) + nextColumn * STEPS[zoom], centerZ); nextColumn++;
            }
        }
    }

    private void renderTerrain(GuiGraphicsExtractor g) {
        double scale = mapSide / (GRID * 1.6);
        if (renderOrder.isEmpty()) rotate(0);
        for (int index : renderOrder) {
            var sample = samples[index]; if (sample == null || !sample.occupied()) continue;
            double x = index % GRID - GRID / 2.0, z = index / GRID - GRID / 2.0;
            double y = (sample.surfaceY() - (preview.minY() + preview.maxY()) / 2.0) * GRID / Math.max(1, preview.maxY() - preview.minY());
            int cx = mapLeft + mapSide / 2, cy = MAP_TOP + mapSide / 2;
            int color = markers.get(index) ? 0xFFFFD560 : arenaEdge(index) ? 0xFF80DFFF : colors[index];
            IsoCanvas.quad(g, color, IsoCanvas.point(x,y,z,yaw,scale,cx,cy), IsoCanvas.point(x+1,y,z,yaw,scale,cx,cy),
                    IsoCanvas.point(x+1,y,z+1,yaw,scale,cx,cy), IsoCanvas.point(x,y,z+1,yaw,scale,cx,cy));
        }
    }

    private void rotate(int direction) {
        yaw += direction * Math.PI / 4;
        renderOrder = java.util.stream.IntStream.range(0, samples.length).boxed().sorted(java.util.Comparator.comparingDouble(i ->
                (i % GRID - GRID / 2.0) * Math.sin(yaw) + (i / GRID - GRID / 2.0) * Math.cos(yaw))).toList();
    }

    private boolean arenaEdge(int index) {
        if (!preview.endArena()) return false;
        int x = start(centerX) + index % GRID * STEPS[zoom], z = start(centerZ) + index / GRID * STEPS[zoom], step = STEPS[zoom];
        return (x <= -256 && x + step > -256 || x <= 255 && x + step > 255) && z >= -256 && z <= 255
                || (z <= -256 && z + step > -256 || z <= 255 && z + step > 255) && x >= -256 && x <= 255;
    }

    private void renderSection(GuiGraphicsExtractor g) {
        int rows = Math.min(160, preview.maxY() - preview.minY() + 1);
        for (int x = 0; x < GRID; x++) for (int row = 0; row < rows; row++) {
            var column = section[x]; if (column == null) continue;
            int y = preview.maxY() - row * (preview.maxY() - preview.minY() + 1) / rows;
            var block = column.getBlock(y);
            int color = block.isAir() ? 0xFF141E2B : !block.getFluidState().isEmpty() ? 0xFF397CC4
                    : 0xFF000000 | Color.HSBtoRGB(Math.floorMod(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).hashCode(), 360) / 360f, .3f, .75f);
            g.fill(pixelX(x), MAP_TOP + row * mapSide / rows, pixelX(x + 1), MAP_TOP + (row + 1) * mapSide / rows, color);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static Component text(String key) {
        return Component.translatable("worldarchitect.preview." + key);
    }

    private enum View {
        HEIGHT, BIOMES, TERRAIN, SECTION;

        Component label() {
            return Component.literal(switch(this) { case HEIGHT -> "Height map"; case BIOMES -> "Biome map"; case TERRAIN -> "3D terrain"; case SECTION -> "Cross-section"; });
        }
    }
}

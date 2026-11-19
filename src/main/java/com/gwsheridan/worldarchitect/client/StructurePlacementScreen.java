package com.gwsheridan.worldarchitect.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** The client displays server-owned geometry. Only the server can approve a placement. */
final class StructurePlacementScreen extends Screen {
    private final CompoundTag visual;
    private final int[] cells, size, origin;
    private double yaw = Math.PI / 4;
    private Integer[] order;
    StructurePlacementScreen(CompoundTag visual) {
        super(Component.literal("Structure placement")); this.visual = visual.copy();
        cells = visual.getIntArray("cells").orElseThrow(); size = visual.getIntArray("size").orElseThrow(); origin = visual.getIntArray("origin").orElseThrow();
        sort();
    }
    private void sort() {
        order = java.util.stream.IntStream.range(0, cells.length / 2).boxed().sorted(Comparator.comparingDouble(i -> {
            int pos = cells[i * 2]; return (pos & 31) * Math.sin(yaw) + (pos >> 10 & 31) * Math.cos(yaw);
        })).toArray(Integer[]::new);
    }
    @Override protected void init() {
        int span = Math.min(440, width - 24), left = (width - span) / 2, fifth = (span - 16) / 5;
        add("Orbit left", left, 28, (span - 4) / 2, () -> { yaw -= Math.PI / 4; sort(); });
        add("Orbit right", left + (span + 4) / 2, 28, (span - 4) / 2, () -> { yaw += Math.PI / 4; sort(); });
        boolean viewer = visual.getBooleanOr("viewer", false);
        if (!viewer) {
            add("Rotate", left, height - 76, fifth, () -> restage(nextRotation(), visual.getStringOr("mirror", "none"), origin));
            add("Mirror", left + fifth + 4, height - 76, fifth, () -> restage(visual.getStringOr("rotation", "none"), nextMirror(), origin));
            add("Position", left + 2 * (fifth + 4), height - 76, fifth, this::coordinates);
            add("World ghost", left + 3 * (fifth + 4), height - 76, fifth, () -> { ClientStructurePreview.showGhost(visual); minecraft.gui.setScreen(null); });
            add("Confirm", left + 4 * (fifth + 4), height - 76, fifth, () -> {
                command("wa_structure confirm " + visual.getStringOr("token", "")); ClientStructurePreview.clear(); minecraft.gui.setScreen(null);
            }).active = visual.getBooleanOr("valid", false);
        }
        add("Close", left, height - 26, span, this::onClose);
    }
    private Button add(String text, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, width, 20).build());
    }
    private String nextRotation() { var list = List.of("none", "cw90", "cw180", "ccw90"); return list.get((list.indexOf(visual.getStringOr("rotation", "none")) + 1) % 4); }
    private String nextMirror() { var list = List.of("none", "left_right", "front_back"); return list.get((list.indexOf(visual.getStringOr("mirror", "none")) + 1) % 3); }
    private void coordinates() {
        minecraft.gui.setScreen(new StudioFormScreen(this, "Placement origin", "Only loaded chunks can be used. Confirmation rechecks the destination.", List.of(
                new StudioFormScreen.Field("X", "" + origin[0]), new StudioFormScreen.Field("Y", "" + origin[1]), new StudioFormScreen.Field("Z", "" + origin[2])),
                values -> { for (String value : values) Integer.parseInt(value); return ""; },
                values -> restage(visual.getStringOr("rotation", "none"), visual.getStringOr("mirror", "none"), values.stream().mapToInt(Integer::parseInt).toArray())));
    }
    private void restage(String rotation, String mirror, int[] pos) {
        command("wa_structure stage " + visual.getStringOr("id", "") + " " + pos[0] + " " + pos[1] + " " + pos[2] + " " + rotation + " " + mirror);
    }
    private void command(String command) { if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(command); }
    @Override public void onClose() { ClientStructurePreview.clear(); minecraft.gui.setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float tick) {
        super.extractRenderState(g, mx, my, tick);
        g.centeredText(font, Component.literal(visual.getStringOr("id", "Structure")), width / 2, 9, 0xFFF1E7CE);
        int top = 52, bottom = height - 86;
        double scale = Math.max(.5, Math.min((width - 32.0) / (size[0] + size[2] + 2), (bottom - top) / (size[1] + (size[0] + size[2]) * .5 + 2)));
        for (int i : order) {
            int p = cells[i * 2]; double x = (p & 31) - size[0] / 2.0, y = (p >> 5 & 31) - size[1] / 2.0, z = (p >> 10 & 31) - size[2] / 2.0;
            int color = 0xFF000000 | java.awt.Color.HSBtoRGB(Math.floorMod(cells[i * 2 + 1], 360) / 360f, .45f, .83f);
            int cx = width / 2, cy = (top + bottom) / 2;
            IsoCanvas.quad(g, color, IsoCanvas.point(x,y+1,z,yaw,scale,cx,cy), IsoCanvas.point(x+1,y+1,z,yaw,scale,cx,cy),
                    IsoCanvas.point(x+1,y+1,z+1,yaw,scale,cx,cy), IsoCanvas.point(x,y+1,z+1,yaw,scale,cx,cy));
            IsoCanvas.quad(g, shade(color), IsoCanvas.point(x,y,z+1,yaw,scale,cx,cy), IsoCanvas.point(x+1,y,z+1,yaw,scale,cx,cy),
                    IsoCanvas.point(x+1,y+1,z+1,yaw,scale,cx,cy), IsoCanvas.point(x,y+1,z+1,yaw,scale,cx,cy));
        }
        for (var marker : visual.getListOrEmpty("markers")) if (marker instanceof CompoundTag tag) {
            int[] p = tag.getIntArray("pos").orElse(new int[3]);
            int[] dot = IsoCanvas.point(p[0] - size[0]/2.0+.5, p[1] - size[1]/2.0+.5, p[2] - size[2]/2.0+.5, yaw, scale, width/2, (top+bottom)/2);
            g.fill(dot[0]-2, dot[1]-2, dot[0]+3, dot[1]+3, 0xFFFFD560);
            g.text(font, tag.getStringOr("name", "spawn") + " · " + tag.getStringOr("facing", ""), dot[0]+4, dot[1], 0xFFFFD560);
        }
        var lines = font.split(Component.literal(visual.getStringOr("status", "")), width - 24);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), 12, height - 51 + i * 10, visual.getBooleanOr("valid", false) ? 0xFFACEAB8 : 0xFFFFC080);
    }
    private static int shade(int color) { return 0xFF000000 | (color >> 16 & 255) * 3 / 4 << 16 | (color >> 8 & 255) * 3 / 4 << 8 | (color & 255) * 3 / 4; }
}

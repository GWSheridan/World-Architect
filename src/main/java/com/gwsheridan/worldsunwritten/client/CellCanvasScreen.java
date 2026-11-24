package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

/** Geometry-only canvas; opening or editing it never requests a world chunk. */
final class CellCanvasScreen extends Screen {
    private final Screen parent;
    private final CellCanvas draft;
    private final long seed;
    private final Consumer<CellWorldProfile> save;
    private final CellWorldProfile initial;
    private String brush, status = "Select cells or use Go to, then open Tools.";
    private Cell anchor = Cell.ORIGIN, cursor = Cell.ORIGIN;
    private int radius = 4, mapY = 55, mapH;
    private boolean painting;

    CellCanvasScreen(
            Screen parent, CellWorldProfile profile, long seed, Consumer<CellWorldProfile> save) {
        super(Component.literal("World Layout Designer"));
        this.parent = parent;
        initial = profile;
        draft = new CellCanvas(profile, seed);
        this.seed = seed;
        this.save = save;
        brush = profile.start();
        if (profile.progressive()) radius = Math.min(radius, profile.growth().radius());
        draft.select(List.of(Cell.ORIGIN));
    }

    @Override
    protected void init() {
        int span = width - 24, left = 12, unit = (span - 20) / 5;
        mapH = Math.max(40, height - 125);
        button(
                painting ? "Paint" : "Select",
                left,
                29,
                unit,
                () -> {
                    painting = !painting;
                    rebuildWidgets();
                });
        button(
                "Brush",
                left + unit + 5,
                29,
                unit,
                () ->
                        choose(
                                "Cell brush",
                                draft.profile().tiles().stream()
                                        .map(CellWorldProfile.Tile::id)
                                        .toList(),
                                v -> brush = v));
        button("Tools", left + (unit + 5) * 2, 29, unit, this::tools);
        button("Go to", left + (unit + 5) * 3, 29, unit, this::go);
        button(
                "Zoom",
                left + (unit + 5) * 4,
                29,
                unit,
                () ->
                        choose(
                                "Visible radius",
                                List.of("1", "2", "4", "8", "15"),
                                v -> radius = Integer.parseInt(v)));
        button(
                "Apply layout",
                12,
                height - 26,
                (span - 6) / 2,
                () ->
                        act(
                                () -> {
                                    save.accept(draft.profile());
                                    minecraft.gui.setScreen(parent);
                                }));
        button("Cancel", 15 + span / 2, height - 26, (span - 6) / 2, this::onClose);
    }

    private void button(String text, int x, int y, int w, Runnable action) {
        addRenderableWidget(
                Button.builder(Component.literal(text), b -> action.run())
                        .bounds(x, y, w, 20)
                        .build());
    }

    private void choose(String title, List<String> values, Consumer<String> action) {
        minecraft.gui.setScreen(
                new StudioChoiceScreen(
                        this,
                        title,
                        values,
                        v -> {
                            minecraft.gui.setScreen(this);
                            act(() -> action.accept(v));
                        }));
    }

    private void form(
            String title, List<StudioFormScreen.Field> fields, Consumer<List<String>> action) {
        minecraft.gui.setScreen(
                new StudioFormScreen(
                        this,
                        title,
                        "Coordinates refer to the saved cell grid; existing worlds are never"
                                + " edited.",
                        fields,
                        v -> "",
                        action));
    }

    private void go() {
        form(
                "Select coordinates",
                List.of(
                        new StudioFormScreen.Field("q", Integer.toString(anchor.q())),
                        new StudioFormScreen.Field("r", Integer.toString(anchor.r()))),
                v -> {
                    var c = new Cell(Integer.parseInt(v.get(0)), Integer.parseInt(v.get(1)));
                    draft.select(List.of(c));
                    anchor = c;
                    cursor = c;
                });
    }

    private void tools() {
        choose(
                "Layout tools",
                List.of(
                        "Paint selection",
                        "Clear assignments",
                        "Select rectangle",
                        "Copy selection",
                        "Paste at cursor",
                        "Rotate clipboard layout",
                        "Mirror clipboard layout",
                        "Save named group",
                        "Select named group",
                        "Undo",
                        "Redo"),
                v -> {
                    switch (v) {
                        case "Paint selection" -> draft.paint(brush);
                        case "Clear assignments" -> draft.erase();
                        case "Copy selection" -> draft.copy(cursor);
                        case "Paste at cursor" -> draft.paste(cursor);
                        case "Rotate clipboard layout" -> draft.transformClipboard(false);
                        case "Mirror clipboard layout" -> draft.transformClipboard(true);
                        case "Undo" -> draft.undo();
                        case "Redo" -> draft.redo();
                        case "Select rectangle" ->
                                form(
                                        "Select cell rectangle",
                                        List.of(
                                                new StudioFormScreen.Field(
                                                        "Minimum q", anchor.q() + ""),
                                                new StudioFormScreen.Field(
                                                        "Minimum r", anchor.r() + ""),
                                                new StudioFormScreen.Field(
                                                        "Maximum q", anchor.q() + ""),
                                                new StudioFormScreen.Field(
                                                        "Maximum r", anchor.r() + "")),
                                        a -> {
                                            int q = Integer.parseInt(a.get(0)),
                                                    r = Integer.parseInt(a.get(1)),
                                                    x = Integer.parseInt(a.get(2)),
                                                    z = Integer.parseInt(a.get(3));
                                            new Cell(q, r);
                                            new Cell(x, z);
                                            if (x < q
                                                    || z < r
                                                    || ((long) x - q + 1) * ((long) z - r + 1)
                                                            > 1024)
                                                throw new IllegalArgumentException(
                                                        "Select an ordered rectangle of at most"
                                                                + " 1024 cells.");
                                            var cells = new ArrayList<Cell>();
                                            for (int i = q; i <= x; i++)
                                                for (int j = r; j <= z; j++)
                                                    cells.add(new Cell(i, j));
                                            draft.select(cells);
                                        });
                        case "Save named group" ->
                                form(
                                        "Name selection",
                                        List.of(new StudioFormScreen.Field("Group ID", "region")),
                                        a -> draft.group(a.getFirst()));
                        case "Select named group" ->
                                choose(
                                        "Named groups",
                                        draft
                                                .profile()
                                                .campaign()
                                                .orElse(CellCampaign.EMPTY)
                                                .groups()
                                                .stream()
                                                .map(CellCampaign.Group::id)
                                                .toList(),
                                        draft::selectGroup);
                        default -> throw new IllegalArgumentException("Unknown layout action");
                    }
                    status = draft.selection().size() + " selected · brush " + brush;
                });
    }

    private double scale() {
        var layout = draft.profile().layout();
        double square = (radius * 2 + 1) * layout.size();
        double hexWidth = Math.sqrt(3) * square;
        double hexHeight = (radius * 3 + 2) * layout.size();
        double spanX =
                switch (layout.shape()) {
                    case SQUARE -> square;
                    case POINTY_HEX -> hexWidth;
                    case FLAT_HEX -> hexHeight;
                };
        double spanZ =
                switch (layout.shape()) {
                    case SQUARE -> square;
                    case POINTY_HEX -> hexHeight;
                    case FLAT_HEX -> hexWidth;
                };
        return Math.min((width - 32) / spanX, (mapH - 8) / spanZ);
    }

    private Cell at(double x, double y) {
        var center = draft.profile().layout().centre(anchor);
        return draft.profile()
                .layout()
                .at(
                        (int) Math.floor(center.x() + (x - width / 2.0) / scale()),
                        (int) Math.floor(center.z() + (y - mapY - mapH / 2.0) / scale()));
    }

    private boolean map(double x, double y) {
        return x >= 12 && x < width - 12 && y >= mapY && y < mapY + mapH;
    }

    private void click(double x, double y) {
        var c = at(x, y);
        cursor = c;
        act(
                () -> {
                    if (painting) {
                        draft.select(List.of(c));
                        draft.paint(brush);
                    } else {
                        var selected = new HashSet<>(draft.selection());
                        if (!selected.add(c)) selected.remove(c);
                        draft.select(selected);
                    }
                    status =
                            "Cursor "
                                    + c.key()
                                    + " · "
                                    + draft.selection().size()
                                    + " selected · "
                                    + brush;
                });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (e.button() == 0 && map(e.x(), e.y())) {
            click(e.x(), e.y());
            return true;
        }
        return super.mouseClicked(e, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (painting && e.button() == 0 && map(e.x(), e.y())) {
            click(e.x(), e.y());
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            status = Objects.toString(e.getMessage(), "Layout edit failed");
        }
    }

    @Override
    public void onClose() {
        if (!initial.equals(draft.profile()))
            minecraft.gui.setScreen(
                    new StudioConfirmScreen(
                            answer -> minecraft.gui.setScreen(answer ? parent : this),
                            Component.literal("Discard layout changes?"),
                            Component.literal("The parent project keeps its last applied layout."),
                            Component.literal("Discard changes"),
                            Component.literal("Keep editing")));
        else minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float tick) {
        super.extractRenderState(g, mx, my, tick);
        g.centeredText(font, title, width / 2, 9, UnwrittenTheme.TEXT);
        g.fill(12, mapY, width - 12, mapY + mapH, 0xFF121A23);
        var colors = new HashMap<Cell, Integer>();
        var profile = draft.profile();
        var layout = profile.layout();
        var center = layout.centre(anchor);
        double scale = scale();
        for (int y = mapY; y < mapY + mapH; y += 2)
            for (int x = 12; x < width - 12; x += 2) {
                int wx = (int) Math.floor(center.x() + (x - width / 2.0) / scale),
                        wz = (int) Math.floor(center.z() + (y - mapY - mapH / 2.0) / scale);
                var cell = layout.at(wx, wz);
                if (profile.progressive() && layout.distance(cell) > profile.growth().radius()
                        || !layout.contains(cell, wx, wz)) continue;
                int color =
                        colors.computeIfAbsent(
                                cell,
                                c ->
                                        0xFF000000
                                                | java.awt.Color.HSBtoRGB(
                                                                (profile.tile(seed, 0, c)
                                                                                        .id()
                                                                                        .hashCode()
                                                                                & 65535)
                                                                        / 65536f,
                                                                .42f,
                                                                .7f)
                                                        & 0xFFFFFF);
                if (draft.selection().contains(cell)
                        && layout.edge(cell, wx + .5, wz + .5) < Math.max(1, 2 / scale))
                    color = UnwrittenTheme.TEXT;
                g.fill(x, y, Math.min(x + 2, width - 12), Math.min(y + 2, mapY + mapH), color);
            }
        g.text(
                font,
                font.plainSubstrByWidth(status, width - 24),
                12,
                height - 61,
                UnwrittenTheme.MUTED);
        g.text(
                font,
                font.plainSubstrByWidth(
                        "Transforms move cells; templates keep their facing.", width - 24),
                12,
                height - 48,
                UnwrittenTheme.MUTED);
    }
}

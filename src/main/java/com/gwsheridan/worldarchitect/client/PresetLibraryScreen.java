package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.modpack.PresetStudio;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Browsing is separate from the draft: selecting a row never loads it. */
final class PresetLibraryScreen extends Screen {
    private final Screen parent;
    private final List<PresetStudio.Revision> revisions;
    private final Consumer<PresetStudio.Revision> load;
    private String query = "";
    private PresetStudio.Revision selected;
    private RevisionList rows;
    private Button open;

    PresetLibraryScreen(Screen parent, List<PresetStudio.Revision> revisions, Consumer<PresetStudio.Revision> load) {
        super(Component.literal("Preset library"));
        this.parent = parent;
        this.revisions = List.copyOf(revisions);
        this.load = load;
    }

    @Override protected void init() {
        int span = Math.min(480, width - 24), left = (width - span) / 2;
        var search = addRenderableWidget(new EditBox(font, left, 28, span, 20, Component.literal("Search presets")));
        search.setMaxLength(256);
        search.setHint(Component.literal("Search name, version, author or lock"));
        search.setValue(query);
        rows = addRenderableWidget(new RevisionList(span, height - 86));
        rows.setX(left);
        open = addRenderableWidget(Button.builder(Component.literal("Load selected"), b -> {
            if (selected != null) load.accept(selected);
        }).bounds(left, height - 26, (span - 8) / 2, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left + (span + 8) / 2, height - 26, (span - 8) / 2, 20).build());
        search.setResponder(value -> { query = value; refresh(); });
        refresh();
        setInitialFocus(search);
    }

    private void refresh() {
        String filter = query.strip().toLowerCase(Locale.ROOT);
        var matches = revisions.stream().filter(r -> (r.label() + " " + r.manifest().title() + " " + r.manifest().author())
                .toLowerCase(Locale.ROOT).contains(filter)).map(Row::new).toList();
        var previous = selected;
        rows.replaceEntries(matches);
        rows.setSelected(matches.stream().filter(r -> r.revision.equals(previous)).findFirst().orElse(null));
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
        super.extractRenderState(graphics, x, y, tick);
        graphics.centeredText(font, title, width / 2, 9, 0xFFF1E7CE);
        if (rows.children().isEmpty()) graphics.centeredText(font, Component.literal("No matching saved versions"), width / 2, 62, 0xFFCCCCCC);
    }

    private final class RevisionList extends ObjectSelectionList<Row> {
        RevisionList(int width, int height) { super(PresetLibraryScreen.this.minecraft, width, height, 54, 34); }
        @Override public int getRowWidth() { return getWidth() - 16; }
        @Override public void setSelected(Row row) { super.setSelected(row); selected = row == null ? null : row.revision; open.active = row != null; }
    }
    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final PresetStudio.Revision revision;
        Row(PresetStudio.Revision revision) { this.revision = revision; }
        @Override public Component getNarration() { return Component.literal(revision.manifest().title() + " " + revision.label() + " by " + revision.manifest().author()); }
        @Override public void extractContent(GuiGraphicsExtractor g, int x, int y, boolean hover, float tick) {
            g.text(font, font.plainSubstrByWidth(revision.manifest().title() + " · " + revision.release(), getWidth() - 8), getX() + 4, getY() + 3, 0xFFFFFFFF);
            g.text(font, font.plainSubstrByWidth(revision.name() + " · " + revision.manifest().author() + " · " + revision.manifest().lock().getSerializedName(), getWidth() - 8), getX() + 4, getY() + 16, 0xFFCCCCCC);
        }
    }
}

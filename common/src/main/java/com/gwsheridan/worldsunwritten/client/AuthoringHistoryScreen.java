package com.gwsheridan.worldsunwritten.client;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AuthoringHistoryScreen extends Screen {
    record Action(String label, BooleanSupplier enabled, Runnable run) {}
    private final Screen parent;
    private final ClientDraftJournal journal;
    private final Consumer<JsonElement> restore;
    private final Supplier<JsonElement> snapshot;
    private final List<Action> actions;
    private String error = "";
    AuthoringHistoryScreen(Screen parent, ClientDraftJournal journal, Supplier<JsonElement> snapshot, Consumer<JsonElement> restore, List<Action> actions) {
        super(Component.literal("Draft history and recovery")); this.parent = parent; this.journal = journal;
        this.snapshot = snapshot; this.restore = restore; this.actions = List.copyOf(actions);
    }
    @Override protected void init() {
        int span = Math.min(420, width - 24), left = (width - span) / 2, half = (span - 8) / 2;
        button("Undo", left, 34, half, ()->journal.canUndo()&&!journal.pending(), () -> {journal.step(true,restore,snapshot);onClose();});
        button("Redo", left + half + 8, 34, half, ()->journal.canRedo()&&!journal.pending(), () -> {journal.step(false,restore,snapshot);onClose();});
        button("Restore recovery", left, 58, half, journal::canRecover, () -> { restore.accept(journal.recovery()); journal.resolved(snapshot.get()); onClose(); });
        button("Discard recovery", left + half + 8, 58, half, journal::pending, () -> { journal.discard(); rebuildWidgets(); });
        int columns=86+Math.max(0,actions.size()-1)*23+20>height-58?2:1;
        for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            button(action.label(), left+(i%columns)*(half+8), 86+(i/columns)*23, columns==1?span:half, ()->!journal.pending()&&action.enabled().getAsBoolean(), () -> { action.run().run(); if(minecraft.gui.screen()==this)onClose(); });
        }
        button("Back", left, height - 26, span, () -> true, this::onClose);
    }
    private void button(String label, int x, int y, int width, BooleanSupplier enabled, Checked action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> { try { action.run(); } catch (Exception e) { error = e.getMessage(); rebuildWidgets(); } }).bounds(x, y, width, 20).build()).active = enabled.getAsBoolean();
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        super.extractRenderState(g, x, y, tick); g.centeredText(font, title, width / 2, 8, 0xFFF1E7CE);
        int left = (width - Math.min(420, width - 24)) / 2;
        var lines = font.split(Component.literal(error.isEmpty() ? journal.status() : error), width - left * 2);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), left, height - 52 + i * 10, 0xFFEAEAEA);
    }
}

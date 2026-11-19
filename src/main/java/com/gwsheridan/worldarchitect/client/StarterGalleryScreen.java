package com.gwsheridan.worldarchitect.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class StarterGalleryScreen extends Screen {
    enum Starter {
        SINGLE("Single Block", "Rolling stone terrain. Choose its material and shape in the dimension editor."),
        LAYERED("Layered", "Dirt over andesite and stone. Layers and terrain remain editable."),
        SKYBLOCK("Skyblock", "A classic starting island with 64 personal islands in the Overworld."),
        END("Custom End", "Custom outer End terrain and the vanilla central arena, dragon fight and gateways.");
        final String title, help;
        Starter(String title, String help) { this.title = title; this.help = help; }
    }
    private final Screen parent;
    private final boolean editable;
    private final Consumer<Starter> select;
    StarterGalleryScreen(Screen parent, boolean editable, Consumer<Starter> select) {
        super(Component.literal("Starter gallery")); this.parent = parent; this.editable = editable; this.select = select;
    }
    @Override protected void init() {
        int span = Math.min(400, width - 24), left = (width - span) / 2;
        for (var starter : Starter.values()) {
            var button = addRenderableWidget(Button.builder(Component.literal(starter.title), b -> { minecraft.gui.setScreen(parent); select.accept(starter); })
                    .bounds(left, 48 + starter.ordinal() * 28, span, 20).tooltip(Tooltip.create(Component.literal(starter.help))).build());
            button.active = editable;
            if (!editable) button.setTooltip(Tooltip.create(Component.literal("An installed bundle locks starter selection. Edit only its permitted controls.")));
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(left, height - 26, span, 20).build());
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        super.extractRenderState(g, x, y, tick);
        g.centeredText(font, title, width / 2, 9, 0xFFF1E7CE);
        g.centeredText(font, Component.literal("Choose a starter, then edit its dimensions."), width / 2, 26, 0xFFCCCCCC);
    }
}

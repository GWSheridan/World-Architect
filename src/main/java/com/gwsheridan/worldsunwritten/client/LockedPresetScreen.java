package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.modpack.BundleManifest;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class LockedPresetScreen extends Screen {
    private final Screen parent;
    private final BundleManifest manifest;

    LockedPresetScreen(Screen parent, BundleManifest manifest) {
        super(Component.literal(manifest.title()));
        this.parent = parent;
        this.manifest = manifest;
    }

    @Override
    protected void init() {
        int span=Math.min(368,width-24),left=(width-span)/2,half=(span-8)/2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left, height - 32, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Preset help"), button -> minecraft.gui.setScreen(new StudioReportScreen(this, "Preset Studio help", StudioReportScreen.help())))
                .bounds(left+half+8, height - 32, half, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 20, 0xFFF1E7CE);
        int left = Math.max(16, (width - 330) / 2);
        int y = 43;
        var lines = List.of(Component.literal(manifest.author() + " · " + manifest.release()),
                Component.translatable("worldsunwritten.modpack.dimensions_locked"),
                Component.literal(manifest.description()));
        for (Component line : lines) {
            for (var wrapped : font.split(line, width - 2 * left)) {
                if (y >= height - 45) {
                    return;
                }
                graphics.text(font, wrapped, left, y, 0xFFE5E1D7);
                y += 11;
            }
            y += 9;
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

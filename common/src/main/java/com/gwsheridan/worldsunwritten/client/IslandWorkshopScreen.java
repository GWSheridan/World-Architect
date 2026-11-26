package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.config.DimensionProfile;
import com.gwsheridan.worldsunwritten.config.IslandCatalog;
import com.gwsheridan.worldsunwritten.structure.IslandReview;
import com.gwsheridan.worldsunwritten.worldgen.SkyblockGenerator;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;

final class IslandWorkshopScreen extends Screen {
    private final Screen parent;
    private final HolderLookup.Provider registries;
    private final List<IslandCatalog.Entry> entries;
    private final long seed;
    private final int[] selected = {0, 0};
    private final IslandModelPreview[] previews = new IslandModelPreview[2];
    private String status = "";
    private int panelWidth;

    IslandWorkshopScreen(Screen parent, HolderLookup.Provider registries, IslandCatalog catalog, long seed) {
        super(Component.literal("Island Workshop"));
        this.parent = parent;
        this.registries = registries;
        this.entries = catalog.entries();
        this.seed = seed;
        selected[1] = Math.min(1, entries.size() - 1);
        for (int side = 0; side < 2; side++) previews[side] = new IslandModelPreview(entry(side).build());
    }

    private IslandCatalog.Entry entry(int side) {
        return entries.get(selected[side]);
    }

    @Override
    protected void init() {
        panelWidth = (width - 36) / 2;
        for (int side = 0; side < 2; side++) {
            int index = side;
            int x = 12 + side * (panelWidth + 12);
            button(entry(side).title(), x, 31, panelWidth, () -> choose(index));
            button("Supplies and spawn", x, height - 78, panelWidth, () -> review(index));
            button("Play preview", x, height - 54, panelWidth, () -> preview(index));
        }
        button("Back", 12, height - 27, width - 24, this::onClose);
    }

    private void button(String title, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(title), ignored -> action.run())
                .bounds(x, y, width, 20).build());
    }

    private void choose(int side) {
        var labels = entries.stream().map(entry -> entry.id() + " · " + entry.title()).toList();
        minecraft.gui.setScreen(new StudioChoiceScreen(this, "Island type", labels, label -> {
            selected[side] = labels.indexOf(label);
            previews[side] = new IslandModelPreview(entry(side).build());
            minecraft.gui.setScreen(this);
        }));
    }

    private void review(int side) {
        try {
            minecraft.gui.setScreen(new StudioReportScreen(this, entry(side).title(),
                    IslandReview.inspect(entry(side).build(), registries)));
        } catch (IOException error) {
            status = error.getMessage();
        }
    }

    private void preview(int side) {
        try {
            var entry = entry(side);
            var build = entry.build().checked(registries);
            AuthoringPreview.open(this, "Island preview", new WorldOptions(seed, false, false), context -> {
                var dimensions = new LinkedHashMap<>(context.selectedDimensions().dimensions());
                var old = dimensions.get(LevelStem.OVERWORLD);
                var type = old.type().value();
                var profile = new DimensionProfile(type.minY(), type.height(), Optional.empty());
                var biome = context.worldgenLoadContext().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
                var catalog = new IslandCatalog(List.of(new IslandCatalog.Entry(entry.id(), entry.title(),
                        entry.description(), entry.icon(), 1, build)), entry.id(), "fixed", false, 64, 128, 0);
                dimensions.put(LevelStem.OVERWORLD, new LevelStem(old.type(), new SkyblockGenerator(biome, profile, catalog)));
                return new WorldDimensions(dimensions);
            });
        } catch (IOException | RuntimeException error) {
            status = error.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
        super.extractRenderState(graphics, x, y, tick);
        graphics.centeredText(font, title, width / 2, 12, 0xfff1e7ce);
        for (int side = 0; side < 2; side++)
            previews[side].draw(graphics, 12 + side * (panelWidth + 12), 55, panelWidth, height - 151);
        graphics.centeredText(font, Component.literal(status == null ? "" : status), width / 2, height - 91, 0xffffccaa);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

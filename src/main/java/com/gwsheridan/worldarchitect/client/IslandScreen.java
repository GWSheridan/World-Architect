package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldarchitect.config.DimensionProfile;
import com.gwsheridan.worldarchitect.config.IslandProfile;
import com.gwsheridan.worldarchitect.config.IslandTemplate;
import com.gwsheridan.worldarchitect.dimension.DimensionCatalog.Replacement;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import org.jspecify.annotations.Nullable;

final class IslandScreen extends Screen {
    private final Screen parent;
    private final ResourceKey<LevelStem> dimension;
    private final DimensionProfile profile;
    private final Replacement support;
    private final Consumer<VoidGenerator> save;
    private IslandTemplate template;
    private String file = "";
    private String marker;
    private String originY;
    private String spacing;
    private String slots;
    private boolean classic;
    private @Nullable Component problem;
    private int span;
    private int left;
    private int third;
    private EditBox markerBox;

    IslandScreen(Screen parent, ResourceKey<LevelStem> dimension, DimensionProfile profile, Replacement support,
            Optional<IslandProfile> existing, Consumer<VoidGenerator> save) {
        super(text("title"));
        this.parent = parent;
        this.dimension = dimension;
        this.profile = profile;
        this.support = support;
        this.save = save;
        classic = existing.isEmpty();
        template = existing.map(IslandProfile::template).orElseGet(IslandTemplate::classic);
        marker = coordinates(template.spawn());
        int bottom = Math.clamp(64, profile.minY(), profile.minY() + profile.height() - template.size().getY());
        originY = Integer.toString(existing.map(IslandProfile::originY).orElse(bottom));
        spacing = Integer.toString(existing.map(IslandProfile::spacing).orElse(128));
        slots = Integer.toString(existing.map(IslandProfile::personalSlots).orElse(0));
    }

    @Override
    protected void init() {
        span = Math.min(width - 24, 338);
        left = (width - span) / 2;
        third = (span - 12) / 3;
        int half = (span - 6) / 2;
        boolean skyblock = support.modes().contains(TerrainMode.SKYBLOCK);
        if (skyblock) {
            markerBox = field("marker", marker, 64, left, span, 36, value -> marker = value);
            field("file", file, 100, left, span, 1024, value -> file = value);
            addRenderableWidget(Button.builder(text("import"), button -> importTemplate())
                    .bounds(left, 125, half, 20).build());
            addRenderableWidget(Button.builder(text("classic"), button -> {
                template = IslandTemplate.classic();
                markerBox.setValue(coordinates(template.spawn()));
                classic = true;
                problem = null;
            }).bounds(left + half + 6, 125, half, 20).build());
            field("origin", originY, 162, left, third, 6, value -> originY = value);
            field("spacing", spacing, 162, left + third + 6, third, 4, value -> spacing = value);
            if (dimension.equals(LevelStem.OVERWORLD)) {
                field("slots", slots, 162, left + 2 * (third + 6), third, 4, value -> slots = value);
            }
            addRenderableWidget(Button.builder(text("apply_skyblock"), button -> applySkyblock())
                    .bounds(left + third + 6, height - 28, third, 20).build());
        }
        if (support.modes().contains(TerrainMode.VOID)) {
            addRenderableWidget(Button.builder(text("apply_void"), button -> applyVoid())
                    .bounds(left, height - 28, third, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + 2 * (third + 6), height - 28, third, 20).build());
    }

    private EditBox field(String label, String value, int y, int x, int width, int maxLength, Consumer<String> update) {
        var box = addRenderableWidget(new EditBox(font, x, y, width, 20, text(label)));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(input -> {
            update.accept(input);
            problem = null;
        });
        return box;
    }

    private void importTemplate() {
        try {
            template = IslandTemplate.importStructure(Path.of(file.strip()), marker());
            classic = false;
            problem = null;
        } catch (IOException | RuntimeException e) {
            problem = Component.literal(e.getMessage() == null ? "Unable to read the structure file." : e.getMessage());
        }
    }

    private BlockPos marker() {
        String[] parts = marker.strip().split("\\s+");
        if (parts.length != 3) {
            throw new NumberFormatException();
        }
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private void applySkyblock() {
        try {
            int distance = Integer.parseInt(spacing);
            int allocations = dimension.equals(LevelStem.OVERWORLD) ? Integer.parseInt(slots) : 0;
            if (distance < 64 || distance > 4096 || distance % 16 != 0 || allocations < 0 || allocations > IslandProfile.MAX_SLOTS) {
                problem = text("spacing_or_slots");
                return;
            }
            int y = Integer.parseInt(originY);
            if (y < profile.minY() || y > profile.minY() + profile.height() - template.size().getY()) {
                problem = text("height_range");
                return;
            }
            apply(Optional.of(new IslandProfile(template.withSpawn(marker()), y, distance, allocations)), false);
        } catch (NumberFormatException e) {
            problem = text("number_error");
        } catch (IllegalStateException e) {
            problem = Component.literal(e.getMessage());
        }
    }

    private void applyVoid() {
        if (dimension.equals(LevelStem.OVERWORLD)) {
            minecraft.gui.setScreen(new StudioConfirmScreen(confirmed -> {
                if (confirmed) {
                    apply(Optional.empty(), true);
                } else {
                    minecraft.gui.setScreen(this);
                }
            }, text("unsafe_title"), text("unsafe_description"), text("accept_void"), CommonComponents.GUI_CANCEL));
        } else {
            apply(Optional.empty(), false);
        }
    }

    private void apply(Optional<IslandProfile> island, boolean allowUnsafeSpawn) {
        var generator = new VoidGenerator(support.biome().orElseThrow(), profile, island, allowUnsafeSpawn);
        var ruleProblem = generator.profileProblem();
        if (ruleProblem.isPresent()) { problem = Component.literal(ruleProblem.orElseThrow()); return; }
        save.accept(generator);
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, 12, 0xFFF1E7CE);
        graphics.centeredText(font, Component.literal(dimension.identifier().toString()), width / 2, 27, 0xFFCCCCCC);
        if (support.modes().contains(TerrainMode.SKYBLOCK)) {
            graphics.text(font, Component.translatable("worldarchitect.island.template", text(classic ? "classic" : "custom"),
                    coordinates(template.size())), left, 41, 0xFFCCCCCC);
            graphics.text(font, text("marker"), left, 53, 0xFFFFFFFF);
            graphics.text(font, text("file"), left, 89, 0xFFFFFFFF);
            graphics.text(font, text("origin"), left, 151, 0xFFFFFFFF);
            graphics.text(font, text("spacing"), left + third + 6, 151, 0xFFFFFFFF);
            if (dimension.equals(LevelStem.OVERWORLD)) {
                graphics.text(font, text("slots"), left + 2 * (third + 6), 151, 0xFFFFFFFF);
            }
        }
        if (problem != null) {
            var lines = font.split(problem, span);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                graphics.text(font, lines.get(i), left, 187 + i * 10, 0xFFFFAAAA);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static Component text(String key) {
        return Component.translatable("worldarchitect.island." + key);
    }
}

package com.gwsheridan.worldsunwritten.client;

import static com.gwsheridan.worldsunwritten.client.GenerationRuleScreen.text;

import com.gwsheridan.worldsunwritten.config.ContentProfile;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

final class BiomeRulesScreen extends Screen {
    private final Screen parent;
    private final Registry<Biome> registry;
    private final ContentProfile original;
    private final Consumer<ContentProfile> save;
    private String ids;
    private String scale;
    private String seed;
    private Component status = CommonComponents.EMPTY;
    private Button done;
    private int left;
    private int top;
    private int span;

    BiomeRulesScreen(Screen parent, RegistryAccess registries, ContentProfile original, long worldSeed, Consumer<ContentProfile> save) {
        super(text("biomes"));
        this.parent = parent;
        this.registry = registries.lookupOrThrow(Registries.BIOME);
        this.original = original;
        this.save = save;
        this.ids = original.biomes().stream().map(biome -> biome.unwrapKey().orElseThrow().identifier().toString()).collect(Collectors.joining(", "));
        this.scale = Integer.toString(original.biomeScale());
        this.seed = Long.toString(original.biomes().isEmpty() ? worldSeed : original.biomeSeed());
    }

    @Override
    protected void init() {
        span = Math.min(380, width - 24);
        left = (width - span) / 2;
        top = Math.max(6, (height - 155) / 2);
        int half = (span - 8) / 2;
        entry("biome_ids", ids, left, 39, span, 1500, value -> ids = value).setHint(text("biome_default"));
        entry("biome_scale", scale, left, 79, half, 5, value -> scale = value);
        entry("biome_seed", seed, left + half + 8, 79, half, 21, value -> seed = value);
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> parse().result().ifPresent(profile -> {
            save.accept(profile);
            onClose();
        })).bounds(left, top + 132, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(left + half + 8, top + 132, half, 20).build());
        validate();
    }

    private EditBox entry(String key, String value, int x, int y, int width, int max, Consumer<String> updated) {
        EditBox field = addRenderableWidget(new EditBox(font, x, top + y, width, 20, text(key)));
        field.setMaxLength(max);
        field.setValue(value);
        field.setResponder(input -> { updated.accept(input); validate(); });
        return field;
    }

    private DataResult<ContentProfile> parse() {
        if (ids.isBlank()) {
            return DataResult.success(original.withBiomes(List.of(), 128, 0L));
        }
        List<Holder<Biome>> selected = new ArrayList<>();
        for (String value : ids.split(",", -1)) {
            Identifier id = Identifier.tryParse(value.strip());
            var biome = id == null ? null : registry.get(id).orElse(null);
            if (biome == null || selected.contains(biome)) {
                return DataResult.error(() -> text("biome_invalid").getString() + " " + value.strip());
            }
            selected.add(biome);
        }
        if (selected.size() > ContentProfile.MAX_RULES) {
            return DataResult.error(() -> text("biome_limit").getString());
        }
        try {
            int size = Integer.parseInt(scale.strip());
            long layoutSeed = Long.parseLong(seed.strip());
            if (size >= 16 && size <= 1024) {
                return DataResult.success(original.withBiomes(selected, size, layoutSeed));
            }
        } catch (NumberFormatException ignored) {
        }
        return DataResult.error(() -> text("biome_range").getString());
    }

    private void validate() {
        var result = parse();
        done.active = result.isSuccess();
        status = result.error().<Component>map(error -> Component.literal(error.message())).orElseGet(() -> text("biomes_help"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, top + 5, 0xFFF1E7CE);
        graphics.text(font, text("biome_ids"), left, top + 27, 0xFFEAEAEA);
        graphics.text(font, text("biome_scale"), left, top + 67, 0xFFEAEAEA);
        graphics.text(font, text("biome_seed"), left + (span + 8) / 2, top + 67, 0xFFEAEAEA);
        var lines = font.split(status, span);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), left, top + 106 + i * 9, done.active ? 0xFFCCCCCC : 0xFFFFAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

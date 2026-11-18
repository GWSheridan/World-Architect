package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldarchitect.config.ContentProfile;
import com.gwsheridan.worldarchitect.config.DimensionProfile;
import com.gwsheridan.worldarchitect.config.IslandProfile;
import com.gwsheridan.worldarchitect.config.LayeredProfile;
import com.gwsheridan.worldarchitect.config.SingleBlockProfile;
import com.gwsheridan.worldarchitect.config.TerrainShape;
import com.gwsheridan.worldarchitect.dimension.DimensionCatalog;
import com.gwsheridan.worldarchitect.dimension.DimensionCatalog.Replacement;
import com.gwsheridan.worldarchitect.modpack.BundleManifest;
import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.gwsheridan.worldarchitect.worldgen.LayeredGenerator;
import com.gwsheridan.worldarchitect.worldgen.ManagedGenerator;
import com.gwsheridan.worldarchitect.worldgen.SingleBlockGenerator;
import com.gwsheridan.worldarchitect.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jspecify.annotations.Nullable;

final class DimensionManagerScreen extends Screen {
    private final CreateWorldScreen parent;
    private final WorldCreationContext context;
    private final Map<ResourceKey<LevelStem>, LevelStem> draft;
    private final Map<ResourceKey<LevelStem>, LevelStem> preset;
    private final Map<ResourceKey<LevelStem>, LevelStem> vanilla;
    private final List<ResourceKey<LevelStem>> keys;
    private final Optional<BundleManifest> lock;
    private @Nullable ResourceKey<LevelStem> selected;
    private @Nullable Replacement replacement;
    private String query = "";
    private DimensionList rows;
    private Button originalButton;
    private Button singleButton;
    private Button layeredButton;
    private Button featuresButton;
    private Button voidButton;
    private int detailLeft;
    private int detailWidth;

    DimensionManagerScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(text("title"));
        this.parent = parent;
        this.context = context;
        this.draft = new LinkedHashMap<>(context.selectedDimensions().dimensions());
        var holder = parent.getUiState().getWorldType().preset();
        lock = ModpackRuntime.locked().filter(bundle -> holder != null && holder.is(bundle.key())).map(bundle -> bundle.manifest());
        this.preset = holder == null ? Map.of() : holder.value().createWorldDimensions().dimensions();
        this.vanilla = context.worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).get(WorldPresets.NORMAL)
                .map(normal -> normal.value().createWorldDimensions().dimensions()).orElseGet(Map::of);
        this.keys = DimensionCatalog.discover(draft, context.datapackDimensions());
        this.selected = keys.getFirst();
    }

    @Override
    protected void init() {
        int span = Math.min(448, width - 24);
        int left = (width - span) / 2;
        int listWidth = span / 2 - 8;
        detailLeft = left + listWidth + 16;
        detailWidth = span - listWidth - 16;
        EditBox search = addRenderableWidget(new EditBox(font, left, 36, listWidth, 20, text("search")));
        search.setHint(text("search"));
        search.setMaxLength(256);
        search.setValue(query);
        rows = addRenderableWidget(new DimensionList(left, listWidth, height - 99));
        originalButton = addRenderableWidget(Button.builder(text("original"), button -> restoreOriginal())
                .bounds(detailLeft, 121, detailWidth, 20).build());
        int detailHalf = (detailWidth - 6) / 2;
        singleButton = addRenderableWidget(Button.builder(text("single"), button -> edit(TerrainMode.SINGLE_BLOCK))
                .bounds(detailLeft, 147, detailHalf, 20).build());
        layeredButton = addRenderableWidget(Button.builder(text("layered"), button -> edit(TerrainMode.LAYERED))
                .bounds(detailLeft + detailHalf + 6, 147, detailHalf, 20).build());
        featuresButton = addRenderableWidget(Button.builder(text("features"), button -> editFeatures())
                .bounds(detailLeft, 173, detailHalf, 20).build());
        voidButton = addRenderableWidget(Button.builder(text("void_sky"), button -> editVoid())
                .bounds(detailLeft + detailHalf + 6, 173, detailHalf, 20).build());
        int half = (span - 8) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            parent.getUiState().updateDimensions((registries, old) -> new WorldDimensions(Map.copyOf(draft)));
            onClose();
        }).bounds(left, height - 30, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + half + 8, height - 30, half, 20).build());
        search.setResponder(value -> {
            query = value;
            refreshRows();
        });
        refreshRows();
    }

    private void refreshRows() {
        ResourceKey<LevelStem> previous = selected;
        String filter = query.strip().toLowerCase(Locale.ROOT);
        List<DimensionRow> matches = keys.stream().filter(key -> key.identifier().toString().contains(filter)).map(DimensionRow::new).toList();
        rows.replaceEntries(matches);
        rows.setSelected(matches.stream().filter(row -> row.key.equals(previous)).findFirst().orElse(matches.isEmpty() ? null : matches.getFirst()));
    }

    private LevelStem effective(ResourceKey<LevelStem> key) {
        return DimensionCatalog.effective(key, draft, context.datapackDimensions());
    }

    private Optional<LevelStem> original(ResourceKey<LevelStem> key) {
        return context.datapackDimensions().getOptional(key)
                .or(() -> Optional.ofNullable(preset.get(key)).filter(stem -> !(stem.generator() instanceof ManagedGenerator)))
                .or(() -> Optional.ofNullable(vanilla.get(key)))
                .or(() -> Optional.ofNullable(draft.get(key)).filter(stem -> !(stem.generator() instanceof ManagedGenerator)));
    }

    private void updateActions() {
        ResourceKey<LevelStem> key = selected;
        LevelStem stem = key == null ? null : effective(key);
        var original = key == null ? Optional.<LevelStem>empty() : original(key);
        replacement = stem == null ? null : DimensionCatalog.inspect(context.worldgenLoadContext(), key, original.orElse(stem));
        LevelStem configured = key == null ? null : draft.get(key);
        boolean canEdit = key != null && canEditDimension(key);
        boolean canEditFeatures = key != null && (canEdit || lock.map(policy -> policy.editableFeatures().containsKey(key.identifier())).orElse(false));
        originalButton.active = canEdit && configured != null && configured.generator() instanceof ManagedGenerator && original.isPresent();
        singleButton.active = canEdit && replacement != null && replacement.modes().contains(TerrainMode.SINGLE_BLOCK);
        layeredButton.active = canEdit && replacement != null && replacement.modes().contains(TerrainMode.LAYERED);
        voidButton.active = canEdit && replacement != null && (replacement.modes().contains(TerrainMode.VOID) || replacement.modes().contains(TerrainMode.SKYBLOCK));
        featuresButton.active = canEditFeatures && stem != null && stem.generator() instanceof SolidColumnGenerator;
        featuresButton.setTooltip(featuresButton.active ? null : Tooltip.create(canEditFeatures ? GenerationRuleScreen.text("requires_terrain")
                : Component.translatable("worldarchitect.modpack.option_locked")));
        Tooltip warning = stem != null && stem.type().value().hasEnderDragonFight() ? Tooltip.create(text("dragon_warning")) : null;
        singleButton.setTooltip(warning);
        layeredButton.setTooltip(warning);
    }

    private boolean canEditDimension(ResourceKey<LevelStem> key) {
        return lock.map(policy -> policy.canEditDimension(key.identifier())).orElse(true);
    }

    private void editFeatures() {
        ResourceKey<LevelStem> key = selected;
        if (key == null) {
            return;
        }
        LevelStem stem = effective(key);
        if (stem.generator() instanceof SolidColumnGenerator generator) {
            var type = stem.type().value();
            var profile = generator.dimensionProfile().orElseGet(() -> new DimensionProfile(type.minY(), type.height(), Optional.empty()));
            minecraft.gui.setScreen(new DimensionFeaturesScreen(this, Component.literal(key.identifier().toString()), generator, profile,
                    context.worldgenLoadContext(), context.options().seed(),
                    feature -> lock.map(policy -> policy.canEditFeature(key.identifier(), feature)).orElse(true),
                    edited -> draft.put(key, new LevelStem(stem.type(), edited))));
        }
    }

    private void restoreOriginal() {
        ResourceKey<LevelStem> key = selected;
        if (key == null) {
            return;
        }
        original(key).ifPresent(stem -> {
            if (!key.equals(LevelStem.OVERWORLD) && context.datapackDimensions().containsKey(key)) {
                draft.remove(key);
            } else {
                draft.put(key, stem);
            }
            updateActions();
        });
    }

    private void editVoid() {
        ResourceKey<LevelStem> key = selected;
        var support = replacement;
        if (key == null || support == null) {
            return;
        }
        LevelStem stem = effective(key);
        var type = stem.type().value();
        var profile = new DimensionProfile(type.minY(), type.height(), support.adapter());
        var current = stem.generator() instanceof VoidGenerator generator ? generator.island() : Optional.<IslandProfile>empty();
        minecraft.gui.setScreen(new IslandScreen(this, key, profile, support, current,
                edited -> draft.put(key, new LevelStem(stem.type(), edited))));
    }

    private void edit(TerrainMode mode) {
        ResourceKey<LevelStem> key = selected;
        DimensionCatalog.Replacement support = replacement;
        if (key == null || support == null || !support.modes().contains(mode)) {
            return;
        }
        LevelStem stem = effective(key);
        var type = stem.type().value();
        ContentProfile retained = stem.generator() instanceof SolidColumnGenerator generator ? generator.content() : ContentProfile.EMPTY;
        var profile = Optional.of(new DimensionProfile(type.minY(), type.height(), support.adapter(), retained));
        var biome = support.biome().orElseThrow();
        BlockState material = key.equals(LevelStem.NETHER) ? Blocks.NETHERRACK.defaultBlockState()
                : key.equals(LevelStem.END) ? Blocks.END_STONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        int elevation = Math.clamp(TerrainShape.HILLS.elevation(), type.minY(), type.minY() + type.height() - 4);
        if (mode == TerrainMode.SINGLE_BLOCK) {
            var settings = stem.generator() instanceof SingleBlockGenerator current ? current.profile()
                    : new SingleBlockProfile(SingleBlockProfile.VERSION, material, elevation, TerrainShape.HILLS.relief());
            var generator = new SingleBlockGenerator(biome, settings, profile);
            minecraft.gui.setScreen(new SingleBlockScreen(this, Component.literal(key.identifier().toString()), generator,
                    edited -> draft.put(key, new LevelStem(stem.type(), edited))));
        } else {
            var settings = stem.generator() instanceof LayeredGenerator current ? current.profile()
                    : new LayeredProfile(1, material, List.of(), elevation, TerrainShape.HILLS.relief());
            var generator = new LayeredGenerator(biome, settings, profile);
            minecraft.gui.setScreen(new LayeredScreen(this, generator, () -> context.options().seed(),
                    edited -> draft.put(key, new LevelStem(stem.type(), edited))));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, 15, 0xFFF1E7CE);
        ResourceKey<LevelStem> key = selected;
        if (key == null) {
            graphics.text(font, text("no_matches"), detailLeft, 63, 0xFFFFFFFF);
            return;
        }
        LevelStem stem = effective(key);
        String mode = stem.generator() instanceof ManagedGenerator generator ? switch (generator.terrainMode()) {
            case SINGLE_BLOCK -> "single";
            case LAYERED -> "layered";
            case VOID -> "void";
            case SKYBLOCK -> "skyblock";
        } : "original";
        var type = stem.type().value();
        graphics.text(font, text(mode), detailLeft, 62, 0xFFF1E7CE);
        graphics.text(font, Component.translatable("worldarchitect.dimensions.height", type.minY(), type.minY() + type.height() - 1), detailLeft, 76, 0xFFCCCCCC);
        var detail = type.hasEnderDragonFight() && replacement != null && !replacement.modes().isEmpty()
                ? text("dragon_warning") : replacement == null ? CommonComponents.EMPTY : replacement.detail();
        if (!canEditDimension(key)) {
            detail = Component.translatable("worldarchitect.modpack.option_locked");
        }
        var lines = font.split(detail, detailWidth);
        for (int index = 0; index < Math.min(3, lines.size()); index++) {
            graphics.text(font, lines.get(index), detailLeft, 89 + index * 9, 0xFFE8E8E8);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static Component text(String name) {
        return Component.translatable("worldarchitect.dimensions." + name);
    }

    private final class DimensionList extends ObjectSelectionList<DimensionRow> {
        DimensionList(int left, int width, int height) {
            super(DimensionManagerScreen.this.minecraft, width, height, 62, 22);
            setX(left);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 16;
        }

        @Override
        public void setSelected(@Nullable DimensionRow row) {
            super.setSelected(row);
            selected = row == null ? null : row.key;
            updateActions();
        }
    }

    private final class DimensionRow extends ObjectSelectionList.Entry<DimensionRow> {
        private final ResourceKey<LevelStem> key;

        DimensionRow(ResourceKey<LevelStem> key) {
            this.key = key;
        }

        @Override
        public Component getNarration() {
            return Component.literal(key.identifier().toString());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tick) {
            graphics.text(font, font.plainSubstrByWidth(key.identifier().toString(), getWidth() - 8), getX() + 4, getY() + 6, 0xFFFFFFFF);
        }
    }
}

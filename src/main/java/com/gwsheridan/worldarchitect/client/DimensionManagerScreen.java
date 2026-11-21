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
import com.gwsheridan.worldarchitect.worldgen.GenerationPreview;
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

final class DimensionManagerScreen extends Screen implements AuthoringContext {
    @Override public String authoringKey() { return "dimension:" + selected + ":" + draftOptions.seed(); }
    private final CreateWorldScreen parent;
    private final WorldCreationContext context;
    private final Map<ResourceKey<LevelStem>, LevelStem> draft;
    private final Map<ResourceKey<LevelStem>, LevelStem> preset;
    private final Map<ResourceKey<LevelStem>, LevelStem> vanilla;
    private List<ResourceKey<LevelStem>> keys;
    private net.minecraft.world.level.levelgen.WorldOptions draftOptions;
    private boolean studioOptionsChanged;
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
    private Button previewButton;
    private Button rulesButton;
    private Button endButton;
    private int detailLeft;
    private int detailWidth;
    private int actionTop;
    private final Map<ResourceKey<LevelStem>, LevelStem> initial;
    private boolean applied;

    DimensionManagerScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(text("title"));
        this.parent = parent;
        this.context = context;
        this.draftOptions = context.options();
        this.draft = new LinkedHashMap<>(context.selectedDimensions().dimensions());
        var holder = parent.getUiState().getWorldType().preset();
        lock = ModpackRuntime.locked().filter(bundle -> holder != null && holder.is(bundle.key())).map(bundle -> bundle.manifest());
        this.preset = holder == null ? Map.of() : holder.value().createWorldDimensions().dimensions();
        this.vanilla = context.worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).get(WorldPresets.NORMAL)
                .map(normal -> normal.value().createWorldDimensions().dimensions()).orElseGet(Map::of);
        this.keys = DimensionCatalog.discover(draft, context.datapackDimensions());
        this.selected = keys.getFirst();
        this.initial = Map.copyOf(draft);
    }

    @Override
    protected void init() {
        int span = Math.min(448, width - 24);
        int left = (width - span) / 2;
        int listWidth = span / 2 - 8;
        detailLeft = left + listWidth + 16;
        detailWidth = span - listWidth - 16;
        actionTop = Math.max(66, Math.min(109, height - 154));
        EditBox search = addRenderableWidget(new EditBox(font, left, 25, listWidth, 20, text("search")));
        search.setHint(text("search"));
        search.setMaxLength(256);
        search.setValue(query);
        rows = addRenderableWidget(new DimensionList(left, listWidth, height - 86));
        previewButton = addRenderableWidget(Button.builder(Component.translatable("worldarchitect.preview.open"), button -> openPreview())
                .bounds(detailLeft, 25, detailWidth, 20).build());
        previewButton.setWidth((detailWidth-6)/2);
        var builder=addRenderableWidget(Button.builder(Component.literal("Builder"),button->{
            try{
                var all=new LinkedHashMap<ResourceKey<LevelStem>,LevelStem>();keys.forEach(key->all.put(key,effective(key)));
                var source=com.gwsheridan.worldarchitect.modpack.PresetStudio.capture(com.gwsheridan.worldarchitect.modpack.PresetStudio.defaults("my-modpack","1.0.0",draftOptions),new WorldDimensions(all),context.worldgenLoadContext());
                minecraft.gui.setScreen(new ModpackBuilderScreen(this,context.worldgenLoadContext(),"my-modpack",source,(name,edited)->{
                    try{var replacement=com.gwsheridan.worldarchitect.modpack.PresetStudio.forApply(edited,context.worldgenLoadContext()).createWorldDimensions().dimensions();draft.clear();draft.putAll(replacement);draftOptions=edited.manifest().options();studioOptionsChanged=true;keys=DimensionCatalog.discover(draft,context.datapackDimensions());refreshRows();}catch(java.io.IOException error){throw new IllegalArgumentException(error.getMessage(),error);}
                }));
            }catch(java.io.IOException error){minecraft.gui.setScreen(new StudioReportScreen(this,"Cannot open Modpack Builder",List.of(error.getMessage())));}
        }).bounds(detailLeft+(detailWidth+6)/2,25,(detailWidth-6)/2,20).build());
        builder.active=lock.isEmpty();
        originalButton = addRenderableWidget(Button.builder(text("original"), button -> restoreOriginal())
                .bounds(detailLeft, actionTop, detailWidth, 20).build());
        int detailHalf = (detailWidth - 6) / 2;
        singleButton = addRenderableWidget(Button.builder(text("single"), button -> edit(TerrainMode.SINGLE_BLOCK))
                .bounds(detailLeft, actionTop + 22, detailHalf, 20).build());
        layeredButton = addRenderableWidget(Button.builder(text("layered"), button -> edit(TerrainMode.LAYERED))
                .bounds(detailLeft + detailHalf + 6, actionTop + 22, detailHalf, 20).build());
        featuresButton = addRenderableWidget(Button.builder(text("features"), button -> editFeatures())
                .bounds(detailLeft, actionTop + 44, detailHalf, 20).build());
        voidButton = addRenderableWidget(Button.builder(text("void_sky"), button -> editVoid())
                .bounds(detailLeft + detailHalf + 6, actionTop + 44, detailHalf, 20).build());
        rulesButton = addRenderableWidget(Button.builder(Component.literal("World Generation Rules"), button -> editRules())
                .bounds(detailLeft, actionTop + 66, detailWidth, 20).build());
        endButton = addRenderableWidget(Button.builder(Component.literal("Dragon fight"), button -> toggleEndProgression())
                .bounds(detailLeft + detailHalf + 6, actionTop + 66, detailHalf, 20).build());
        var studio = addRenderableWidget(Button.builder(Component.literal("Preset Studio"), button -> {
            try {
                var all = new LinkedHashMap<ResourceKey<LevelStem>, LevelStem>(); keys.forEach(key -> all.put(key, effective(key)));
                minecraft.gui.setScreen(new PresetStudioScreen(this, context.worldgenLoadContext(), new WorldDimensions(all), draftOptions, edited -> {
                    try {
                        var replacement = com.gwsheridan.worldarchitect.modpack.PresetStudio.forApply(edited, context.worldgenLoadContext()).createWorldDimensions().dimensions();
                        draft.clear(); draft.putAll(replacement);
                        draftOptions = edited.manifest().options();
                        studioOptionsChanged = true;
                        keys = DimensionCatalog.discover(draft, context.datapackDimensions());
                        refreshRows();
                    } catch (java.io.IOException e) { throw new IllegalArgumentException(e.getMessage(), e); }
                }));
            } catch (java.io.IOException e) {
                minecraft.gui.setScreen(new StudioReportScreen(this, "Cannot open Preset Studio", List.of(e.getMessage())));
            }
        }).bounds(detailLeft, actionTop + 88, detailWidth, 20).build());
        studio.active = lock.isEmpty();
        studio.setWidth((detailWidth - 6) / 2);
        addRenderableWidget(Button.builder(Component.literal("Starters"), b -> minecraft.gui.setScreen(
                new StarterGalleryScreen(this, lock.isEmpty(), this::applyStarter)))
                .bounds(detailLeft + (detailWidth + 6) / 2, actionTop + 88, (detailWidth - 6) / 2, 20).build());
        int half = (span - 8) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            if (studioOptionsChanged) {
                parent.getUiState().setSeed(Long.toString(draftOptions.seed()));
                parent.getUiState().setGenerateStructures(draftOptions.generateStructures());
                parent.getUiState().setBonusChest(draftOptions.generateBonusChest());
            }
            parent.getUiState().updateDimensions((registries, old) -> new WorldDimensions(Map.copyOf(draft)));
            applied = true;
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
        previewButton.active = stem != null;
        previewButton.setTooltip(previewButton.active || stem == null ? null
                : Tooltip.create(Component.translatable("worldarchitect.preview.unavailable")));
        originalButton.active = canEdit && configured != null && configured.generator() instanceof ManagedGenerator && original.isPresent();
        singleButton.active = canEdit && replacement != null && replacement.modes().contains(TerrainMode.SINGLE_BLOCK);
        layeredButton.active = canEdit && replacement != null && replacement.modes().contains(TerrainMode.LAYERED);
        voidButton.active = canEdit && replacement != null && (replacement.modes().contains(TerrainMode.VOID) || replacement.modes().contains(TerrainMode.SKYBLOCK));
        boolean cellular=stem!=null&&stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.CellGenerator;
        featuresButton.active = cellular?canEdit:canEditFeatures && stem != null && stem.generator() instanceof SolidColumnGenerator;
        featuresButton.setMessage(cellular?Component.literal("Cell World Studio"):text("features"));
        rulesButton.active = canEdit && stem != null && stem.generator() instanceof ManagedGenerator&&!cellular;
        featuresButton.setTooltip(featuresButton.active ? null : Tooltip.create(canEditFeatures ? GenerationRuleScreen.text("requires_terrain")
                : Component.translatable("worldarchitect.modpack.option_locked")));
        endButton.visible = key != null && key.equals(LevelStem.END) && stem.generator() instanceof ManagedGenerator;
        endButton.active = endButton.visible && canEdit && stem.type().value().hasEnderDragonFight() && stem.type().value().minY() == 0 && stem.type().value().height() == 256
                && ((ManagedGenerator) stem.generator()).dimensionProfile().flatMap(DimensionProfile::adapter).isEmpty();
        endButton.setMessage(Component.literal("Dragon fight: " + (stem != null && stem.generator() instanceof ManagedGenerator managed && managed.endProgression().isPresent() ? "On" : "Off")));
        endButton.setTooltip(Tooltip.create(Component.literal("Reserves the central 512 by 512 blocks for vanilla End progression. Gateway travel may create landing islands. Starting templates in the reservation are replaced by the native arena.")));
        rulesButton.setMessage(endButton.visible ? text("rules_short") : Component.literal("World Generation Rules"));
        rulesButton.setWidth(endButton.visible ? (detailWidth - 6) / 2 : detailWidth);
        Tooltip warning = stem != null && stem.type().value().hasEnderDragonFight() ? Tooltip.create(text("dragon_warning")) : null;
        singleButton.setTooltip(warning);
        layeredButton.setTooltip(warning);
    }

    private void toggleEndProgression() {
        if (selected == null || !canEditDimension(selected)) return;
        var stem = effective(selected);
        if (!(stem.generator() instanceof ManagedGenerator generator)) return;
        var profile = generator.dimensionProfile().orElseThrow();
        var changed = profile.withEndProgression(generator.endProgression().isPresent() ? Optional.empty()
                : Optional.of(com.gwsheridan.worldarchitect.config.EndProgression.vanillaArena(context.worldgenLoadContext())));
        var edited = stem.generator() instanceof SolidColumnGenerator solid ? solid.withDimensionProfile(changed)
                : stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.CellGenerator cells ? cells.withDimensionProfile(changed) : ((VoidGenerator)stem.generator()).withDimensionProfile(changed);
        draft.put(selected, new LevelStem(stem.type(), edited));
        updateActions();
    }

    private void openPreview() {
        ResourceKey<LevelStem> key = selected;
        if (key != null) {
            long seed = draftOptions.seed();
            var stem = effective(key);
            var preview = GenerationPreview.forStem(stem, seed);
            if (preview.isPresent()) minecraft.gui.setScreen(new WorldPreviewScreen(this, key.identifier(), seed, preview.orElseThrow(), stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.ManagedGenerator managed ? managed.dimensionProfile().map(DimensionProfile::rules).orElse(com.gwsheridan.worldarchitect.rules.RuleSet.EMPTY) : com.gwsheridan.worldarchitect.rules.RuleSet.EMPTY));
            else minecraft.gui.setScreen(new StudioReportScreen(this, "Preview unavailable", List.of(
                    "This original or modded generator does not expose a base-terrain sampler.",
                    "World Architect does not create chunks to simulate unsupported generators.")));
        }
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
        if(stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.CellGenerator cells&&canEditDimension(key)){
            minecraft.gui.setScreen(new CellStudioScreen(this,context.worldgenLoadContext(),stem,draftOptions.seed(),edited->draft.put(key,new LevelStem(stem.type(),edited))));return;
        }
        if (stem.generator() instanceof SolidColumnGenerator generator) {
            var type = stem.type().value();
            var profile = generator.dimensionProfile().orElseGet(() -> new DimensionProfile(type.minY(), type.height(), Optional.empty()));
            minecraft.gui.setScreen(new DimensionFeaturesScreen(this, Component.literal(key.identifier().toString()), generator, profile,
                    context.worldgenLoadContext(), draftOptions.seed(),
                    feature -> lock.map(policy -> policy.canEditFeature(key.identifier(), feature)).orElse(true),
                    edited -> draft.put(key, new LevelStem(stem.type(), edited))));
        }
    }

    private void editRules() {
        if (selected == null) return;
        var key = selected; var stem = effective(key);
        if (!(stem.generator() instanceof ManagedGenerator generator)) return;
        var profile = generator.dimensionProfile().orElseGet(() -> new DimensionProfile(stem.type().value().minY(), stem.type().value().height(), Optional.empty()));
        var all = new LinkedHashMap<ResourceKey<LevelStem>, LevelStem>(); keys.forEach(dimension -> all.put(dimension, effective(dimension)));
        minecraft.gui.setScreen(new RuleEngineScreen(this, key.identifier(), stem.generator(), profile, context.worldgenLoadContext(), all, draftOptions.seed(), edited -> {
            var changed = stem.generator() instanceof SolidColumnGenerator solid ? solid.withDimensionProfile(edited)
                    : stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.CellGenerator cells ? cells.withDimensionProfile(edited) : ((VoidGenerator)stem.generator()).withDimensionProfile(edited);
            draft.put(key, new LevelStem(stem.type(), changed)); updateActions();
        }));
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
        if(stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.SkyblockGenerator rich){
            var entries=rich.catalog().entries();
            minecraft.gui.setScreen(new StudioChoiceScreen(this,"Island templates",entries.stream().map(e->e.id()+" · "+e.title()).toList(),choice->{
                var entry=entries.stream().filter(e->choice.startsWith(e.id()+" · ")).findFirst().orElseThrow();
                minecraft.gui.setScreen(new IslandStudioScreen(this,context.worldgenLoadContext(),entry.build(),build->{
                    var catalog=rich.catalog();var edited=new java.util.ArrayList<>(catalog.entries());edited.replaceAll(e->e.id().equals(entry.id())?new com.gwsheridan.worldarchitect.config.IslandCatalog.Entry(e.id(),e.title(),e.description(),e.icon(),e.weight(),build):e);
                    draft.put(key,new LevelStem(stem.type(),new com.gwsheridan.worldarchitect.worldgen.SkyblockGenerator(rich.biome(),rich.dimensionProfile().orElseThrow(),new com.gwsheridan.worldarchitect.config.IslandCatalog(edited,catalog.lobby(),catalog.mode(),catalog.teams(),catalog.originY(),catalog.spacing(),catalog.personalSlots()))));
                }));
            }));return;
        }
        var type = stem.type().value();
        var retainedRules = stem.generator() instanceof ManagedGenerator managed ? managed.dimensionProfile().map(DimensionProfile::rules).orElse(com.gwsheridan.worldarchitect.rules.RuleSet.EMPTY) : com.gwsheridan.worldarchitect.rules.RuleSet.EMPTY;
        var profile = new DimensionProfile(type.minY(), type.height(), support.adapter()).withRules(retainedRules)
                .withEndProgression(stem.generator() instanceof ManagedGenerator managed ? managed.endProgression() : Optional.empty());
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
        var retainedRules = stem.generator() instanceof ManagedGenerator managed ? managed.dimensionProfile().map(DimensionProfile::rules).orElse(com.gwsheridan.worldarchitect.rules.RuleSet.EMPTY) : com.gwsheridan.worldarchitect.rules.RuleSet.EMPTY;
        var profile = Optional.of(new DimensionProfile(type.minY(), type.height(), support.adapter(), retained, retainedRules)
                .withEndProgression(stem.generator() instanceof ManagedGenerator managed ? managed.endProgression()
                        : key.equals(LevelStem.END) && type.hasEnderDragonFight() && type.minY() == 0 && type.height() == 256 && support.adapter().isEmpty()
                                ? Optional.of(com.gwsheridan.worldarchitect.config.EndProgression.vanillaArena(context.worldgenLoadContext())) : Optional.empty()));
        var biome = support.biome().orElseThrow();
        BlockState material = key.equals(LevelStem.NETHER) ? Blocks.NETHERRACK.defaultBlockState()
                : key.equals(LevelStem.END) ? Blocks.END_STONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        int elevation = Math.clamp(TerrainShape.HILLS.elevation(), type.minY(), type.minY() + type.height() - 4);
        if (mode == TerrainMode.SINGLE_BLOCK) {
            var settings = stem.generator() instanceof SingleBlockGenerator current ? current.profile()
                    : new SingleBlockProfile(material, new TerrainShape(elevation, TerrainShape.HILLS.relief()));
            var generator = new SingleBlockGenerator(biome, settings, profile);
            minecraft.gui.setScreen(new SingleBlockScreen(this, Component.literal(key.identifier().toString()), generator, context.worldgenLoadContext(),
                    edited -> draft.put(key, new LevelStem(stem.type(), edited))));
        } else {
            var settings = stem.generator() instanceof LayeredGenerator current ? current.profile()
                    : new LayeredProfile(material, List.of(), new TerrainShape(elevation, TerrainShape.HILLS.relief()), List.of());
            var generator = new LayeredGenerator(biome, settings, profile);
            minecraft.gui.setScreen(new LayeredScreen(this, generator, context.worldgenLoadContext(), () -> draftOptions.seed(),
                    edited -> draft.put(key, new LevelStem(stem.type(), edited))));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, 9, 0xFFF1E7CE);
        ResourceKey<LevelStem> key = selected;
        if (key == null) {
            graphics.text(font, text("no_matches"), detailLeft, 52, 0xFFFFFFFF);
            return;
        }
        LevelStem stem = effective(key);
        String mode = stem.generator() instanceof ManagedGenerator generator ? switch (generator.terrainMode()) {
            case SINGLE_BLOCK -> "single";
            case LAYERED -> "layered";
            case VOID -> "void";
            case SKYBLOCK -> "skyblock";
            case CELLULAR -> "cellular";
        } : "original";
        var type = stem.type().value();
        graphics.text(font, text(mode), detailLeft, 51, 0xFFF1E7CE);
        graphics.text(font, Component.translatable("worldarchitect.dimensions.height", type.minY(), type.minY() + type.height() - 1), detailLeft, 64, 0xFFCCCCCC);
        var detail = type.hasEnderDragonFight() && replacement != null && !replacement.modes().isEmpty()
                ? text("dragon_warning") : replacement == null ? CommonComponents.EMPTY : replacement.detail();
        if (!canEditDimension(key)) {
            detail = Component.translatable("worldarchitect.modpack.option_locked");
        }
        var lines = font.split(detail, detailWidth);
        int visible = Math.min(3, (actionTop - 78) / 9);
        for (int index = 0; index < Math.min(visible, lines.size()); index++) {
            graphics.text(font, lines.get(index), detailLeft, 78 + index * 9, 0xFFE8E8E8);
        }
    }

    private void applyStarter(StarterGalleryScreen.Starter starter) {
        if (lock.isPresent()) return;
        var key = starter == StarterGalleryScreen.Starter.END ? LevelStem.END : LevelStem.OVERWORLD;
        var stem = effective(key);
        var support = DimensionCatalog.inspect(context.worldgenLoadContext(), key, original(key).orElse(stem));
        var mode = switch (starter) {
            case SINGLE, END -> TerrainMode.SINGLE_BLOCK;
            case LAYERED -> TerrainMode.LAYERED;
            case SKYBLOCK -> TerrainMode.SKYBLOCK;
        };
        if (!support.modes().contains(mode) || support.biome().isEmpty()
                || starter == StarterGalleryScreen.Starter.END && (support.adapter().isPresent() || !stem.type().value().hasEnderDragonFight()
                    || stem.type().value().minY() != 0 || stem.type().value().height() != 256)) {
            minecraft.gui.setScreen(new StudioReportScreen(this, "Starter unavailable", List.of("This dimension does not support that starter. " + support.detail().getString())));
            return;
        }
        var type = stem.type().value();
        var profile = new DimensionProfile(type.minY(), type.height(), support.adapter());
        if (starter == StarterGalleryScreen.Starter.END) profile = profile.withEndProgression(Optional.of(
                com.gwsheridan.worldarchitect.config.EndProgression.vanillaArena(context.worldgenLoadContext())));
        var shape = new TerrainShape(Math.clamp(68, type.minY(), type.minY() + type.height() - 4), 44);
        net.minecraft.world.level.chunk.ChunkGenerator generator = switch (starter) {
            case SINGLE, END -> new SingleBlockGenerator(support.biome().orElseThrow(), new SingleBlockProfile(
                    (starter == StarterGalleryScreen.Starter.END ? Blocks.END_STONE : Blocks.STONE).defaultBlockState(), shape), Optional.of(profile));
            case LAYERED -> new LayeredGenerator(support.biome().orElseThrow(), new LayeredProfile(Blocks.STONE.defaultBlockState(), List.of(
                    new LayeredProfile.Layer(Blocks.DIRT.defaultBlockState(), 1), new LayeredProfile.Layer(Blocks.ANDESITE.defaultBlockState(), 3)), shape, List.of()), Optional.of(profile));
            case SKYBLOCK -> new VoidGenerator(support.biome().orElseThrow(), profile, Optional.of(new IslandProfile(
                    com.gwsheridan.worldarchitect.config.IslandTemplate.classic(), Math.clamp(64, type.minY(), type.minY() + type.height() - 6), 128, 64)), false);
        };
        draft.put(key, new LevelStem(stem.type(), generator)); selected = key; refreshRows();
    }

    @Override
    public void onClose() {
        if (!applied && (!draft.equals(initial) || studioOptionsChanged)) {
            minecraft.gui.setScreen(new StudioConfirmScreen(discard -> minecraft.gui.setScreen(discard ? parent : this),
                    Component.literal("Discard world draft?"), Component.literal("Changes in this editing session have not been applied to the new world."),
                    Component.literal("Discard changes"), Component.literal("Keep editing")));
        } else minecraft.gui.setScreen(parent);
    }

    private static Component text(String name) {
        return Component.translatable("worldarchitect.dimensions." + name);
    }

    private final class DimensionList extends ObjectSelectionList<DimensionRow> {
        DimensionList(int left, int width, int height) {
            super(DimensionManagerScreen.this.minecraft, width, height, 50, 22);
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

package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.config.TerrainMaterial;
import com.gwsheridan.worldarchitect.config.TerrainShape;
import com.gwsheridan.worldarchitect.config.SingleBlockProfile;
import com.gwsheridan.worldarchitect.worldgen.SingleBlockGenerator;
import com.mojang.serialization.DataResult;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class SingleBlockScreen extends Screen implements AuthoringContext {
    @Override public String authoringKey() { return parent instanceof AuthoringContext context ? context.authoringKey() : dimension.getString(); }
    private static final Component MATERIAL_LABEL = Component.translatable("worldarchitect.editor.material");
    private final Screen parent;
    private final SingleBlockGenerator original;
    private final net.minecraft.core.RegistryAccess registries;
    private final Component dimension;
    private final Consumer<SingleBlockGenerator> save;
    private String material;
    private TerrainShape shape;
    private Component status = CommonComponents.EMPTY;
    private Button done;
    private int formTop;

    SingleBlockScreen(Screen parent, Component dimension, SingleBlockGenerator generator, net.minecraft.core.RegistryAccess registries, Consumer<SingleBlockGenerator> save) {
        super(Component.translatable("worldarchitect.editor.title"));
        this.parent = parent;
        this.original = generator;
        this.registries = registries;
        this.dimension = dimension;
        this.save = save;
        this.material = BlockStateParser.serialize(original.profile().material());
        this.shape = original.profile().shape();
    }

    @Override
    protected void init() {
        int center = width / 2;
        int formWidth = Math.min(300, width - 24);
        int left = center - formWidth / 2;
        int half = (formWidth - 8) / 2;
        formTop = Math.max(4, (height - 176) / 2);
        EditBox input = addRenderableWidget(new EditBox(font, left, formTop + 48, formWidth, 20, MATERIAL_LABEL));
        input.setMaxLength(512);
        input.setValue(material);
        int quickWidth = (formWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.editor.sand"), button -> input.setValue("minecraft:sand"))
                .bounds(left, formTop + 74, quickWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.editor.stone"), button -> input.setValue("minecraft:stone"))
                .bounds(left + quickWidth + 4, formTop + 74, quickWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.blocks.browse"), button ->
                minecraft.gui.setScreen(new BlockPickerScreen(this, material, picked -> material = picked)))
                .bounds(left + 2 * (quickWidth + 4), formTop + 74, quickWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldarchitect.terrain.title"), button ->
                minecraft.gui.setScreen(new TerrainShapeScreen(this, shape, original.getMinY(), original.getGenDepth(), registries, updated -> shape = updated)))
                .bounds(left, formTop + 122, formWidth, 20).build());
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> apply())
                .bounds(left, formTop + 148, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + half + 8, formTop + 148, half, 20).build());
        input.setResponder(this::validate);
        validate(material);
        setInitialFocus(input);
    }

    private void validate(String value) {
        material = value;
        var result = configured();
        done.active = result.isSuccess();
        status = result.error()
                .<Component>map(error -> Component.literal(error.message()))
                .orElseGet(() -> Component.translatable("worldarchitect.editor.valid"));
        done.setTooltip(done.active ? null : Tooltip.create(status));
    }

    private void apply() {
        configured().result().ifPresent(candidate -> {
            save.accept(candidate);
            onClose();
        });
    }

    private DataResult<SingleBlockGenerator> configured() {
        return TerrainMaterial.parse(material).flatMap(state -> {
            var profile = new SingleBlockProfile(state, shape);
            SingleBlockGenerator candidate = original.withProfile(profile);
            return candidate.contentProblem().<DataResult<SingleBlockGenerator>>map(problem -> DataResult.error(() -> problem))
                    .orElseGet(() -> DataResult.success(candidate));
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int textWidth = Math.min(300, width - 24);
        graphics.centeredText(font, title, center, formTop, 0xFFF1E7CE);
        graphics.centeredText(font, font.plainSubstrByWidth(dimension.getString(), width - 24), center, formTop + 15, 0xFFCCCCCC);
        graphics.text(font, MATERIAL_LABEL, center - textWidth / 2, formTop + 36, 0xFFFFFFFF);
        var lines = font.split(status, textWidth);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), center - textWidth / 2, formTop + 101 + i * 9, done.active ? 0xFFACEAB8 : 0xFFFFAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.config.TerrainMaterial;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

final class BlockPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> use;
    private final List<CatalogBlock> catalog;
    private final BlockPickerHistory history = BlockPickerHistory.get();
    private String query = "";
    private String namespace = "";
    private Filter filter = Filter.ALL;
    private @Nullable BlockState selected;
    private List<Property<?>> properties = List.of();
    private int propertyIndex;
    private BlockList list;
    private Button apply;
    private Button favourite;
    private Button property;
    private Button value;
    private Component status = CommonComponents.EMPTY;

    BlockPickerScreen(Screen parent, String material, Consumer<String> use) {
        super(text("title"));
        this.parent = parent;
        this.use = use;
        selected = TerrainMaterial.parse(material).result().orElse(null);
        if (selected != null) {
            properties = List.copyOf(selected.getProperties());
        }
        catalog = BuiltInRegistries.BLOCK.entrySet().stream().map(entry -> {
            var block = entry.getValue();
            return new CatalogBlock(entry.getKey().identifier(), block, normalize(block.getName().getString()), block.asItem().getDefaultInstance());
        }).sorted(Comparator.comparing(CatalogBlock::name).thenComparing(entry -> entry.id().toString())).toList();
    }

    @Override
    protected void init() {
        int span = Math.min(width - 24, 460);
        int left = (width - span) / 2;
        int half = (span - 6) / 2;
        int nsWidth = Math.min(110, span / 3);
        EditBox search = field(left, span - nsWidth - 6, "search", query, next -> query = next);
        field(left + span - nsWidth, nsWidth, "namespace", namespace, next -> namespace = next);
        addRenderableWidget(CycleButton.builder(Filter::label, filter).withValues(Filter.values()).displayOnlyValue()
                .create(left, 43, half, 20, text("filter"), (button, next) -> {
                    filter = next;
                    refresh();
                }));
        favourite = button("favourite", left + half + 6, 43, half, this::toggleFavourite);
        list = addRenderableWidget(new BlockList(left, span, height - 132));
        property = button("property", left, height - 64, half, this::nextProperty);
        value = button("value", left + half + 6, height - 64, half, this::nextValue);
        apply = button("apply", left, height - 26, half, this::apply);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> onClose())
                .bounds(left + half + 6, height - 26, half, 20).build());
        refresh();
        setInitialFocus(search);
    }

    private EditBox field(int x, int width, String label, String initial, Consumer<String> edit) {
        EditBox field = addRenderableWidget(new EditBox(font, x, 20, width, 20, text(label)));
        field.setHint(text(label));
        field.setMaxLength(128);
        field.setValue(initial);
        field.setResponder(next -> {
            edit.accept(next);
            refresh();
        });
        return field;
    }

    private Button button(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(text(label), ignored -> action.run()).bounds(x, y, width, 20).build());
    }

    private void refresh() {
        String term = normalize(query);
        String mod = normalize(namespace);
        var recents = filter == Filter.RECENT ? history.recents() : List.<Identifier>of();
        var entries = catalog.stream().filter(entry -> (mod.isEmpty() || entry.id().getNamespace().equals(mod))
                && (entry.name().contains(term) || entry.id().toString().contains(term))
                && switch (filter) {
                    case ALL -> true;
                    case FAVOURITES -> history.favourite(entry.id());
                    case RECENT -> recents.contains(entry.id());
                });
        if (filter == Filter.RECENT) {
            entries = entries.sorted(Comparator.comparingInt(entry -> recents.indexOf(entry.id())));
        }
        var rows = entries.map(Row::new).toList();
        var preferred = rows.stream().filter(row -> selected != null && row.entry.block() == selected.getBlock()).findFirst();
        list.replaceEntries(rows);
        list.setSelected(preferred.orElse(rows.isEmpty() ? null : rows.getFirst()));
        list.scrollToSelected();
    }

    private void select(@Nullable Row row) {
        if (row == null) {
            selected = null;
            properties = List.of();
            propertyIndex = 0;
        } else if (selected == null || selected.getBlock() != row.entry.block()) {
            selected = row.entry.block().defaultBlockState();
            properties = List.copyOf(selected.getProperties());
            propertyIndex = 0;
        }
        updateControls();
    }

    private void updateControls() {
        var row = list.getSelected();
        boolean hasProperty = selected != null && !properties.isEmpty();
        property.active = hasProperty && properties.size() > 1;
        value.active = hasProperty;
        favourite.active = row != null && history.canFavourite(row.entry.id());
        favourite.setMessage(text(row != null && history.favourite(row.entry.id()) ? "unfavourite" : "favourite"));
        if (hasProperty) {
            var current = properties.get(propertyIndex);
            property.setMessage(Component.translatable("worldsunwritten.blocks.property_name", current.getName()));
            value.setMessage(Component.translatable("worldsunwritten.blocks.property_value", propertyValue(selected, current)));
        } else {
            property.setMessage(text("no_properties"));
            value.setMessage(CommonComponents.EMPTY);
        }
        var result = selected == null ? null : TerrainMaterial.validate(selected);
        apply.active = result != null && result.isSuccess();
        status = result == null ? text("no_matches") : result.error().<Component>map(error -> Component.literal(error.message()))
                .orElseGet(() -> Component.literal(BlockStateParser.serialize(selected)));
        apply.setTooltip(apply.active ? null : Tooltip.create(status));
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private void nextProperty() {
        propertyIndex = (propertyIndex + 1) % properties.size();
        updateControls();
    }

    private void nextValue() {
        if (selected != null) {
            selected = selected.cycle(properties.get(propertyIndex));
            updateControls();
        }
    }

    private void toggleFavourite() {
        var row = list.getSelected();
        if (row != null) {
            history.toggleFavourite(row.entry.id());
            refresh();
        }
    }

    private void apply() {
        if (selected != null && TerrainMaterial.validate(selected).isSuccess()) {
            history.use(BuiltInRegistries.BLOCK.getKey(selected.getBlock()));
            use.accept(BlockStateParser.serialize(selected));
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, 7, 0xFFF1E7CE);
        graphics.centeredText(font, font.plainSubstrByWidth(status.getString(), width - 24), width / 2, height - 38,
                apply.active ? 0xFFB1EAC2 : 0xFFFFB5AF);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static String normalize(String input) {
        return input.strip().toLowerCase(Locale.ROOT);
    }

    private static Component text(String key) {
        return Component.translatable("worldsunwritten.blocks." + key);
    }

    private record CatalogBlock(Identifier id, Block block, String name, ItemStack icon) {}

    private enum Filter {
        ALL, FAVOURITES, RECENT;

        Component label() {
            return text(name().toLowerCase(Locale.ROOT));
        }
    }

    private final class BlockList extends ObjectSelectionList<Row> {
        BlockList(int left, int width, int height) {
            super(BlockPickerScreen.this.minecraft, width, height, 66, 24);
            setX(left);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 16;
        }

        @Override
        public void setSelected(@Nullable Row row) {
            super.setSelected(row);
            select(row);
        }

        void scrollToSelected() {
            if (getSelected() != null) {
                centerScrollOn(getSelected());
            }
        }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final CatalogBlock entry;

        Row(CatalogBlock entry) {
            this.entry = entry;
        }

        @Override
        public Component getNarration() {
            return entry.block().getName();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tick) {
            int x = getX() + 3;
            int y = getY() + 1;
            if (entry.icon().isEmpty()) {
                int color = entry.block().defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
                graphics.fill(x, y + 2, x + 15, y + 17, 0xFF000000 | color);
            } else {
                graphics.item(entry.icon(), x, y + 2);
            }
            int space = getWidth() - 30;
            graphics.text(font, font.plainSubstrByWidth(entry.block().getName().getString(), space), x + 22, y + 1, 0xFFFFFFFF);
            graphics.text(font, font.plainSubstrByWidth(entry.id().toString(), space), x + 22, y + 11, 0xFFB8C6D2);
        }
    }
}

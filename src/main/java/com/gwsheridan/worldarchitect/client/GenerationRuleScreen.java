package com.gwsheridan.worldarchitect.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Edits a list of persistent, codec-validated rules without exposing serialized JSON. */
final class GenerationRuleScreen<T> extends Screen {
    private final Screen parent;
    private final Codec<T> codec;
    private final DynamicOps<JsonElement> ops;
    private final List<Field> fields;
    private final List<List<String>> drafts = new ArrayList<>();
    private final int limit;
    private final Consumer<List<T>> doneCallback;
    private final Component guidance;
    private int index;
    private int top;
    private int left;
    private int span;
    private boolean compact;
    private Button done;
    private Component status = CommonComponents.EMPTY;

    GenerationRuleScreen(Screen parent, Component title, Component guidance, Codec<T> codec, DynamicOps<JsonElement> ops,
            List<Field> fields, List<T> rules, int limit, Consumer<List<T>> doneCallback) {
        super(title);
        this.parent = parent;
        this.guidance = guidance;
        this.codec = codec;
        this.ops = ops;
        this.fields = fields;
        this.limit = limit;
        this.doneCallback = doneCallback;
        for (T rule : rules) {
            JsonObject encoded = codec.encodeStart(ops, rule).getOrThrow().getAsJsonObject();
            List<String> values = new ArrayList<>();
            for (Field field : fields) {
                JsonElement value = encoded.get(field.name());
                values.add(value == null ? field.missingValue() : field.kind() == Kind.LIST
                        ? StreamSupport.stream(value.getAsJsonArray().spliterator(), false).map(JsonElement::getAsString).collect(Collectors.joining(", "))
                        : value.getAsString());
            }
            drafts.add(values);
        }
    }

    @Override
    protected void init() {
        span = Math.min(392, width - 24);
        compact = height < (fields.size() > 6 ? 235 : 218);
        int footer = footerY();
        top = compact ? 0 : (height - footer - 24) / 2;
        left = (width - span) / 2;
        int half = (span - 8) / 2;
        int navigationY = compact ? 20 : 30;
        int editY = compact ? 20 : 55;
        int offset = compact ? 38 : 0;
        control(Component.literal("←"), left, navigationY, 34, () -> select(index - 1)).active = index > 0;
        control(Component.literal("→"), left + span - 34, navigationY, 34, () -> select(index + 1)).active = index < drafts.size() - 1;
        control(text("add"), left + offset, editY, half - offset, () -> {
            drafts.add(new ArrayList<>(fields.stream().map(Field::initial).toList()));
            select(drafts.size() - 1);
        }).active = drafts.size() < limit;
        control(text("remove"), left + half + 8, editY, half - offset, () -> {
            drafts.remove(index);
            select(Math.max(0, Math.min(index, drafts.size() - 1)));
        }).active = !drafts.isEmpty();
        done = control(CommonComponents.GUI_DONE, left, footer, half, this::save);
        control(CommonComponents.GUI_CANCEL, left + half + 8, footer, half, this::onClose);

        if (!drafts.isEmpty()) {
            for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
                int slot = fieldIndex;
                Field field = fields.get(fieldIndex);
                int x = left + (fieldIndex % 2) * (half + 8);
                int y = top + fieldY(fieldIndex);
                int fieldHeight = compact ? 14 : fields.size() > 6 ? 16 : 18;
                EditBox entry = addRenderableWidget(new EditBox(font, x, y, half, fieldHeight, label(field)));
                entry.setMaxLength(switch (field.kind()) {
                    case TEXT -> 512;
                    case LIST -> 1500;
                    default -> 12;
                });
                entry.setValue(drafts.get(index).get(fieldIndex));
                if (field.kind() == Kind.LIST) {
                    entry.setHint(text("all_biomes"));
                }
                entry.setResponder(value -> {
                    drafts.get(index).set(slot, value);
                    validate();
                });
            }
        }
        validate();
    }

    private Button control(Component label, int x, int y, int span, Runnable action) {
        return addRenderableWidget(Button.builder(label, button -> action.run()).bounds(x, top + y, span, 20).build());
    }

    private void select(int selected) {
        index = selected;
        rebuildWidgets();
    }

    private int fieldY(int slot) {
        return compact ? 59 + (slot / 2) * 23 : fields.size() > 6 ? 89 + (slot / 2) * 26 : 93 + (slot / 2) * 30;
    }

    private int footerY() {
        return compact ? height - 24 : fields.size() > 6 ? 211 : 194;
    }

    private DataResult<List<T>> parse() {
        List<T> decoded = new ArrayList<>();
        for (int row = 0; row < drafts.size(); row++) {
            JsonObject json = new JsonObject();
            List<String> draft = drafts.get(row);
            for (int slot = 0; slot < fields.size(); slot++) {
                Field field = fields.get(slot);
                String value = draft.get(slot).strip();
                if (value.isEmpty() && field.optional()) {
                    continue;
                }
                try {
                    switch (field.kind()) {
                        case TEXT -> json.addProperty(field.name(), value);
                        case LIST -> {
                            JsonArray ids = new JsonArray();
                            for (String id : value.split(",", -1)) {
                                ids.add(id.strip());
                            }
                            json.add(field.name(), ids);
                        }
                        case INTEGER -> json.addProperty(field.name(), Integer.parseInt(value));
                        case BOOLEAN -> {
                            if (!value.equals("true") && !value.equals("false")) {
                                throw new IllegalArgumentException();
                            }
                            json.addProperty(field.name(), Boolean.parseBoolean(value));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    int number = row + 1;
                    return DataResult.error(() -> label(field).getString() + " · " + text("invalid_value").getString() + " " + number);
                }
            }
            var result = codec.parse(ops, json);
            if (result.error().isPresent()) {
                int number = row + 1;
                return DataResult.error(() -> text("rule").getString() + " " + number + " · " + result.error().orElseThrow().message());
            }
            decoded.add(result.getOrThrow());
        }
        return DataResult.success(List.copyOf(decoded));
    }

    private void validate() {
        var result = parse();
        done.active = result.isSuccess();
        status = result.error().<Component>map(error -> Component.literal(error.message())).orElse(guidance);
    }

    private void save() {
        parse().result().ifPresent(values -> {
            doneCallback.accept(values);
            onClose();
        });
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        Component position = drafts.isEmpty() ? text("none") : Component.translatable("worldarchitect.features.position", index + 1, drafts.size());
        graphics.centeredText(font, compact ? title.copy().append(" · ").append(position) : title, width / 2, top + 7, 0xFFF1E7CE);
        if (!compact) {
            graphics.centeredText(font, position, width / 2, top + 36, 0xFFEAEAEA);
        }
        int half = (span - 8) / 2;
        if (!drafts.isEmpty()) {
            for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
                String label = label(fields.get(fieldIndex)).getString();
                graphics.text(font, font.plainSubstrByWidth(label, half), left + (fieldIndex % 2) * (half + 8),
                        top + fieldY(fieldIndex) - 10, 0xFFEAEAEA);
            }
        }
        var lines = font.split(status, span);
        for (int i = 0; i < Math.min(compact ? 1 : 2, lines.size()); i++) {
            graphics.text(font, lines.get(i), left, top + footerY() - (compact ? 11 : 20) + i * 9, done.active ? 0xFFCCCCCC : 0xFFFFAAAA);
        }
    }

    static Component text(String key) {
        return Component.translatable("worldarchitect.features." + key);
    }

    private static Component label(Field field) {
        return text(field.kind() == Kind.LIST ? "rule_biomes" : field.name());
    }

    enum Kind { TEXT, LIST, INTEGER, BOOLEAN }

    record Field(String name, Kind kind, String initial, boolean optional, String missingValue) {
        Field(String name, Kind kind, String initial) {
            this(name, kind, initial, false, "");
        }
    }
}

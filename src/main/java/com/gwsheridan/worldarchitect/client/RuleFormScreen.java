package com.gwsheridan.worldarchitect.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gwsheridan.worldarchitect.rules.RuleSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;

/** Small pages keep every condition and action editable without a serialized JSON text box. */
final class RuleFormScreen extends Screen {
    enum Kind { TEXT, INTEGER, LONG, LIST, POSITION }
    record Field(String path, String label, Kind kind, boolean optional) {}
    private final Screen parent;
    private final String guidance;
    private final JsonObject original;
    private final RegistryOps<JsonElement> ops;
    private final List<Field> fields;
    private final List<String> values = new ArrayList<>();
    private final Consumer<JsonObject> save;
    private Button apply;
    private String error = "";
    private int left, span, firstY, rowHeight;

    RuleFormScreen(Screen parent, String title, String guidance, JsonObject original, RegistryOps<JsonElement> ops,
            List<Field> fields, Consumer<JsonObject> save) {
        super(Component.literal(title));
        this.parent = parent; this.guidance = guidance; this.original = original.deepCopy(); this.ops = ops; this.fields = fields; this.save = save;
        for (var field : fields) {
            JsonElement value = get(original, field.path());
            if (value == null) values.add("");
            else if (value.isJsonArray()) {
                var parts = new ArrayList<String>(); value.getAsJsonArray().forEach(part -> parts.add(part.getAsString()));
                values.add(String.join(field.kind() == Kind.POSITION ? " " : ", ", parts));
            } else values.add(value.getAsString());
        }
    }

    @Override protected void init() {
        span = Math.min(400, width - 24); left = (width - span) / 2;
        boolean compact = height < 230; firstY = compact ? 44 : 68; rowHeight = compact ? 22 : 32;
        int half = (span - 8) / 2;
        apply = addRenderableWidget(Button.builder(Component.literal("Apply"), b -> {
            var candidate = parse(); if (candidate != null) { save.accept(candidate); onClose(); }
        }).bounds(left, height - 24, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(left + half + 8, height - 24, half, 20).build());
        for (int i = 0; i < fields.size(); i++) {
            int index = i; var field = fields.get(i);
            var entry = addRenderableWidget(new EditBox(font, left + i % 2 * (half + 8), firstY + i / 2 * rowHeight, half, compact ? 14 : 18, Component.literal(field.label())));
            entry.setMaxLength(field.kind() == Kind.LIST ? 1500 : 512);
            entry.setValue(values.get(i));
            entry.setResponder(text -> { values.set(index, text); validate(); });
        }
        validate();
    }

    private JsonObject parse() {
        try {
            JsonObject candidate = original.deepCopy();
            for (int i = 0; i < fields.size(); i++) {
                var field = fields.get(i); String text = values.get(i).strip();
                String[] path = field.path().split("\\."); JsonObject object = candidate;
                for (int p = 0; p < path.length - 1; p++) {
                    if (!object.has(path[p])) object.add(path[p], new JsonObject());
                    object = object.getAsJsonObject(path[p]);
                }
                String key = path[path.length - 1];
                if (text.isEmpty() && field.optional()) { object.remove(key); continue; }
                switch (field.kind()) {
                    case TEXT -> object.addProperty(key, text);
                    case INTEGER -> object.addProperty(key, Integer.parseInt(text));
                    case LONG -> object.addProperty(key, Long.parseLong(text));
                    case LIST, POSITION -> {
                        var array = new JsonArray();
                        for (String part : text.split(field.kind() == Kind.POSITION ? "\\s+" : ",")) {
                            if (field.kind() == Kind.POSITION) array.add(Integer.parseInt(part)); else array.add(part.strip());
                        }
                        object.add(key, array);
                    }
                }
            }
            prune(candidate);
            var decoded = RuleSet.Rule.CODEC.parse(ops, candidate);
            if (decoded.error().isPresent()) { error = decoded.error().orElseThrow().message(); return null; }
            error = "";
            return RuleSet.Rule.CODEC.encodeStart(ops, decoded.getOrThrow()).getOrThrow().getAsJsonObject();
        } catch (RuntimeException e) { error = e.getMessage() == null ? "Check the entered values." : e.getMessage(); return null; }
    }

    private void validate() { apply.active = parse() != null; }
    private static JsonElement get(JsonObject root, String path) {
        JsonElement value = root;
        for (String part : path.split("\\.")) { if (value == null || !value.isJsonObject()) return null; value = value.getAsJsonObject().get(part); }
        return value;
    }
    private static void prune(JsonObject root) {
        for (String key : new ArrayList<>(root.keySet())) {
            if (root.get(key).isJsonObject()) { var child = root.getAsJsonObject(key); prune(child); if (child.isEmpty()) root.remove(key); }
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, 7, 0xFFF1E7CE);
        var help = font.split(Component.literal(guidance), span);
        for (int i = 0; i < Math.min(height < 230 ? 1 : 2, help.size()); i++) graphics.text(font, help.get(i), left, 22 + i * 9, 0xFFCCCCCC);
        int half = (span - 8) / 2;
        for (int i = 0; i < fields.size(); i++) graphics.text(font, font.plainSubstrByWidth(fields.get(i).label(), half), left + i % 2 * (half + 8), firstY + i / 2 * rowHeight - 10, 0xFFEAEAEA);
        var lines = font.split(Component.literal(error), span);
        for (int i = 0; i < Math.min(2, lines.size()); i++) graphics.text(font, lines.get(i), left, height - 48 + i * 9, 0xFFFFAAAA);
    }
}

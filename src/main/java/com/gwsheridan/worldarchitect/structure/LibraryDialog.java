package com.gwsheridan.worldarchitect.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class LibraryDialog {
    static final int PAGE_SIZE = 12;

    private LibraryDialog() {}

    static void library(ServerPlayer player, List<Identifier> ids, int page) {
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.clamp(page, 1, pages);
        var actions = new ArrayList<ActionButton>();
        actions.add(button("Capture selection", "/wa_structure builder"));
        actions.add(button("Import template", "/wa_structure builder import"));
        int first = (current - 1) * PAGE_SIZE;
        for (Identifier id : ids.subList(first, Math.min(ids.size(), first + PAGE_SIZE))) {
            actions.add(button(id.toString(), "/wa_structure preview " + id));
        }
        if (current > 1) {
            actions.add(button("Previous page", "/wa_structure list " + (current - 1)));
        }
        if (current < pages) {
            actions.add(button("Next page", "/wa_structure list " + (current + 1)));
        }
        String description = ids.isEmpty() ? "No saved templates."
                : "%d templates · page %d of %d".formatted(ids.size(), current, pages);
        show(player, "World Architect · Structure Library", List.of(message(description)), actions, Optional.empty());
    }

    static void preview(ServerPlayer player, Identifier id, LibraryEntry entry, int layer) {
        var body = new ArrayList<DialogBody>();
        body.add(message(summary(id, entry)));
        var rules = entry.rules();
        body.add(message("Placement: " + rules.terrain().key() + " · " + rules.collision().key()
                + " · Y " + rules.minY() + " to " + rules.maxY() + "\nDimension: "
                + rules.dimension().map(Identifier::toString).orElse("Any") + " · Biome: "
                + rules.biome().map(Identifier::toString).orElse("Any")));
        if (!entry.markers().isEmpty()) {
            var markers = new java.util.TreeMap<>(entry.markers());
            for (var entryMarker : markers.entrySet()) {
                var marker = entryMarker.getValue();
                body.add(message(entryMarker.getKey() + " · " + marker.pos().toShortString() + " · " + marker.facing().getSerializedName()));
            }
        }
        body.add(message(layer < 0 ? "Top view · north at the top" : "Horizontal slice at local Y " + layer));
        body.add(new PlainMessage(projection(entry, layer), 300));
        List<ActionButton> actions = new ArrayList<>();
        int max = entry.template().size().getY() - 1;
        if (layer < 0) {
            actions.add(button("View bottom slice", "/wa_structure preview " + id + " 0"));
        } else {
            actions.add(button("Top view", "/wa_structure preview " + id));
            if (layer > 0) {
                actions.add(button("Slice below", "/wa_structure preview " + id + " " + (layer - 1)));
            }
            if (layer < max) {
                actions.add(button("Slice above", "/wa_structure preview " + id + " " + (layer + 1)));
            }
        }
        actions.add(button("3D viewer", "/wa_structure view3d " + id));
        actions.add(button("Stage here", "/wa_structure stage " + id + " ~ ~ ~"));
        actions.add(button("Edit template", "/wa_structure editor " + id));
        show(player, entry.title(), body, actions, Optional.of(button("Library", "/wa_structure")));
    }

    static void builder(ServerPlayer player, @Nullable BlockPos first, @Nullable BlockPos second) {
        var body = List.<DialogBody>of(message("Corner 1 · " + showCorner(first) + "\nCorner 2 · " + showCorner(second)),
                message("Close the dialog to move to the next corner. Reopen it to record your position."));
        var actions = new ArrayList<ActionButton>();
        actions.add(button("Set corner 1 here", "/wa_structure builder pos1"));
        actions.add(button("Set corner 2 here", "/wa_structure builder pos2"));
        List<Input> inputs = List.of();
        if (first != null && second != null) {
            actions.add(button("Outline selection", "/wa_structure outline"));
            inputs = List.of(input("id", "New template ID", "builds:", 120));
            actions.add(command("Capture", "/wa_structure capture $(id)"));
        }
        show(player, "Capture a structure", body, inputs, actions, Optional.of(button("Library", "/wa_structure")));
    }

    static void importer(ServerPlayer player) {
        show(player, "Import a template", List.of(message("Use namespace:path IDs. On a dedicated server, place the file on the server first.")),
                List.of(input("source", "Source ID", "examples:", 120), input("target", "New template ID", "builds:", 120)),
                List.of(command("Import native .nbt", "/wa_structure import $(source) $(target)"),
                        command("Import Sponge .schem", "/wa_structure import_schem $(source) $(target)")),
                Optional.of(button("Library", "/wa_structure")));
    }

    static void editor(ServerPlayer player, Identifier id, LibraryEntry entry, String section) {
        switch (section) {
            case "palette" -> palette(player, id, entry);
            case "transform" -> transform(player, id);
            case "markers" -> markers(player, id, entry);
            case "placement" -> placement(player, id);
            default -> show(player, entry.title(), List.of(message(summary(id, entry))),
                    List.of(button("Edit palette", "/wa_structure editor " + id + " palette"),
                            button("Rotate or mirror", "/wa_structure editor " + id + " transform"),
                            button("Edit spawn markers", "/wa_structure editor " + id + " markers"),
                            button("Place template", "/wa_structure editor " + id + " placement")),
                    Optional.of(button("Preview", "/wa_structure preview " + id)));
        }
    }

    private static void palette(ServerPlayer player, Identifier id, LibraryEntry entry) {
        var palette = new TreeMap<String, Integer>();
        entry.template().blocks().forEach(cell -> palette.merge(BlockStateParser.serialize(cell.state()), 1, Integer::sum));
        StringBuilder states = new StringBuilder();
        palette.entrySet().stream().limit(12).forEach(state -> states.append(state.getKey()).append(" · ").append(state.getValue()).append('\n'));
        if (palette.size() > 12) {
            states.append("Additional states · ").append(palette.size() - 12);
        }
        show(player, "Template palette", List.of(message(states.toString())),
                List.of(input("from", "Exact state to replace", palette.firstKey(), 512), input("to", "Replacement state", "", 512)),
                List.of(command("Replace state", "/wa_structure replace " + id + " $(from) $(to)")), Optional.of(back(id)));
    }

    private static void transform(ServerPlayer player, Identifier id) {
        show(player, "Transform template", List.of(message("Apply saves the transformed blocks and spawn markers as a new revision.")),
                orientation(), List.of(command("Apply transformation", "/wa_structure transform " + id + " $(rotation) $(mirror)")),
                Optional.of(back(id)));
    }

    private static void markers(ServerPlayer player, Identifier id, LibraryEntry entry) {
        var body = new ArrayList<DialogBody>();
        body.add(message("Use local feet coordinates. The marker needs two air cells above a safe block."));
        new TreeMap<>(entry.markers()).forEach((name, marker) -> body.add(message(name + " · " + marker.pos().toShortString()
                + " · " + marker.facing().getSerializedName())));
        show(player, "Spawn markers", body,
                List.of(input("name", "Marker name", "entry", 32), input("position", "Local X Y Z", "0 1 0", 36),
                        choice("facing", "Facing", "north", "east", "south", "west")),
                List.of(command("Set marker", "/wa_structure marker set " + id + " $(name) $(position) $(facing)"),
                        command("Remove marker", "/wa_structure marker remove " + id + " $(name)")), Optional.of(back(id)));
    }

    private static void placement(ServerPlayer player, Identifier id) {
        var inputs = new ArrayList<Input>();
        inputs.add(input("origin", "Origin X Y Z", "~ ~ ~", 80));
        inputs.addAll(orientation());
        show(player, "Place template", List.of(message("Enter the lower corner in your current dimension. The saved placement rules apply.")),
                inputs, List.of(command("Place", "/wa_structure place " + id + " $(origin) $(rotation) $(mirror)")), Optional.of(back(id)));
    }

    private static ActionButton back(Identifier id) {
        return button("Template editor", "/wa_structure editor " + id);
    }

    private static String showCorner(@Nullable BlockPos position) {
        return position == null ? "Unset" : position.toShortString();
    }

    private static List<Input> orientation() {
        return List.of(choice("rotation", "Rotation", "none", "cw90", "cw180", "ccw90"),
                choice("mirror", "Mirror", "none", "left_right", "front_back"));
    }

    private static Input choice(String key, String label, String... values) {
        return new Input(key, new SingleOptionInput(218, java.util.Arrays.stream(values)
                .map(value -> new SingleOptionInput.Entry(value, Optional.empty(), value.equals(values[0]))).toList(), Component.literal(label), true));
    }

    private static Input input(String key, String label, String value, int maxLength) {
        return new Input(key, new TextInput(218, Component.literal(label), true, value, maxLength, Optional.empty()));
    }

    static String summary(Identifier id, LibraryEntry entry) {
        BlockPos size = entry.template().size();
        var namespaces = new TreeSet<String>();
        entry.template().blocks().forEach(cell -> namespaces.add(BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getNamespace()));
        namespaces.remove("minecraft");
        return id + " · revision " + entry.revision() + " · " + entry.category() + "\n"
                + size.getX() + " × " + size.getY() + " × " + size.getZ() + " · " + entry.template().blocks().size() + " blocks"
                + (entry.author().isBlank() ? "" : " · " + entry.author()) + "\nBlock namespaces required: "
                + (namespaces.isEmpty() ? "Minecraft" : String.join(", ", namespaces));
    }

    private static Component projection(LibraryEntry entry, int layer) {
        BlockPos size = entry.template().size();
        int width = size.getX();
        int depth = size.getZ();
        var top = new NativeTemplate.Cell[width * depth];
        for (var cell : entry.template().blocks()) {
            BlockPos pos = cell.pos();
            int column = pos.getZ() * width + pos.getX();
            if ((layer < 0 && (top[column] == null || pos.getY() > top[column].pos().getY())) || layer == pos.getY()) {
                top[column] = cell;
            }
        }
        boolean[] markerColumns = new boolean[top.length];
        for (var marker : entry.markers().values()) {
            if (layer < 0 || layer == marker.pos().getY()) {
                markerColumns[marker.pos().getZ() * width + marker.pos().getX()] = true;
            }
        }
        var image = Component.empty();
        for (int z = 0; z < depth; z++) {
            if (z > 0) {
                image.append("\n");
            }
            for (int x = 0; x < width; x++) {
                int column = z * width + x;
                var cell = top[column];
                int color = markerColumns[column] ? 0xFFCB54 : cell == null ? 0x2C3037 : color(cell.state(), cell.pos());
                image.append(Component.literal("█").withColor(color));
            }
        }
        return image;
    }

    private static int color(BlockState state, BlockPos pos) {
        int color = state.getMapColor(EmptyBlockGetter.INSTANCE, pos).col;
        return color == 0 ? 0xABB5C2 : color;
    }

    private static PlainMessage message(String text) {
        return new PlainMessage(Component.literal(text), 300);
    }

    private static ActionButton button(String label, String command) {
        return new ActionButton(new CommonButtonData(Component.literal(label), Optional.of(Component.literal(label)), 200),
                Optional.ofNullable(command).map(value -> new StaticAction(new ClickEvent.RunCommand(value))));
    }

    private static ActionButton command(String label, String template) {
        ParsedTemplate command = ParsedTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(template)).getOrThrow();
        return new ActionButton(new CommonButtonData(Component.literal(label), Optional.empty(), 200),
                Optional.of(new CommandTemplate(command)));
    }

    private static void show(ServerPlayer player, String title, List<DialogBody> body, List<ActionButton> actions, Optional<ActionButton> exit) {
        show(player, title, body, List.of(), actions, exit);
    }

    private static void show(ServerPlayer player, String title, List<DialogBody> body, List<Input> inputs,
            List<ActionButton> actions, Optional<ActionButton> exit) {
        CommonDialogData header = new CommonDialogData(Component.literal(title), Optional.empty(), true, false, DialogAction.CLOSE, body, inputs);
        player.openDialog(Holder.direct(new MultiActionDialog(header, actions, exit, 1)));
    }
}

package com.gwsheridan.worldarchitect.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
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
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

final class LibraryDialog {
    static final int PAGE_SIZE = 12;

    private LibraryDialog() {}

    static void library(ServerPlayer player, List<Identifier> ids, int page) {
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.clamp(page, 1, pages);
        var actions = new ArrayList<ActionButton>();
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
        if (actions.isEmpty()) {
            actions.add(button("Close", null));
        }
        String description = ids.isEmpty() ? "Capture a selection or import a native Minecraft structure."
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
        show(player, entry.title(), body, actions, Optional.of(button("Library", "/wa_structure")));
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

    private static void show(ServerPlayer player, String title, List<DialogBody> body, List<ActionButton> actions, Optional<ActionButton> exit) {
        CommonDialogData header = new CommonDialogData(Component.literal(title), Optional.empty(), true, false, DialogAction.CLOSE, body, List.of());
        player.openDialog(Holder.direct(new MultiActionDialog(header, actions, exit, 1)));
    }
}

package com.gwsheridan.worldarchitect.structure;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NumericTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The single-palette vanilla structure format used by the library and the island importer. */
public record NativeTemplate(BlockPos size, List<Cell> blocks) {
    public static final int MAX_SIDE = 32;
    private static final int MAX_CELLS = MAX_SIDE * MAX_SIDE * MAX_SIDE;

    public NativeTemplate {
        size = size.immutable();
        blocks = List.copyOf(blocks);
    }

    /** Detached template with only supported decorative block-entity metadata. */
    public NativeTemplate sanitized(net.minecraft.core.HolderLookup.Provider registries) throws IOException {
        var cells = new ArrayList<Cell>();
        for (var cell : blocks) {
            if (TemplateMetadata.restricted(cell.state())) throw new IOException("Unsupported generation template block " + cell.state());
            cells.add(new Cell(cell.pos(), cell.state(), TemplateMetadata.sanitize(cell.state(), cell.metadata(), registries)));
        }
        return NativeTemplate.read(new NativeTemplate(size, cells).toTag());
    }

    public static NativeTemplate read(CompoundTag root) throws IOException {
        if (root.contains("palettes")) {
            throw new IOException("The library supports native structures with a single palette.");
        }
        BlockPos size = readPosition(root.getListOrEmpty("size"));
        checkSize(size);
        List<BlockState> palette = new ArrayList<>();
        ListTag paletteTag = root.getListOrEmpty("palette");
        if (paletteTag.size() > MAX_CELLS) {
            throw new IOException("Structure palette exceeds " + MAX_CELLS + " states.");
        }
        for (var value : paletteTag) {
            palette.add(readState(value));
        }

        ListTag entries = root.getListOrEmpty("blocks");
        if (entries.size() > size.getX() * size.getY() * size.getZ()) {
            throw new IOException("The structure has more cells than its bounds allow.");
        }
        Set<BlockPos> occupied = new HashSet<>();
        List<Cell> blocks = new ArrayList<>();
        for (var value : entries) {
            if (!(value instanceof CompoundTag block) || !isInteger(block.get("state"))) {
                throw new IOException("Invalid native structure block entry.");
            }
            int index = block.getIntOr("state", -1);
            BlockPos pos = readPosition(block.getListOrEmpty("pos"));
            if (index < 0 || index >= palette.size() || !inside(size, pos) || !occupied.add(pos)) {
                throw new IOException("Invalid or duplicate template cell at " + pos.toShortString());
            }
            CompoundTag metadata = block.getCompound("nbt").orElse(null);
            if (metadata == null && block.contains("nbt")) {
                throw new IOException("Invalid block-entity data at " + pos.toShortString());
            }
            blocks.add(new Cell(pos, palette.get(index), metadata));
        }
        return new NativeTemplate(size, blocks);
    }

    private static BlockState readState(net.minecraft.nbt.Tag value) throws IOException {
        String state;
        if (value instanceof CompoundTag entry) {
            boolean current = entry.contains("id");
            StringJoiner suffix = new StringJoiner(",", "[", "]").setEmptyValue("");
            String key = current ? "properties" : "Properties";
            var properties = entry.getCompound(key).orElse(null);
            if (properties == null && entry.contains(key)) {
                throw new IOException("Invalid block state properties.");
            }
            if (properties != null) {
                for (String name : properties.keySet().stream().sorted().toList()) {
                    String property = properties.getString(name).orElseThrow(() -> new IOException("Invalid block property " + name));
                    suffix.add(name + "=" + property);
                }
            }
            state = entry.getStringOr(current ? "id" : "Name", "") + suffix;
        } else {
            state = value.asString().orElseThrow(() -> new IOException("Invalid structure palette entry."));
        }
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, state, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IOException("Unknown block or block state " + state + ". " + e.getRawMessage().getString(), e);
        }
    }

    public CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        root.put("size", writePosition(size));
        Map<BlockState, Integer> indices = new LinkedHashMap<>();
        ListTag palette = new ListTag();
        ListTag entries = new ListTag();
        for (Cell cell : blocks) {
            int index = indices.computeIfAbsent(cell.state, state -> {
                int next = palette.size();
                palette.add(NbtUtils.writeBlockState(state));
                return next;
            });
            CompoundTag entry = new CompoundTag();
            entry.put("pos", writePosition(cell.pos));
            entry.putInt("state", index);
            if (cell.metadata != null) {
                entry.put("nbt", cell.metadata.copy());
            }
            entries.add(entry);
        }
        root.put("palette", palette);
        root.put("blocks", entries);
        root.put("entities", new ListTag());
        return NbtUtils.addCurrentDataVersion(root);
    }

    public static void checkSize(BlockPos size) throws IOException {
        if (size.getX() < 1 || size.getX() > MAX_SIDE || size.getY() < 1 || size.getY() > MAX_SIDE
                || size.getZ() < 1 || size.getZ() > MAX_SIDE) {
            throw new IOException("Structure bounds must be between 1 and " + MAX_SIDE + " blocks on each axis.");
        }
    }

    public static boolean inside(BlockPos size, BlockPos pos) {
        return pos.getX() >= 0 && pos.getX() < size.getX() && pos.getY() >= 0 && pos.getY() < size.getY()
                && pos.getZ() >= 0 && pos.getZ() < size.getZ();
    }

    static ListTag writePosition(BlockPos pos) {
        ListTag result = new ListTag();
        result.add(IntTag.valueOf(pos.getX()));
        result.add(IntTag.valueOf(pos.getY()));
        result.add(IntTag.valueOf(pos.getZ()));
        return result;
    }

    static BlockPos readPosition(ListTag tag) throws IOException {
        if (tag.size() != 3 || tag.stream().anyMatch(value -> !isInteger(value))) {
            throw new IOException("Expected three integer structure coordinates.");
        }
        return new BlockPos(tag.getIntOr(0, 0), tag.getIntOr(1, 0), tag.getIntOr(2, 0));
    }

    private static boolean isInteger(net.minecraft.nbt.Tag value) {
        return value instanceof NumericTag number && number.doubleValue() == number.intValue();
    }

    public record Cell(BlockPos pos, BlockState state, @Nullable CompoundTag metadata) {
        public Cell {
            pos = pos.immutable();
            metadata = metadata == null ? null : metadata.copy();
        }

        @Override
        public @Nullable CompoundTag metadata() {
            return metadata == null ? null : metadata.copy();
        }

        public boolean isSolidContent() {
            return !state.isAir() && !state.is(Blocks.STRUCTURE_VOID);
        }
    }
}

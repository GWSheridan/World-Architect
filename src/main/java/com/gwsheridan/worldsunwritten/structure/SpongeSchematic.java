package com.gwsheridan.worldsunwritten.structure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.resources.Identifier;

/** Converts the block portion of Sponge version 3 into the vanilla structure schema for data fixing. */
final class SpongeSchematic {
    private static final int MAX_PALETTE = NativeTemplate.MAX_SIDE * NativeTemplate.MAX_SIDE * NativeTemplate.MAX_SIDE;

    private SpongeSchematic() {}

    static CompoundTag toNative(CompoundTag root) throws IOException {
        return toNative(root,32);
    }

    static CompoundTag island(CompoundTag root) throws IOException {return toNative(root,64);}

    private static CompoundTag toNative(CompoundTag root,int side) throws IOException {
        CompoundTag schematic = compound(root, "Schematic");
        if (!(schematic.get("Version") instanceof IntTag) || schematic.getIntOr("Version", 0) != 3) {
            throw new IOException("The schematic must be Sponge version 3. Re-export it in that format.");
        }
        int version = schematic.getIntOr("DataVersion", 0);
        if (!(schematic.get("DataVersion") instanceof IntTag) || version <= 0) {
            throw new IOException("The schematic needs a valid Minecraft DataVersion.");
        }
        if (schematic.contains("Biomes") || !readList(schematic, "Entities").isEmpty()) {
            throw new IOException("This schematic contains biomes or entities. Re-export it with blocks only.");
        }
        if (schematic.contains("Offset") && schematic.getIntArray("Offset").filter(offset -> offset.length == 3).isEmpty()) {
            throw new IOException("The schematic offset must contain three integers.");
        }
        var size = new BlockPos(length(schematic, "Width"), length(schematic, "Height"), length(schematic, "Length"));
        if(size.getX()<1||size.getY()<1||size.getZ()<1||size.getX()>side||size.getY()>side||size.getZ()>side)throw new IOException("Schematic bounds exceed "+side+" blocks per side.");
        int plane = size.getX() * size.getZ();
        int volume = plane * size.getY();
        CompoundTag blocks = compound(schematic, "Blocks");
        CompoundTag spongePalette = compound(blocks, "Palette");
        int paletteLimit=side==64?IslandBuild.MAX_CELLS:MAX_PALETTE;
        if (spongePalette.isEmpty() || spongePalette.size() > paletteLimit) {
            throw new IOException("The schematic palette must contain between 1 and " + paletteLimit + " states.");
        }
        Map<Integer, Integer> indices = new HashMap<>();
        ListTag palette = new ListTag();
        for (String state : spongePalette.keySet().stream().sorted().toList()) {
            int index = spongePalette.getIntOr(state, -1);
            if (!(spongePalette.get(state) instanceof IntTag) || index < 0 || indices.putIfAbsent(index, palette.size()) != null) {
                throw new IOException("The schematic palette contains an invalid or repeated index for " + state);
            }
            palette.add(stateTag(state));
        }
        byte[] data = blocks.getByteArray("Data").orElseThrow(() -> new IOException("The schematic has no block index array."));
        if (data.length < volume || data.length > volume * 5) {
            throw new IOException("The schematic block data length does not match its bounds.");
        }
        Map<BlockPos, CompoundTag> blockEntities = blockEntities(blocks, size, volume);
        ByteArrayInputStream bytes = new ByteArrayInputStream(data);
        ListTag cells = new ListTag();
        for (int index = 0; index < volume; index++) {
            var pos = new BlockPos(index % size.getX(), index / plane, index / size.getX() % size.getZ());
            int id = readIndex(bytes);
            Integer state = indices.get(id);
            if (state == null) {
                throw new IOException("The schematic references missing palette index " + id + " at " + pos.toShortString());
            }
            CompoundTag cell = new CompoundTag();
            cell.put("pos", NativeTemplate.writePosition(pos));
            cell.putInt("state", state);
            CompoundTag entity = blockEntities.get(pos);
            if (entity != null) {
                cell.put("nbt", entity);
            }
            cells.add(cell);
        }
        if (bytes.available() != 0) {
            throw new IOException("The schematic contains more block indices than its bounds allow.");
        }
        var result = new CompoundTag();
        result.putInt("DataVersion", version);
        result.put("size", NativeTemplate.writePosition(size));
        result.put("palette", palette);
        result.put("blocks", cells);
        result.put("entities", new ListTag());
        if (schematic.contains("Metadata")) {
            CompoundTag metadata = compound(schematic, "Metadata");
            metadata.getString("Name").filter(name -> !name.isBlank()).ifPresent(name -> result.putString("title", name));
            metadata.getString("Author").ifPresent(author -> result.putString("author", author));
        }
        return result;
    }

    private static CompoundTag stateTag(String state) throws IOException {
        int opening = state.indexOf('[');
        String name = opening < 0 ? state : state.substring(0, opening);
        Identifier id = Identifier.tryParse(name);
        if (id == null || name.isEmpty()) {
            throw new IOException("Invalid schematic block ID " + name);
        }
        var entry = new CompoundTag();
        entry.putString("Name", id.toString());
        if (opening >= 0) {
            if (!state.endsWith("]")) {
                throw new IOException("Invalid schematic block state " + state);
            }
            var properties = new CompoundTag();
            for (String pair : state.substring(opening + 1, state.length() - 1).split(",", -1)) {
                int equals = pair.indexOf('=');
                if (equals <= 0 || equals == pair.length() - 1 || properties.contains(pair.substring(0, equals))) {
                    throw new IOException("Invalid or repeated property in schematic block state " + state);
                }
                properties.putString(pair.substring(0, equals), pair.substring(equals + 1));
            }
            entry.put("Properties", properties);
        }
        return entry;
    }

    private static Map<BlockPos, CompoundTag> blockEntities(CompoundTag blocks, BlockPos size, int volume) throws IOException {
        var entries = readList(blocks, "BlockEntities");
        if (entries.size() > volume) {
            throw new IOException("The schematic contains more block entities than cells.");
        }
        var entities = new HashMap<BlockPos, CompoundTag>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i).orElseThrow(() -> new IOException("Malformed schematic block entity."));
            int[] coords = entry.getIntArray("Pos").filter(pos -> pos.length == 3)
                    .orElseThrow(() -> new IOException("A schematic block entity needs three local coordinates."));
            var pos = new BlockPos(coords[0], coords[1], coords[2]);
            String name = entry.getStringOr("Id", "");
            Identifier id = Identifier.tryParse(name);
            if (!NativeTemplate.inside(size, pos) || id == null || name.isEmpty() || entities.containsKey(pos)) {
                throw new IOException("Invalid or duplicate schematic block entity at " + pos.toShortString());
            }
            CompoundTag data = entry.contains("Data") ? compound(entry, "Data").copy() : new CompoundTag();
            data.putString("id", id.toString());
            entities.put(pos, data);
        }
        return entities;
    }

    private static int readIndex(ByteArrayInputStream data) throws IOException {
        int index = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int value = data.read();
            if (value < 0 || (shift == 28 && value > 7)) {
                break;
            }
            index |= (value & 0x7f) << shift;
            if ((value & 0x80) == 0) {
                return index;
            }
        }
        throw new IOException("The schematic has a truncated or out-of-range block palette index.");
    }

    private static int length(CompoundTag schematic, String field) throws IOException {
        if (schematic.get(field) instanceof ShortTag size) {
            return Short.toUnsignedInt(size.value());
        }
        throw new IOException("The schematic needs a short " + field + " field.");
    }

    private static CompoundTag compound(CompoundTag parent, String name) throws IOException {
        return parent.getCompound(name).orElseThrow(() -> new IOException("Missing or malformed schematic " + name + " data."));
    }

    private static ListTag readList(CompoundTag parent, String name) throws IOException {
        if (!parent.contains(name)) {
            return new ListTag();
        }
        return parent.getList(name).orElseThrow(() -> new IOException("Malformed schematic " + name + " list."));
    }
}

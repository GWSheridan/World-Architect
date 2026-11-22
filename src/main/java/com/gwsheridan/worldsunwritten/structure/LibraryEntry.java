package com.gwsheridan.worldsunwritten.structure;

import com.gwsheridan.worldsunwritten.config.IslandTemplate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

record LibraryEntry(NativeTemplate template, String title, String category, String author, int revision,
        TemplateRules rules, Map<String, Marker> markers, String metadataHeader) {
    LibraryEntry(NativeTemplate template, String title, String category, String author, int revision,
            TemplateRules rules, Map<String, Marker> markers) {
        this(template, title, category, author, revision, rules, markers,
                com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyMetadata.writing());
    }
    static final Codec<LibraryEntry> CODEC = CompoundTag.CODEC.comapFlatMap(root -> {
        try {
            NativeTemplate template = NativeTemplate.read(root);
            for (var cell : template.blocks()) {
                if (TemplateMetadata.restricted(cell.state())) {
                    throw new IOException("Unsupported generation template block " + cell.state());
                }
            }
            return DataResult.success(read(template, root, "Generated structure", ""));
        } catch (IOException | IllegalArgumentException e) {
            return DataResult.error(() -> e.getMessage());
        }
    }, LibraryEntry::toTag);
    private static final int MAX_MARKERS = 16;

    LibraryEntry {
        markers = Map.copyOf(markers);
    }

    static LibraryEntry read(NativeTemplate template, CompoundTag root, String fallbackTitle, String fallbackAuthor) throws IOException {
        String header = com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyMetadata.header(root);
        var meta = root.getCompound(header).orElse(null);
        if (meta == null) {
            if (root.contains(header)) {
                throw new IOException("Malformed Worlds Unwritten template metadata.");
            }
            return new LibraryEntry(template, fallbackTitle, "Custom Structures", root.getStringOr("author", fallbackAuthor),
                    1, TemplateRules.DEFAULT, Map.of()).validated();
        }
        if (meta.getIntOr("format", 0) != 1) {
            throw new IOException("Unsupported Worlds Unwritten template format.");
        }
        var spawnMarkers = new TreeMap<String, Marker>();
        var savedMarkers = meta.getCompoundOrEmpty("markers");
        if (savedMarkers.size() > MAX_MARKERS) {
            throw new IOException("A template can have at most " + MAX_MARKERS + " spawn markers.");
        }
        for (String name : savedMarkers.keySet()) {
            var tag = savedMarkers.getCompound(name).orElseThrow(() -> new IOException("Invalid spawn marker " + name));
            BlockPos pos = NativeTemplate.readPosition(tag.getListOrEmpty("pos"));
            Direction facing = Direction.byName(tag.getStringOr("facing", ""));
            if (facing == null) {
                throw new IOException("Invalid spawn facing for " + name);
            }
            spawnMarkers.put(name, new Marker(pos, facing));
        }
        var rules = TemplateRules.CODEC.parse(NbtOps.INSTANCE, meta.getCompoundOrEmpty("rules")).getOrThrow(IOException::new);
        return new LibraryEntry(template, meta.getStringOr("title", fallbackTitle), meta.getStringOr("category", "Custom Structures"),
                meta.getStringOr("author", fallbackAuthor), meta.getIntOr("revision", 0), rules, spawnMarkers, header).validated();
    }

    CompoundTag toTag() {
        CompoundTag root = template.toTag();
        CompoundTag meta = new CompoundTag();
        meta.putInt("format", 1);
        meta.putInt("revision", revision);
        meta.putString("title", title);
        meta.putString("category", category);
        meta.putString("author", author);
        meta.put("rules", TemplateRules.CODEC.encodeStart(NbtOps.INSTANCE, rules).getOrThrow());
        CompoundTag savedMarkers = new CompoundTag();
        new TreeMap<>(markers).forEach((name, marker) -> {
            CompoundTag tag = new CompoundTag();
            tag.put("pos", NativeTemplate.writePosition(marker.pos));
            tag.putString("facing", marker.facing.getSerializedName());
            savedMarkers.put(name, tag);
        });
        meta.put("markers", savedMarkers);
        root.put(metadataHeader, meta);
        return root;
    }

    LibraryEntry withMarker(String name, Marker marker) throws IOException {
        var next = new TreeMap<>(markers);
        next.put(name, marker);
        return new LibraryEntry(template, title, category, author, Math.incrementExact(revision), rules, next, metadataHeader).validated();
    }

    LibraryEntry withoutMarker(String name) throws IOException {
        var next = new TreeMap<>(markers);
        if (next.remove(name) == null) {
            throw new IOException("Unknown spawn marker " + name);
        }
        return new LibraryEntry(template, title, category, author, Math.incrementExact(revision), rules, next, metadataHeader);
    }

    LibraryEntry withDetails(String title, String category) throws IOException {
        return new LibraryEntry(template, title, category, author, Math.incrementExact(revision), rules, markers, metadataHeader).validated();
    }

    LibraryEntry withRules(TemplateRules rules) {
        return new LibraryEntry(template, title, category, author, Math.incrementExact(revision), rules, markers, metadataHeader);
    }

    LibraryEntry duplicate() {
        return new LibraryEntry(template, title, category, author, 1, rules, markers, metadataHeader);
    }

    LibraryEntry transformed(TemplatePlacement transform) throws IOException {
        BlockPos size = template.size();
        var blocks = template.blocks().stream().map(cell -> new NativeTemplate.Cell(
                transform.position(size, cell.pos()), transform.state(cell.state()), cell.metadata())).toList();
        var transformedMarkers = new TreeMap<String, Marker>();
        markers.forEach((name, marker) -> transformedMarkers.put(name,
                new Marker(transform.position(size, marker.pos()), transform.facing(marker.facing()))));
        return withTemplate(new NativeTemplate(transform.size(size), blocks), transformedMarkers);
    }

    LibraryEntry replaceState(BlockState original, BlockState replacement) throws IOException {
        if (original == replacement || TemplateMetadata.restricted(replacement)) {
            throw new IOException("Choose a different, supported replacement block state.");
        }
        var blocks = new ArrayList<NativeTemplate.Cell>(template.blocks().size());
        boolean found = false;
        for (var cell : template.blocks()) {
            if (cell.state() != original) {
                blocks.add(cell);
                continue;
            }
            found = true;
            var metadata = cell.metadata();
            if (metadata != null && original.getBlock() != replacement.getBlock()) {
                throw new IOException("A block with saved metadata can only change properties of the same block type.");
            }
            var edited = new NativeTemplate.Cell(cell.pos(), replacement, metadata);
            if (edited.isSolidContent()) {
                blocks.add(edited);
            }
        }
        if (!found) {
            throw new IOException("The selected block state is absent from this template. Use an exact state from its palette.");
        }
        return withTemplate(new NativeTemplate(template.size(), blocks), markers);
    }

    private LibraryEntry withTemplate(NativeTemplate replacement, Map<String, Marker> replacementMarkers) throws IOException {
        return new LibraryEntry(replacement, title, category, author, Math.incrementExact(revision), rules, replacementMarkers, metadataHeader).validated();
    }

    private LibraryEntry validated() throws IOException {
        if (template.blocks().isEmpty() || title.isBlank() || category.isBlank() || title.length() > 80 || category.length() > 40
                || author.length() > 64 || revision < 1 || markers.size() > MAX_MARKERS) {
            throw new IOException("Invalid or empty library entry. Title, category and author limits are 80, 40 and 64 characters.");
        }
        Map<BlockPos, BlockState> cells = new HashMap<>();
        if (!markers.isEmpty()) {
            template.blocks().forEach(cell -> cells.put(cell.pos(), cell.state()));
        }
        for (var entry : markers.entrySet()) {
            Marker marker = entry.getValue();
            BlockPos pos = marker.pos;
            BlockState floor = cells.get(pos.below());
            if (!entry.getKey().matches("[a-z0-9_-]{1,32}") || !marker.facing.getAxis().isHorizontal()
                    || !NativeTemplate.inside(template.size(), pos.above()) || floor == null
                    || cells.containsKey(pos) || cells.containsKey(pos.above())
                    || !IslandTemplate.safeFloor(floor, EmptyBlockGetter.INSTANCE, pos.below())) {
                throw new IOException("Spawn marker " + entry.getKey() + " needs two air cells above a safe full block inside the template.");
            }
        }
        return this;
    }

    record Marker(BlockPos pos, Direction facing) {
        Marker {
            pos = pos.immutable();
        }
    }

}

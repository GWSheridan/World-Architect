package com.gwsheridan.worldarchitect.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

final class BlockPickerHistory {
    private static final int MAX_FAVOURITES = 128;
    private static final int MAX_RECENTS = 24;
    private static final BlockPickerHistory INSTANCE = new BlockPickerHistory(FMLPaths.CONFIGDIR.get().resolve("worldarchitect/block-picker.json"));
    private final Path file;
    private final Set<Identifier> favourites = new LinkedHashSet<>();
    private final ArrayDeque<Identifier> recents = new ArrayDeque<>();

    private BlockPickerHistory(Path file) {
        this.file = file;
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 65_536) {
                try (var input = Files.newBufferedReader(file)) {
                    var root = JsonParser.parseReader(input).getAsJsonObject();
                    favourites.addAll(read(root, "favourites", MAX_FAVOURITES));
                    recents.addAll(read(root, "recent", MAX_RECENTS));
                }
            }
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("Could not read World Architect block picker history: {}", error.getMessage());
        }
    }

    static BlockPickerHistory get() {
        return INSTANCE;
    }

    boolean favourite(Identifier id) {
        return favourites.contains(id);
    }

    boolean canFavourite(Identifier id) {
        return favourite(id) || favourites.size() < MAX_FAVOURITES;
    }

    void toggleFavourite(Identifier id) {
        if (canFavourite(id)) {
            if (!favourites.remove(id)) {
                favourites.add(id);
            }
            save();
        }
    }

    List<Identifier> recents() {
        return List.copyOf(recents);
    }

    void use(Identifier id) {
        recents.remove(id);
        recents.addFirst(id);
        if (recents.size() > MAX_RECENTS) {
            recents.removeLast();
        }
        save();
    }

    private static List<Identifier> read(JsonObject object, String key, int limit) {
        if (!(object.get(key) instanceof JsonArray array)) {
            return List.of();
        }
        return array.asList().stream().filter(value -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
                .map(value -> Identifier.tryParse(value.getAsString())).filter(id -> id != null).distinct().limit(limit).toList();
    }

    private void save() {
        var root = new JsonObject();
        root.add("favourites", json(favourites));
        root.add("recent", json(recents));
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "block-picker-", ".json");
            try {
                Files.writeString(temporary, root.toString());
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            LogUtils.getLogger().warn("Could not save World Architect block picker history: {}", error.getMessage());
        }
    }

    private static JsonArray json(Iterable<Identifier> identifiers) {
        var array = new JsonArray();
        identifiers.forEach(id -> array.add(id.toString()));
        return array;
    }
}

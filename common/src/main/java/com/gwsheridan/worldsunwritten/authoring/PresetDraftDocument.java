package com.gwsheridan.worldsunwritten.authoring;

import com.google.gson.*;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.mojang.serialization.JsonOps;
import java.util.*;

/** Portable authoring document; validation still belongs to Preset Studio's registry overlay. */
public final class PresetDraftDocument {
    private PresetDraftDocument() {}
    public static JsonObject encode(String name, PresetStudio.Draft draft) {
        var json = new JsonObject(); json.addProperty("name", name);
        json.add("manifest", BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE, draft.manifest()).getOrThrow());
        json.add("preset", draft.preset()); var resources = new JsonObject();
        draft.templates().forEach((key, bytes) -> resources.addProperty(key, Base64.getEncoder().encodeToString(bytes)));
        json.add("resources", resources); return json;
    }
    public static PresetStudio.Draft decode(JsonElement document) {
        var json = document.getAsJsonObject(); String name = name(document);
        var resources = new TreeMap<String, byte[]>(); var source = json.getAsJsonObject("resources");
        if (source.size() > 128) throw new IllegalArgumentException("Too many embedded recovery resources.");
        long total = 0;
        for (var entry : source.entrySet()) {
            byte[] bytes = Base64.getDecoder().decode(entry.getValue().getAsString());
            total += bytes.length;
            if (bytes.length > 8 * 1024 * 1024 || total > 32 * 1024 * 1024) throw new IllegalArgumentException("Recovery resources exceed preset budgets.");
            resources.put(entry.getKey(), bytes);
        }
        return new PresetStudio.Draft(BundleManifest.CODEC.parse(JsonOps.INSTANCE, json.get("manifest")).getOrThrow(), json.getAsJsonObject("preset"), resources);
    }
    public static String name(JsonElement document) {
        String value = document.getAsJsonObject().get("name").getAsString();
        if (!value.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid recovery library name.");
        return value;
    }
}

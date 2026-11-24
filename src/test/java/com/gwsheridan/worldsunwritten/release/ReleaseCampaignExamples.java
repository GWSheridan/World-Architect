package com.gwsheridan.worldsunwritten.release;

import com.google.gson.*;

import java.nio.file.*;
import java.util.*;

/** Packages the two campaign starters from exports validated by a dedicated server. */
public final class ReleaseCampaignExamples {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), exports = Path.of(args[1]);
        String version = args[2];
        for (String slug : List.of("hex_frontier", "one_chunk_to_the_end")) {
            boolean hex = slug.equals("hex_frontier");
            String title = hex ? "Hex Frontier" : "One Chunk to the End",
                    name = slug.replace('_', '-');
            var files = ReleaseExamples.read(exports.resolve(slug + "-" + version + ".zip"));
            var manifest = ReleaseExamples.json(files.get("worldsunwritten.bundle.json"));
            String previous = manifest.get("preset").getAsString(),
                    id = "worldsunwritten_examples:" + slug;
            byte[] preset = files.remove(path(previous));
            if (preset == null) throw new IllegalStateException("Missing verified campaign preset");
            manifest.addProperty("format", 3);
            manifest.addProperty("preset", id);
            manifest.addProperty("title", title);
            manifest.addProperty("author", "GWSHERIDAN");
            manifest.addProperty(
                    "description",
                    hex
                            ? "Resource islands, reserved paths and procedural ruins, with a shared"
                                    + " native End campaign."
                            : "One starting chunk, advancement and timber objectives, an End portal"
                                    + " room and shared native End progression.");
            manifest.addProperty("release", version);
            manifest.addProperty("lock", hex ? "full" : "partial");
            manifest.addProperty("allow_structure_library", false);
            for (String field :
                    List.of(
                            "editable_dimensions",
                            "editable_features",
                            "editable_options",
                            "inheritance")) manifest.remove(field);
            if (!hex) {
                manifest.add("editable_dimensions", new JsonArray());
                manifest.add("editable_features", new JsonObject());
                var options = new JsonArray();
                options.add("seed");
                manifest.add("editable_options", options);
            }
            var pack = new JsonObject();
            pack.addProperty("version", 1);
            var defaults = new JsonObject();
            defaults.addProperty("version", 1);
            var protection = new JsonObject();
            for (String field : List.of("breaking", "placement", "containers", "shared"))
                protection.addProperty(field, true);
            defaults.add("protection", protection);
            pack.add("defaults", defaults);
            var editable = new JsonArray();
            if (!hex) editable.add("protection");
            pack.add("editable", editable);
            manifest.add("pack", pack);
            files.put(path(id), preset);
            files.put("worldsunwritten.bundle.json", ReleaseExamples.bytes(manifest));
            var metadata = ReleaseExamples.json(files.get("pack.mcmeta"));
            metadata.getAsJsonObject("pack")
                    .addProperty("description", title + " for Worlds Unwritten " + version);
            files.put("pack.mcmeta", ReleaseExamples.bytes(metadata));
            var directory = root.resolve("examples/campaigns");
            ReleaseExamples.save(
                    files,
                    directory.resolve("bundles/" + name + "-" + version + ".zip"),
                    directory.resolve(name + "-" + version + "-source"));
        }
        System.out.println(
                "Prepared Hex Frontier and One Chunk to the End with explicit full and partial"
                        + " locks.");
    }

    private static String path(String id) {
        var parts = id.split(":", 2);
        return "data/" + parts[0] + "/worldgen/world_preset/" + parts[1] + ".json";
    }
}

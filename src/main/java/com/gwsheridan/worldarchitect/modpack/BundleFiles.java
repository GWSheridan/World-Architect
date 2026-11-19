package com.gwsheridan.worldarchitect.modpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.resources.Identifier;

final class BundleFiles {
    static final String MANIFEST = "worldarchitect.bundle.json";
    static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024;
    static final int MAX_JSON_BYTES = 8 * 1024 * 1024;
    static final int MAX_TEMPLATE_BYTES = 4 * 1024 * 1024;
    static final int MAX_TEMPLATES = 128;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private BundleFiles() {}

    static boolean safeId(Identifier id) {
        return id.getNamespace().matches("[a-z0-9_-]{1,64}") && id.getPath().length() <= 120
                && id.getPath().matches("[a-z0-9_-]+(/[a-z0-9_-]+)*");
    }

    static boolean filename(String name) {
        return name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}");
    }

    static boolean archiveFilename(String name) {
        return name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}\\.zip");
    }

    static String presetPath(Identifier id) {
        return "data/" + id.getNamespace() + "/worldgen/world_preset/" + id.getPath() + ".json";
    }

    static String templatePath(Identifier id) {
        return "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
    }

    static boolean templatePath(String path) {
        return path.matches("data/[a-z0-9_-]{1,64}/structure/[a-z0-9_-]+(/[a-z0-9_-]+)*\\.nbt") && path.length() <= 205;
    }

    static boolean modularPath(String path) {
        return path.matches("data/[a-z0-9_-]{1,64}/worldgen/structure/[a-z0-9_-]+(/[a-z0-9_-]+)*\\.json") && path.length() <= 220;
    }

    static String modularPath(Identifier id) {
        return "data/" + id.getNamespace() + "/worldgen/structure/" + id.getPath() + ".json";
    }

    static JsonObject readJson(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return readJson(input, path.getFileName().toString());
        }
    }

    static JsonObject readJson(InputStream input, String label) throws IOException {
        var bytes = readBytes(input, MAX_JSON_BYTES, label);
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON object in " + label, e);
        }
    }

    static byte[] jsonBytes(JsonElement value) {
        return (GSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] readBytes(InputStream input, int max, String label) throws IOException {
        byte[] bytes = input.readNBytes(max + 1);
        if (bytes.length > max) {
            throw new IOException(label + " exceeds its uncompressed size limit.");
        }
        return bytes;
    }

    static String fingerprint(Path path) throws IOException {
        if (Files.size(path) > MAX_ARCHIVE_BYTES) {
            throw new IOException("A preset archive may be at most 32 MiB.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

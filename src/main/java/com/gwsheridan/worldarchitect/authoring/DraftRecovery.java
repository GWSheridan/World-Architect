package com.gwsheridan.worldarchitect.authoring;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/** Recovery documents are authoring data, never generation snapshots or installed resources. */
public final class DraftRecovery {
    public static final int MAX_BYTES = 8 * 1024 * 1024, MAX_FILES = 32;
    private DraftRecovery() {}
    public static String fingerprint(JsonElement value) { return fingerprint(value.toString()); }
    public static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static Path file(Path directory, String key) throws IOException {
        var root = directory.toAbsolutePath().normalize();
        for (Path p = root; p != null; p = p.getParent()) if (Files.isSymbolicLink(p)) throw new IOException("Recovery paths cannot contain symbolic links.");
        var path = root.resolve(fingerprint(key) + ".json");
        if (Files.isSymbolicLink(path)) throw new IOException("Recovery files cannot be symbolic links.");
        return path;
    }
    public static Optional<JsonElement> read(Path directory, String key, String base) throws IOException {
        Path path = file(directory, key);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) throw new IOException("Recovery document exceeds its bounds.");
        try (var reader = Files.newBufferedReader(path)) {
            var envelope = JsonParser.parseReader(reader).getAsJsonObject();
            if (envelope.get("format").getAsInt() != 1 || !envelope.get("key").getAsString().equals(key)) throw new IOException("Unknown recovery document.");
            if (!envelope.get("base").getAsString().equals(base)) throw new IOException("The recovery draft has a different base revision. Discard it or restore its original authoring context.");
            return Optional.of(envelope.get("document").deepCopy());
        } catch (RuntimeException e) { throw new IOException("Cannot read the recovery draft.", e); }
    }
    public static void write(Path directory, String key, String base, JsonElement document) throws IOException {
        if (key.length() > 256 || !base.matches("[a-f0-9]{64}")) throw new IOException("Invalid recovery identity.");
        Path path = file(directory, key);
        var envelope = new JsonObject(); envelope.addProperty("format", 1); envelope.addProperty("key", key); envelope.addProperty("base", base); envelope.add("document", document);
        byte[] data = envelope.toString().getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) throw new IOException("Recovery draft exceeds 8 MiB.");
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) try (var files = Files.list(path.getParent())) {
            if (files.filter(p -> p.getFileName().toString().endsWith(".json")).limit(MAX_FILES).count() >= MAX_FILES) throw new IOException("Recovery storage is full. Discard an older recovery draft first.");
        }
        Path temp = Files.createTempFile(path.getParent(), ".recovery-", ".tmp");
        try { Files.write(temp, data); Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    public static void discard(Path directory, String key) throws IOException { Files.deleteIfExists(file(directory, key)); }
}

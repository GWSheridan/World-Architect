package com.gwsheridan.worldarchitect.inspection;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Reads only saved files. Does not open a level, resolve spawns, register resources or repair files. */
public final class SavedWorldInspection {
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    public record Report(List<String> rows, Optional<CompoundTag> dimensions, boolean healthy) {
        public Report { rows = rows.stream().limit(512).map(row->row.length()>500?row.substring(0,500):row).toList(); dimensions = dimensions.map(CompoundTag::copy); }
        @Override public Optional<CompoundTag> dimensions() { return dimensions.map(CompoundTag::copy); }
    }
    private SavedWorldInspection() {}
    public static Report read(Path data) {
        var rows = new ArrayList<String>(); CompoundTag root;
        rows.add("Saved generation is immutable. Inspector actions do not create chunks or change this world.");
        try {
            root = readFile(data.resolve("worldarchitect-generation-v1.dat"));
        } catch (IOException | RuntimeException e) {
            rows.add("Snapshot unavailable or unreadable. Restore the original file from a complete backup.");
            return new Report(rows, Optional.empty(), false);
        }
        var schemaTag = root.get("snapshot_schema"); int schema = schemaTag == null ? 1 : schemaTag instanceof IntTag i ? i.intValue() : -1;
        boolean healthy = schema == 1 || schema == 2; rows.add("Snapshot schema: " + schema + (healthy ? " · supported" : " · unsupported; loading must remain blocked"));
        var settings = root.getCompoundOrEmpty("data"); var dimensions = settings.getCompound("dimensions");
        if (dimensions.isEmpty() || dimensions.orElseThrow().isEmpty()) { rows.add("Missing saved dimension definitions."); healthy = false; }
        try {
            if (!settings.equals(readFile(data.resolve("minecraft/world_gen_settings.dat")).getCompoundOrEmpty("data"))) {
                rows.add("Saved settings differ from the original snapshot. Restore matching files; do not overwrite the snapshot."); healthy = false;
            } else rows.add("Saved settings match the original snapshot.");
        } catch (IOException | RuntimeException e) { rows.add("Saved world settings are missing or unreadable."); healthy = false; }
        if (dimensions.isPresent()) for (String id : dimensions.orElseThrow().keySet().stream().sorted().limit(64).toList()) {
            var generator = dimensions.orElseThrow().getCompoundOrEmpty(id).getCompoundOrEmpty("generator");
            var profile = generator.getCompoundOrEmpty("profile"); var dimension = generator.getCompoundOrEmpty("dimension_profile");
            rows.add(id + " · " + generator.getStringOr("type", "native") + " · generator " + profile.getIntOr("generator_version", 0));
            dimension.getCompound("adapter").ifPresent(a -> rows.add("  Required dimension adapter: " + a.getStringOr("id", "unknown") + " / " + a.getIntOr("version", 0)));
            rows.add("  Saved rule count: " + dimension.getCompoundOrEmpty("rule_engine").getListOrEmpty("rules").size() + " · native End arena: " + dimension.contains("end_progression"));
        }
        var pins = root.getCompoundOrEmpty("generation_dependencies");
        for (String name : List.of("managed_biomes", "native_extensions", "native_resources", "dimension_types", "rule_mods")) {
            var section = pins.getCompoundOrEmpty(name); rows.add("Pinned " + name.replace('_', ' ') + ": " + section.size());
            section.keySet().stream().sorted().limit(24).forEach(id -> rows.add("  " + name + "/" + id));
        }
        var archive = root.getCompound("bundle_archive");
        if (root.contains("bundle_archive") && archive.isEmpty()) { rows.add("Malformed archived preset pin. Restore the original snapshot."); healthy = false; }
        if (archive.isPresent()) {
            var a = archive.orElseThrow(); String digest = a.getStringOr("sha256", "");
            rows.add("Archived preset: " + a.getStringOr("preset", "unknown") + " / " + a.getStringOr("release", "unknown"));
            try {
                if (a.getIntOr("format", 0) != 1 || !digest.matches("[a-f0-9]{64}")) throw new IOException();
                var file = data.resolve("worldarchitect/generation/" + digest + ".zip");
                if (!regular(file) || Files.size(file) > MAX_BYTES || !sha256(file).equals(digest)) throw new IOException();
                rows.add("Archived preset fingerprint: verified.");
            } catch (IOException e) { rows.add("Archived preset missing, oversized or changed. Restore its original ZIP from backup."); healthy = false; }
        } else rows.add("No preset archive is recorded by this snapshot.");
        rows.add("File consistency is not certification of a different modpack. Validate its providers and registries on a copy before upgrading.");
        rows.add("Spawn/protection overrides are separate administration data. Use /wa_admin for permitted runtime changes.");
        rows.add("Displayed dependency lists are limited to 24 IDs per section; saved dimensions to 64. No player identities or template metadata are exported.");
        return new Report(rows, dimensions, healthy);
    }
    private static boolean regular(Path path) { return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS); }
    private static CompoundTag readFile(Path file) throws IOException {
        if (!regular(file) || Files.size(file) > MAX_BYTES) throw new IOException();
        return NbtIo.readCompressed(file, NbtAccounter.create(MAX_BYTES));
    }
    private static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) { byte[] buffer = new byte[65_536]; for (int n; (n = in.read(buffer)) >= 0;) digest.update(buffer, 0, n); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

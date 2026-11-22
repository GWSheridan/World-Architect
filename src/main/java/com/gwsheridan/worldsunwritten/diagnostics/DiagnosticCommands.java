package com.gwsheridan.worldsunwritten.diagnostics;

import com.google.gson.*;
import com.gwsheridan.worldsunwritten.api.*;
import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Allowlisted local diagnostics. Never serializes world settings, connection data or exception messages. */
public final class DiagnosticCommands {
    private DiagnosticCommands() {}
    public static void register(RegisterCommandsEvent event) {
        com.gwsheridan.worldsunwritten.commands.ModCommands.register(event,Commands.literal("diagnose").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("report").executes(context -> {
                    try {
                        Path directory = com.gwsheridan.worldsunwritten.modpack.ModpackRuntime.directory().resolve("diagnostics"); Files.createDirectories(directory);
                        String name = "report-" + UUID.randomUUID() + ".json";
                        Files.writeString(directory.resolve(name), new GsonBuilder().setPrettyPrinting().create().toJson(report(context.getSource().getServer())) + "\n", StandardOpenOption.CREATE_NEW);
                        context.getSource().sendSuccess(() -> Component.literal("Saved local diagnostics to config/worldsunwritten/diagnostics/" + name), false); return 1;
                    } catch (IOException | RuntimeException e) {
                        context.getSource().sendFailure(Component.literal("Cannot write the local diagnostic report. Check directory permissions.")); return 0;
                    }
                })));
    }
    static JsonObject report(MinecraftServer server) {
        JsonObject root = new JsonObject(); root.addProperty("format", 1); root.addProperty("api", WorldsUnwrittenApi.VERSION);
        root.addProperty("java", System.getProperty("java.version")); root.addProperty("os", System.getProperty("os.name"));
        var mods = new JsonObject(); ModList.get().getMods().stream().sorted(Comparator.comparing(m -> m.getModId()))
                .forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString())); root.add("mods", mods);
        var dimensions = new JsonObject();
        for (var level : server.getAllLevels()) {
            var data = new JsonObject(); var id = level.dimension().identifier(); var generator = level.getChunkSource().getGenerator();
            data.addProperty("managed", generator instanceof ManagedGenerator);
            DimensionAdapterRegistry.get(id).ifPresent(adapter -> { data.addProperty("dimension_adapter_version", adapter.version()); data.addProperty("portal_policies", adapter.supportsPortalPolicies()); });
            if (generator instanceof ManagedGenerator managed) {
                data.addProperty("terrain", managed.terrainMode().name()); data.addProperty("native_end_arena", managed.endProgression().isPresent());
                managed.dimensionProfile().flatMap(p -> p.adapter()).ifPresent(binding -> {
                    data.addProperty("required_adapter_version", binding.version());
                    data.addProperty("adapter_match", DimensionAdapterRegistry.get(binding.id()).filter(a -> a.version() == binding.version()).isPresent());
                });
            }
            if (generator instanceof SolidColumnGenerator solid) {
                var adapters = new JsonArray();
                solid.content().nativeFeatures().forEach(rule -> {
                    var adapter = new JsonObject(); adapter.addProperty("feature", FeatureAdapters.type(rule.feature().value()).toString());
                    FeatureAdapters.get(rule.feature().value()).ifPresentOrElse(a -> { adapter.addProperty("version", a.version()); adapter.addProperty("work_limit", a.workLimit(rule.feature().value())); }, () -> adapter.addProperty("missing", true)); adapters.add(adapter);
                });
                solid.structureRules().forEach(rule -> {
                    var adapter = new JsonObject(); adapter.addProperty("structure", StructureAdapters.type(rule.structure().value()).toString());
                    StructureAdapters.get(rule.structure().value()).ifPresentOrElse(a -> adapter.addProperty("version", a.version()), () -> adapter.addProperty("missing", true)); adapters.add(adapter);
                }); data.add("adapters", adapters);
            }
            dimensions.add(id.toString(), data);
        }
        root.add("dimensions", dimensions);
        var hashes = new JsonObject(); Path data = server.getWorldPath(LevelResource.DATA);
        for (String file : List.of(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.marker(data,"-generation-v1.dat"), "minecraft/world_gen_settings.dat")) {
            try { Path source = data.resolve(file); hashes.addProperty(file, Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) ? hash(source) : "absent"); }
            catch (IOException e) { hashes.addProperty(file, "unreadable"); }
        }
        root.add("snapshot_sha256", hashes);
        return root;
    }
    private static String hash(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int n; while ((n = input.read(buffer)) >= 0) digest.update(buffer, 0, n); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

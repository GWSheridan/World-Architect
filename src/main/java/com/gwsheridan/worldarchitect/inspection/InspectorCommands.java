package com.gwsheridan.worldarchitect.inspection;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import com.google.gson.*;
import com.gwsheridan.worldarchitect.modpack.*;
import com.gwsheridan.worldarchitect.studio.StudioViewPayload;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InspectorCommands {
    private InspectorCommands() {}
    public static void register(RegisterCommandsEvent event) {
        var root = literal("wa_inspect").requires(hasPermission(LEVEL_ADMINS)).executes(c -> show(c.getSource(), null, null, false));
        root.then(literal("installed").executes(c -> show(c.getSource(), "@installed", "", false)).then(literal("report").executes(c -> show(c.getSource(), "@installed", "", true))));
        root.then(literal("report").executes(c -> show(c.getSource(), null, null, true)));
        root.then(literal("compare").then(argument("name", word()).then(argument("release", word()).executes(c -> show(c.getSource(), getString(c,"name"), getString(c,"release"), false))
                .then(literal("report").executes(c -> show(c.getSource(), getString(c,"name"), getString(c,"release"), true))))));
        event.getDispatcher().register(root);
    }
    private static JsonObject fingerprints(Map<String,byte[]> resources) {
        var json=new JsonObject();try {var digest=java.security.MessageDigest.getInstance("SHA-256");new TreeMap<>(resources).forEach((path,bytes)->json.addProperty(path,java.util.HexFormat.of().formatHex(digest.digest(bytes))));return json;}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static int show(CommandSourceStack source, String name, String release, boolean export) {
        try {
            var server = source.getServer(); var report = SavedWorldInspection.read(server.getWorldPath(LevelResource.DATA));
            var rows = new ArrayList<>(report.rows());
            if (name != null) {
                var draft = name.equals("@installed") ? PresetStudio.installedDraft().orElseThrow(() -> new IOException("No installed defaults.")) : PresetStudio.load(ModpackRuntime.directory(), name, release);
                var checked = PresetStudio.validate(draft, server.registryAccess());
                rows.add("Comparison with " + name + " / " + release + ". Generation changes affect NEW worlds only.");
                if (!report.healthy() || report.dimensions().isEmpty()) rows.add("Cannot compare until the original saved files are readable and consistent.");
                else {
                    var tag=new CompoundTag();tag.put("dimensions",report.dimensions().orElseThrow());
                    var original=net.minecraft.world.level.levelgen.presets.WorldPreset.DIRECT_CODEC.parse(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE,server.registryAccess()),tag).getOrThrow(IOException::new);
                    var captured=PresetStudio.capture(draft.manifest(),original.createWorldDimensions(),server.registryAccess());
                    var left=new JsonObject();left.add("profiles",captured.preset().get("dimensions"));left.add("embedded_resources",fingerprints(captured.templates()));
                    var right=new JsonObject();right.add("profiles",checked.draft().preset().get("dimensions"));right.add("embedded_resources",fingerprints(checked.draft().templates()));
                    var diff = PresetComparison.compare(left,right);
                    if (diff.changed().isEmpty() && !diff.truncated()) rows.add("No dimension-generation differences.");
                    diff.changed().forEach(path -> rows.add("New-world generation: " + path));
                    if (diff.truncated()) rows.add("Comparison reached its work/output limit. Narrow the preset before comparing again.");
                    if (server.getWorldGenSettings().options().seed() != draft.manifest().options().seed()) rows.add("New-world default: seed differs.");
                    if (server.getWorldGenSettings().options().generateStructures() != draft.manifest().options().generateStructures()) rows.add("New-world default: structures option differs.");
                    if (server.getWorldGenSettings().options().generateBonusChest() != draft.manifest().options().generateBonusChest()) rows.add("New-world default: bonus chest differs.");
                }
            }
            for(var level:server.getAllLevels()) if(level.getChunkSource().getGenerator() instanceof com.gwsheridan.worldarchitect.worldgen.ManagedGenerator managed) managed.dimensionProfile().flatMap(profile->profile.adapter()).ifPresent(binding->{
                rows.add("Installed adapter: "+binding.id()+" · "+com.gwsheridan.worldarchitect.api.DimensionAdapterRegistry.get(binding.id()).map(a->"version "+a.version()).orElse("missing")+" · saved requirement "+binding.version());
            });
            rows.add("Runtime administration is not a preset migration. Missing dependencies must be restored, never bypassed.");
            var outputRows=boundedRows(rows);
            if (export) {
                var json = new JsonObject(); json.addProperty("format", 1); json.addProperty("healthy", report.healthy()); var lines = new JsonArray(); outputRows.forEach(lines::add); json.add("report", lines);
                var directory = ModpackRuntime.directory().resolve("diagnostics"); Files.createDirectories(directory); var output = directory.resolve("inspection-" + UUID.randomUUID() + ".json");
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardOpenOption.CREATE_NEW);
                source.sendSuccess(() -> Component.literal("Saved local inspection to config/worldarchitect/diagnostics/" + output.getFileName()), false);
            } else if (source.getPlayer() != null) {
                var data = new CompoundTag(); data.putString("kind", "inspection"); try {data.putBoolean("installed",ModpackRuntime.installed().isPresent());}catch(RuntimeException ignored){} data.putBoolean("healthy", report.healthy()); if(name!=null)data.putString("comparison",name.equals("@installed")?"@installed":name+" "+release); var lines = new ListTag();
                outputRows.forEach(line -> lines.add(StringTag.valueOf(line))); data.put("rows", lines);
                var presets = new ListTag(); try { PresetStudio.list(ModpackRuntime.directory()).forEach(p -> presets.add(StringTag.valueOf(p.name() + " / " + p.release()))); } catch(IOException e) { lines.add(StringTag.valueOf("The local preset library is unreadable; saved-world inspection remains available.")); } data.put("presets", presets);
                PacketDistributor.sendToPlayer(source.getPlayer(), new StudioViewPayload(data));
            } else outputRows.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
            return 1;
        } catch (IOException | RuntimeException e) { source.sendFailure(Component.literal("Inspector could not read the requested saved data or preset. No saved generation was changed.")); return 0; }
    }
    static List<String> boundedRows(List<String> rows){var result=new ArrayList<>(rows.stream().limit(512).map(s->s.length()>500?s.substring(0,500):s).toList());if(rows.size()>512)result.set(511,"Report truncated at 512 rows; additional differences or dependencies are omitted.");return List.copyOf(result);}
}

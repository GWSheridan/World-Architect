package com.gwsheridan.worldarchitect.worldgen;

import java.nio.file.Path;
import java.util.HashMap;
import net.minecraft.nbt.*;

/** Reads flushed region files without loading either mod into a game runtime. */
public final class ExtensionWorldAudit {
    public static void main(String[] args) throws Exception {
        Path world = Path.of(args[0]); int origin = Integer.parseInt(args[1]);
        var snapshot = NbtIo.readCompressed(world.resolve("data/worldarchitect-generation-v1.dat"), NbtAccounter.unlimitedHeap());
        var settings = NbtIo.readCompressed(world.resolve("data/minecraft/world_gen_settings.dat"), NbtAccounter.unlimitedHeap());
        require(snapshot.getCompoundOrEmpty("data").equals(settings.getCompoundOrEmpty("data")), "Snapshot/settings differ");
        var dependencies = snapshot.getCompoundOrEmpty("generation_dependencies");
        var extensions = dependencies.getCompoundOrEmpty("native_extensions").getCompoundOrEmpty("wa_api_fixture:lab");
        var feature = extensions.getCompoundOrEmpty("feature/wa_api_fixture:markers");
        require(feature.getIntOr("version", 0) == 1 && feature.getIntOr("work", 0) == 1 && feature.contains("definition"), "Feature contract not pinned");
        require(extensions.getCompoundOrEmpty("structure/wa_api_fixture:pyramid").getIntOr("version", 0) == 1, "Structure contract not pinned");
        require(dependencies.contains("native_resources"), "Native resources not pinned");
        var cache = new HashMap<String, CompoundTag>(); int markers = 0, pyramids = 0; long cells = 0;
        for (int cx = origin; cx < origin + 6; cx++) for (int cz = origin; cz < origin + 6; cz++) {
            var chunk = CrossDimensionWorldAudit.chunk(world, "wa_api_fixture:lab", cx * 16, cz * 16, cache);
            var sections = FeatureWorldAudit.sections(chunk, -64, 384, "block_states", 4);
            for (int xz = 0; xz < 256; xz++) {
                require(FeatureWorldAudit.block(sections, -64, -32, xz).equals("wa_api_fixture:slate"), "Modded base block lost");
                for (int y = 41; y < 80; y++) {
                    if (FeatureWorldAudit.block(sections, -64, y, xz).equals("wa_api_fixture:slate")) { require(y == 45, "Native feature escaped height origin"); markers++; }
                    cells++;
                }
            }
            var start = chunk.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").getCompoundOrEmpty("wa_api_fixture:pyramid");
            if (start.getStringOr("id", "").equals("wa_api_fixture:pyramid")) {
                require(!start.getListOrEmpty("Children").isEmpty(), "Adapted structure has no native pieces"); pyramids++;
            }
        }
        require(markers > 0 && markers <= 144 && pyramids > 0, "External feature/structure did not generate");
        var dimensions = settings.getCompoundOrEmpty("data").getCompoundOrEmpty("dimensions");
        require(dimensions.getCompoundOrEmpty("wa_api_fixture:untouched").getCompoundOrEmpty("generator").getStringOr("type", "").equals("minecraft:flat"), "Original dimension replaced");
        System.out.printf("Verified %,d extension positions, %d native markers, %d adapted structure starts, original dimension and adapter/resource pins.%n", cells, markers, pyramids);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

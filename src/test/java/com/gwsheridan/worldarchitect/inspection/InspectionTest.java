package com.gwsheridan.worldarchitect.inspection;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InspectionTest {
    @TempDir Path data;
    @Test void reportClippingAlwaysDisclosesOmittedRows(){var rows=new ArrayList<String>();for(int i=0;i<600;i++)rows.add("value "+i);var shown=InspectorCommands.boundedRows(rows);assertEquals(512,shown.size());assertTrue(shown.getLast().contains("truncated"));assertEquals(600,rows.size());assertEquals(List.of("small"),InspectorCommands.boundedRows(List.of("small")));}
    @Test void semanticPathsIgnoreObjectOrderButRetainArrayOrder() {
        var a=JsonParser.parseString("{\"world\":{\"seed\":1,\"layers\":[1,2]}}");
        var b=JsonParser.parseString("{\"world\":{\"layers\":[1,2],\"seed\":1}}");
        assertTrue(PresetComparison.compare(a,b).changed().isEmpty());
        b.getAsJsonObject().getAsJsonObject("world").getAsJsonArray("layers").set(0,new JsonPrimitive(3));
        assertEquals(List.of("dimensions/world/layers[0] · changed"),PresetComparison.compare(a,b).changed());
        assertFalse(PresetComparison.compare(a,b).truncated());
    }
    @Test void comparisonStopsAtItsWorkAndDepthLimits() {
        var a=new JsonArray();var b=new JsonArray();for(int i=0;i<1000;i++){a.add(i);b.add(-1);}
        var result=PresetComparison.compare(a,b);assertTrue(result.truncated());assertEquals(256,result.changed().size());
        var deep=new JsonObject();var cursor=deep;for(int i=0;i<100;i++){var next=new JsonObject();cursor.add("next",next);cursor=next;}
        assertTrue(PresetComparison.compare(deep,deep.deepCopy()).truncated());
    }
    private CompoundTag snapshot()throws Exception {
        var generator=new CompoundTag();generator.putString("type","worldarchitect:single_block");var profile=new CompoundTag();profile.putInt("generator_version",2);generator.put("profile",profile);
        var stem=new CompoundTag();stem.put("generator",generator);var dimensions=new CompoundTag();dimensions.put("minecraft:overworld",stem);
        var settings=new CompoundTag();settings.put("dimensions",dimensions);var root=new CompoundTag();root.putInt("snapshot_schema",2);root.put("data",settings);
        Files.createDirectories(data.resolve("minecraft"));NbtIo.writeCompressed(root,data.resolve("minecraft/world_gen_settings.dat"));return root;
    }
    @Test void inspectionDoesNotChangeFilesAndDefensivelyCopiesItsData()throws Exception {
        var root=snapshot();var file=data.resolve("worldarchitect-generation-v1.dat");NbtIo.writeCompressed(root,file);var bytes=Files.readAllBytes(file);
        var report=SavedWorldInspection.read(data);assertTrue(report.healthy());assertTrue(String.join("\n",report.rows()).contains("generator 2"));report.dimensions().orElseThrow().putString("malicious","edited");
        assertFalse(report.dimensions().orElseThrow().contains("malicious"));assertArrayEquals(bytes,Files.readAllBytes(file));try(var files=Files.walk(data)){assertEquals(2,files.filter(Files::isRegularFile).count());}
    }
    @Test void unknownCorruptMismatchedAndMissingArchiveCasesRemainUnmodified()throws Exception {
        var root=snapshot();var file=data.resolve("worldarchitect-generation-v1.dat");root.putInt("snapshot_schema",99);NbtIo.writeCompressed(root,file);assertFalse(SavedWorldInspection.read(data).healthy());
        root.putInt("snapshot_schema",2);var pin=new CompoundTag();pin.putInt("format",1);pin.putString("sha256","a".repeat(64));root.put("bundle_archive",pin);NbtIo.writeCompressed(root,file);var bytes=Files.readAllBytes(file);assertFalse(SavedWorldInspection.read(data).healthy());assertArrayEquals(bytes,Files.readAllBytes(file));
        root.remove("bundle_archive");root.getCompoundOrEmpty("data").putLong("seed",17);NbtIo.writeCompressed(root,file);assertFalse(SavedWorldInspection.read(data).healthy());
        Files.writeString(file,"bad compressed data");assertFalse(SavedWorldInspection.read(data).healthy());assertEquals("bad compressed data",Files.readString(file));
    }
}

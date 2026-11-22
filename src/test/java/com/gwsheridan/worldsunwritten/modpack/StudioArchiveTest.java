package com.gwsheridan.worldsunwritten.modpack;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StudioArchiveTest {
    @TempDir Path directory;
    @Test void archiveNamesAndEmbeddedPathsCannotEscapeTheirDirectories() {
        for(String name:List.of("partial-1.0.0.zip","skyblock-1_2-alpha.zip"))assertTrue(BundleFiles.archiveFilename(name));
        for(String name:List.of("../bad.zip","C:/bad.zip","a/b.zip","a\\b.zip",".zip","bad.jar"))assertFalse(BundleFiles.archiveFilename(name));
        assertTrue(BundleFiles.modularPath("data/wa_pack/worldgen/structure/cities/main.json"));
        for(String path:List.of("data/wa_pack/worldgen/structure/../bad.json","data/wa_pack/worldgen/biome/biome.json","data/wa_pack/worldgen/structure/name.JSON"))assertFalse(BundleFiles.modularPath(path));
    }
    @Test void modularDefinitionsAreAcceptedButArbitraryRegistryFilesAreRejected()throws Exception {
        var valid=archive("valid.zip","data/wa_pack/worldgen/structure/city.json","{\"type\":\"worldsunwritten:modular\"}");
        assertEquals("1.0.0",PresetBundle.read(valid).manifest().release());
        var wrongType=archive("wrong.zip","data/wa_pack/worldgen/structure/city.json","{\"type\":\"minecraft:jigsaw\"}");
        assertThrows(java.io.IOException.class,()->PresetBundle.read(wrongType));
        var arbitrary=archive("arbitrary.zip","data/wa_pack/worldgen/biome/biome.json","{}");
        assertThrows(java.io.IOException.class,()->PresetBundle.read(arbitrary));
        var traversal=archive("traversal.zip","data/wa_pack/worldgen/structure/../bad.json","{}");
        assertThrows(java.io.IOException.class,()->PresetBundle.read(traversal));
    }
    @Test void manifestOwnsFeatureListsInsteadOfKeepingMutableReferences() {
        var id=Identifier.parse("minecraft:overworld");var list=new ArrayList<>(List.of(BundleManifest.Feature.ORES));
        var base=PresetStudio.defaults("test","1.0.0",new net.minecraft.world.level.levelgen.WorldOptions(1,true,false));
        var manifest=new BundleManifest(base.preset(),base.title(),base.description(),base.author(),base.release(),BundleManifest.Lock.PARTIAL,base.options(),List.of(),Map.of(id,list),List.of(),List.of(),true);
        list.add(BundleManifest.Feature.CAVES);
        assertEquals(List.of(BundleManifest.Feature.ORES),manifest.editableFeatures().get(id));
        assertThrows(UnsupportedOperationException.class,()->manifest.editableFeatures().get(id).add(BundleManifest.Feature.CAVES));
    }
    @Test void draftOwnsPresetJsonAndTemplateBytes() {
        var json=JsonParser.parseString("{\"dimensions\":{}}").getAsJsonObject();byte[] bytes={1,2,3};
        var manifest=PresetStudio.defaults("test","1.0.0",new net.minecraft.world.level.levelgen.WorldOptions(1,true,false));
        var draft=new PresetStudio.Draft(manifest,json,Map.of("a",bytes));json.addProperty("changed",true);bytes[0]=7;
        assertFalse(draft.preset().has("changed"));assertEquals(1,draft.templates().get("a")[0]);draft.templates().get("a")[0]=8;assertEquals(1,draft.templates().get("a")[0]);
        draft.preset().addProperty("changed",true);assertFalse(draft.preset().has("changed"));
    }
    @Test void libraryMetadataCanBeListedWithoutLoadingPresetDependencies() throws Exception {
        var file=archive("metadata.zip","data/wa_pack/worldgen/structure/city.json","{\"type\":\"worldsunwritten:modular\"}");
        var destination=directory.resolve("studio/example/1.0.0.zip");Files.createDirectories(destination.getParent());
        try(var source=new ZipFile(file.toFile());var output=new ZipOutputStream(Files.newOutputStream(destination))){
            for(var entry:source.stream().toList()){
                byte[] bytes;try(var input=source.getInputStream(entry)){bytes=input.readAllBytes();}
                if(entry.getName().equals(BundleFiles.MANIFEST)){
                    var manifest=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    manifest.add("required_mods",JsonParser.parseString("[\"studio_missing_dependency\"]"));bytes=BundleFiles.jsonBytes(manifest);
                }
                output.putNextEntry(new ZipEntry(entry.getName()));output.write(bytes);output.closeEntry();
            }
        }
        var revisions=PresetStudio.list(directory);assertEquals(1,revisions.size());assertEquals("example",revisions.getFirst().name());assertEquals("1.0.0",revisions.getFirst().release());
    }
    @Test void historicalManifestsRemainReadableAndConflictingHeadersAreRejected() throws Exception {
        Path source=archive("original.zip","data/wa_pack/worldgen/structure/city.json","{\"type\":\"worldarchitect:modular\"}");
        for(boolean conflict:List.of(false,true)) {
            Path destination=directory.resolve(conflict?"conflicting.zip":"legacy.zip");
            try(var input=new ZipFile(source.toFile());var output=new ZipOutputStream(Files.newOutputStream(destination))) {
                for(var item:input.stream().toList()) {
                    byte[] bytes;try(var stream=input.getInputStream(item)){bytes=stream.readAllBytes();}
                    String name=item.getName().equals(BundleFiles.MANIFEST)?BundleFiles.LEGACY_MANIFEST:item.getName();
                    output.putNextEntry(new ZipEntry(name));output.write(bytes);output.closeEntry();
                    if(conflict&&name.equals(BundleFiles.LEGACY_MANIFEST)){output.putNextEntry(new ZipEntry(BundleFiles.MANIFEST));output.write(bytes);output.closeEntry();}
                }
            }
            if(conflict)assertThrows(java.io.IOException.class,()->PresetBundle.read(destination));
            else {assertEquals("1.0.0",PresetBundle.read(destination).manifest().release());assertEquals(BundleManifest.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,PresetBundle.read(source).manifest()).getOrThrow(),BundleManifest.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,PresetBundle.summary(destination)).getOrThrow());}
        }
    }
    private Path archive(String filename,String extra,String value)throws Exception {
        var output=directory.resolve(filename);
        try(var zip=new ZipOutputStream(Files.newOutputStream(output))){
            entry(zip,"pack.mcmeta","{}");
            entry(zip,BundleFiles.MANIFEST,"{\"format\":1,\"preset\":\"wa_pack:test\",\"title\":\"Test\",\"description\":\"Test archive\",\"author\":\"Test\",\"release\":\"1.0.0\",\"lock\":\"unlocked\",\"options\":{\"seed\":1}}");
            entry(zip,"data/wa_pack/worldgen/world_preset/test.json","{\"dimensions\":{}}");entry(zip,extra,value);
        }return output;
    }
    private void entry(ZipOutputStream zip,String name,String value)throws Exception {zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
}

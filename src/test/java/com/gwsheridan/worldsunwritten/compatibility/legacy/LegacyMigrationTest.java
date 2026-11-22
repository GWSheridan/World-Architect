package com.gwsheridan.worldsunwritten.compatibility.legacy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyMigrationTest {
    @TempDir Path temporary;

    @Test void embeddedMetadataHeadersKeepTheirOriginAndRejectAmbiguity() throws Exception {
        var root=new net.minecraft.nbt.CompoundTag();var metadata=new net.minecraft.nbt.CompoundTag();metadata.putInt("revision",7);
        root.put(LegacyIds.NAMESPACE,metadata);assertEquals(LegacyIds.NAMESPACE,LegacyMetadata.header(root));
        assertEquals(7,root.getCompoundOrEmpty(LegacyMetadata.header(root)).getIntOr("revision",0));
        root.put(LegacyIds.CURRENT,metadata.copy());assertThrows(java.io.IOException.class,()->LegacyMetadata.header(root));
        var malformed=new net.minecraft.nbt.CompoundTag();malformed.putInt(LegacyIds.NAMESPACE,1);assertThrows(java.io.IOException.class,()->LegacyMetadata.header(malformed));
    }

    @Test void everyPublishedApiDescriptorStillLinks() throws Exception {
        try (var input = getClass().getResourceAsStream("/compatibility/api-1-signatures.json")) {
            var classes = JsonParser.parseReader(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            assertEquals(50, classes.size());
            for (var element : classes) {
                var definition = element.getAsJsonObject(); var type = Class.forName(definition.get("name").getAsString(), false, getClass().getClassLoader());
                for (var value : definition.getAsJsonArray("members")) {
                    var member = value.getAsJsonObject(); String name = member.get("name").getAsString(), descriptor = member.get("descriptor").getAsString();
                    int access = member.get("access").getAsInt(); boolean found = false;
                    if (name.equals("<init>")) {
                        for (var ctor : type.getDeclaredConstructors()) if (MethodType.methodType(void.class, ctor.getParameterTypes()).descriptorString().equals(descriptor)) found = visible(ctor.getModifiers(), access);
                    } else {
                        for (Class<?> owner = type; owner != null && !found; owner = owner.getSuperclass()) {
                            if (member.get("kind").getAsString().equals("field")) {
                                for (var field : owner.getDeclaredFields()) if (field.getName().equals(name) && field.getType().descriptorString().equals(descriptor)) found = visible(field.getModifiers(), access);
                            } else {
                                for (var method : owner.getDeclaredMethods()) if (method.getName().equals(name) && MethodType.methodType(method.getReturnType(), method.getParameterTypes()).descriptorString().equals(descriptor)) found = visible(method.getModifiers(), access);
                            }
                        }
                    }
                    assertTrue(found, () -> "Missing legacy ABI: " + type.getName() + "." + name + descriptor);
                }
            }
        }
    }
    private static boolean visible(int current, int original) {
        return (Modifier.isPublic(current) || !Modifier.isPublic(original) && Modifier.isProtected(current)) && Modifier.isStatic(current) == Modifier.isStatic(original);
    }

    @Test void legacyValuesRoundTripWithoutChangingEnumsOrNestedSettings() {
        var old = com.gwsheridan.worldarchitect.spawn.WorldAdministration.Settings.DEFAULT;
        com.gwsheridan.worldsunwritten.spawn.WorldAdministration.Settings current = LegacyValues.current(old);
        assertEquals(com.gwsheridan.worldsunwritten.spawn.WorldAdministration.Settings.DEFAULT, current);
        assertEquals(old, LegacyValues.legacy(current));
        var layout = new com.gwsheridan.worldarchitect.api.CellLayout(com.gwsheridan.worldarchitect.api.CellLayout.Shape.POINTY_HEX, 32, 4, 2);
        com.gwsheridan.worldsunwritten.api.CellLayout converted = LegacyValues.current(layout);
        assertEquals(layout, LegacyValues.legacy(converted));
        assertEquals(layout.centre(new com.gwsheridan.worldarchitect.api.CellLayout.Cell(-3, 7)), LegacyValues.legacy(converted.centre(new com.gwsheridan.worldsunwritten.api.CellLayout.Cell(-3, 7))));
    }

    private record Probe(int value, boolean legacy) {}
    @Test void codecScopeIsNestedAndDoesNotLeakAcrossThreadsOrExceptions() {
        var codec = Codec.INT.fieldOf("value").xmap(value -> new Probe(value, LegacyCodecs.constructing()), Probe::value);
        var input = JsonParser.parseString("{\"value\":17}");
        assertTrue(LegacyCodecs.alias(codec).codec().parse(JsonOps.INSTANCE, input).getOrThrow().legacy());
        assertFalse(LegacyCodecs.during(() -> LegacyCodecs.canonical(codec).codec().parse(JsonOps.INSTANCE, input).getOrThrow()).legacy());
        assertFalse(LegacyCodecs.constructing());
        assertThrows(IllegalStateException.class, () -> LegacyCodecs.during(() -> { throw new IllegalStateException(); }));
        assertFalse(LegacyCodecs.constructing());
        var jobs = java.util.stream.IntStream.range(0, 32).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
            boolean legacy = i % 2 == 0;
            return LegacyCodecs.serialized(codec, legacy).codec().parse(JsonOps.INSTANCE, input).getOrThrow().legacy() == legacy && !LegacyCodecs.constructing();
        })).toList();
        assertTrue(jobs.stream().allMatch(CompletableFuture::join));
    }

    @Test void existingWorldPathsRemainOriginalAndMixedFamiliesAreRejected() throws Exception {
        Files.writeString(temporary.resolve(LegacyIds.NAMESPACE + "-generation-v1.dat"), "immutable snapshot");
        assertEquals(temporary.resolve(LegacyIds.NAMESPACE).resolve("generation"), WorldFiles.directory(temporary).resolve("generation"));
        assertEquals("immutable snapshot", Files.readString(WorldFiles.file(temporary, "-generation-v1.dat")));
        Files.writeString(temporary.resolve(LegacyIds.CURRENT + "-islands-v1.dat"), "unexpected");
        assertThrows(IllegalStateException.class, () -> WorldFiles.namespace(temporary));
        assertEquals("immutable snapshot", Files.readString(temporary.resolve(LegacyIds.NAMESPACE + "-generation-v1.dat")));
    }

    @Test void configurationImportPreservesEveryByteAndNeverOverwritesAnExistingInstallation() throws Exception {
        Path old = temporary.resolve(LegacyIds.NAMESPACE); Files.createDirectories(old.resolve("bundles"));
        byte[] archive = new byte[] {0, 1, 2, -1, 9}; Files.write(old.resolve("bundles/locked.zip"), archive);
        Files.writeString(old.resolve("developer.json"), "locked settings");
        Path current = LegacyConfiguration.directory(temporary);
        assertArrayEquals(archive, Files.readAllBytes(current.resolve("bundles/locked.zip")));
        assertArrayEquals(archive, Files.readAllBytes(old.resolve("bundles/locked.zip")));
        Files.writeString(current.resolve("developer.json"), "current settings");
        assertEquals(current, LegacyConfiguration.directory(temporary));
        assertEquals("current settings", Files.readString(current.resolve("developer.json")));
        assertEquals("locked settings", Files.readString(old.resolve("developer.json")));
        try (var files = Files.list(temporary)) { assertEquals(2, files.count()); }
    }
}

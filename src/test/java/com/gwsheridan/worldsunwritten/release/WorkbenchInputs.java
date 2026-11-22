package com.gwsheridan.worldsunwritten.release;

import com.google.gson.*;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchCommands;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Disposable inputs for workbench integration checks; no edits to shipped bundles. */
public final class WorkbenchInputs {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),out=Path.of(args[1]);String version=args[2];Files.createDirectory(out);
        // These immutable fixtures retain their bundle release across mod updates.
        Path regional=root.resolve("examples/regional-terrain/bundles/regional-demo-1.7.0.zip"),locked=root.resolve("examples/preset-studio/bundles/studio-skyblock-1.7.0.zip");
        Files.copy(regional,out.resolve("regional.zip"));Files.copy(locked,out.resolve("locked.zip"));
        var valid=scenario(out,"regional.zip",194723);var run=valid.getAsJsonArray("runs").get(0).getAsJsonObject();run.getAsJsonArray("regions").get(0).getAsJsonObject().addProperty("chunk_x",-32);
        var p=new JsonObject();p.addProperty("dimension","minecraft:overworld");p.addProperty("x",0);p.addProperty("y",180);p.addProperty("z",0);p.addProperty("block","minecraft:andesite");p.addProperty("biome","minecraft:desert");run.getAsJsonArray("probes").add(p);write(out,"valid",valid);
        var bad=valid.deepCopy();bad.getAsJsonArray("runs").get(0).getAsJsonObject().addProperty("restart",false);bad.getAsJsonArray("runs").get(0).getAsJsonObject().getAsJsonArray("probes").get(0).getAsJsonObject().addProperty("block","minecraft:gold_block");write(out,"bad-probe",bad);
        var lockedScenario=scenario(out,"locked.zip",19);reject(lockedScenario,"Locked world-generation option changed: [seed]");write(out,"locked-seed",lockedScenario);
        var missing=ReleaseExamples.read(locked);var manifest=ReleaseExamples.json(missing.get("worldsunwritten.bundle.json"));manifest.getAsJsonArray("required_mods").add("wa_missing_fixture_provider");missing.put("worldsunwritten.bundle.json",ReleaseExamples.bytes(manifest));ReleaseExamples.save(missing,out.resolve("missing-provider.zip"),out.resolve("missing-provider-source"));
        var missingScenario=scenario(out,"missing-provider.zip",18);reject(missingScenario,"requires missing mod wa_missing_fixture_provider");write(out,"missing-provider",missingScenario);
        var empty=ReleaseExamples.read(regional);String[] presetId=ReleaseExamples.json(empty.get("worldsunwritten.bundle.json")).get("preset").getAsString().split(":",2);String presetPath="data/"+presetId[0]+"/worldgen/world_preset/"+presetId[1]+".json";var emptyPreset=ReleaseExamples.json(empty.get(presetPath));
        var generator=new JsonObject();generator.addProperty("type","worldsunwritten:void");generator.addProperty("biome","minecraft:the_void");generator.addProperty("allow_unsafe_spawn",true);var dimension=new JsonObject();dimension.addProperty("min_y",-64);dimension.addProperty("height",384);generator.add("dimension",dimension);emptyPreset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").add("generator",generator);empty.put(presetPath,ReleaseExamples.bytes(emptyPreset));ReleaseExamples.save(empty,out.resolve("unsafe-spawn.zip"),out.resolve("unsafe-spawn-source"));
        var unsafe=scenario(out,"unsafe-spawn.zip",194723);unsafe.getAsJsonArray("runs").get(0).getAsJsonObject().addProperty("restart",false);write(out,"unsafe-spawn",unsafe);
    }
    private static JsonObject scenario(Path out,String name,long seed)throws Exception{return WorkbenchCommands.scenario(name,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(out.resolve(name)))),seed,List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end"));}
    private static void reject(JsonObject s,String cause){var run=s.getAsJsonArray("runs").get(0).getAsJsonObject();run.addProperty("expect_startup","reject");run.addProperty("expected_error",cause);run.addProperty("restart",false);}
    private static void write(Path out,String name,JsonObject s)throws Exception{Files.write(out.resolve(name+".scenario.json"),ReleaseExamples.bytes(s),StandardOpenOption.CREATE_NEW);}
}

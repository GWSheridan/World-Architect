package com.gwsheridan.worldarchitect.workbench;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import com.google.gson.*;
import com.gwsheridan.worldarchitect.modpack.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Exports detached test inputs. Starting test servers belongs to the source distribution's CLI. */
public final class WorkbenchCommands {
    private WorkbenchCommands() {}
    public static void register(RegisterCommandsEvent event) {
        var root=literal("wa_workbench").requires(hasPermission(LEVEL_OWNERS)).executes(c->{
            c.getSource().sendSuccess(()->Component.literal("Test workbench: /wa_workbench export <Studio name> <release>. Run the exported scenario with tools/workbench/run.mjs from the source distribution. Jobs use new disposable directories; reports stay local."),false);return 1;
        });
        if(ModpackRuntime.developerMode())root.then(literal("export").then(argument("name",word()).then(argument("release",word()).executes(c->{
            try {
                var server=c.getSource().getServer();String name=getString(c,"name"),release=getString(c,"release");
                var draft=PresetStudio.load(ModpackRuntime.directory(),name,release);var checked=PresetStudio.validate(draft,server.registryAccess());
                if(checked.preset().createWorldDimensions().dimensions().size()>16)throw new IllegalArgumentException("Select at most sixteen dimensions for one workbench scenario.");
                var bundle=PresetStudio.export(ModpackRuntime.directory(),name,release,server.registryAccess());
                var json=scenario(bundle.getFileName().toString(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(bundle))),draft.manifest().options().seed(),checked.preset().createWorldDimensions().dimensions().keySet().stream().map(k->k.identifier().toString()).sorted().toList());
                var path=bundle.resolveSibling(name+"-"+release+".scenario.json");Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(json)+"\n",StandardOpenOption.CREATE_NEW);
                c.getSource().sendSuccess(()->Component.literal("Exported test bundle and "+path.getFileName()+" to config/worldarchitect/exports. Review the scenario before running it."),false);return 1;
            } catch(Exception e){c.getSource().sendFailure(Component.literal("Workbench export failed: "+e.getMessage()));return 0;}
        }))));event.getDispatcher().register(root);
    }
    public static JsonObject scenario(String file,String sha,long seed,List<String> dimensions) {
        var json=new JsonObject();json.addProperty("format",1);var bundle=new JsonObject();bundle.addProperty("file",file);bundle.addProperty("sha256",sha);json.add("bundle",bundle);
        var budgets=new JsonObject();budgets.addProperty("timeout_seconds",300);budgets.addProperty("heap_mib",1400);json.add("budgets",budgets);
        var run=new JsonObject();run.addProperty("name","clean-install");run.addProperty("seed",Long.toString(seed));run.addProperty("expect_startup","accept");run.addProperty("safe_spawn",true);run.addProperty("restart",true);
        var ids=new JsonArray();var regions=new JsonArray();for(String dim:dimensions){ids.add(dim);var region=new JsonObject();region.addProperty("dimension",dim);region.addProperty("chunk_x",256);region.addProperty("chunk_z",256);region.addProperty("width",2);region.addProperty("depth",2);regions.add(region);}run.add("dimensions",ids);run.add("regions",regions);run.add("probes",new JsonArray());var runs=new JsonArray();runs.add(run);json.add("runs",runs);return json;
    }
}

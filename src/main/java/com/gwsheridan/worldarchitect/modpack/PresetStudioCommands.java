package com.gwsheridan.worldarchitect.modpack;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import java.io.IOException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class PresetStudioCommands {
    private PresetStudioCommands() {}
    public static void register(RegisterCommandsEvent event) {
        var root=literal("wa_preset").requires(hasPermission(LEVEL_ADMINS)).executes(c->help(c.getSource()));
        root.then(literal("help").executes(c->help(c.getSource())));
        root.then(literal("list").executes(c->run(c.getSource(),()->{
            var entries=PresetStudio.list(ModpackRuntime.directory());
            if(entries.isEmpty())tell(c.getSource(),"Preset Studio library is empty.");
            entries.forEach(entry->tell(c.getSource(),entry.label()));return entries.size();
        })));
        if(ModpackRuntime.developerMode()){
            root.then(literal("derive").then(argument("name",word()).then(argument("release",word()).then(argument("child",word()).then(argument("child_release",word()).executes(c->run(c.getSource(),()->{
                var directory=ModpackRuntime.directory();String name=getString(c,"name"),release=getString(c,"release"),child=getString(c,"child");var registry=c.getSource().getServer().registryAccess();
                var draft=PresetStudio.derive(directory,name,release,PresetStudio.fingerprint(directory,name,release),child,getString(c,"child_release"),registry);PresetStudio.save(directory,child,draft,registry);draft.manifest().inheritance().orElseThrow().report(draft).forEach(line->tell(c.getSource(),line));return 1;
            })))))));
            root.then(literal("override").then(argument("name",word()).then(argument("release",word()).then(argument("new_release",word()).then(argument("pointer",string()).then(argument("json",greedyString()).executes(c->run(c.getSource(),()->{
                var registry=c.getSource().getServer().registryAccess();String name=getString(c,"name");var source=PresetStudio.withRelease(PresetStudio.load(ModpackRuntime.directory(),name,getString(c,"release")),getString(c,"new_release"));
                var changed=PresetStudio.override(source,getString(c,"pointer"),com.google.gson.JsonParser.parseString(getString(c,"json")),false,registry);PresetStudio.save(ModpackRuntime.directory(),name,changed,registry);tell(c.getSource(),"Saved child override as "+name+" / "+changed.manifest().release());return 1;
            }))))))));
            root.then(literal("capture").then(argument("name",word()).then(argument("release",word()).executes(c->run(c.getSource(),()->{
                var server=c.getSource().getServer();String name=getString(c,"name"),release=getString(c,"release");
                var manifest=PresetStudio.defaults(name,release,server.getWorldGenSettings().options());
                var dimensions=new WorldDimensions(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM));
                var draft=PresetStudio.capture(manifest,dimensions,server.registryAccess());
                var path=PresetStudio.save(ModpackRuntime.directory(),name,draft,server.registryAccess());tell(c.getSource(),"Saved preset version "+name+" / "+path.getFileName());return 1;
            })))));
            root.then(literal("import").then(argument("zip",word()).then(argument("name",word()).executes(c->run(c.getSource(),()->{
                var draft=PresetStudio.readImport(ModpackRuntime.directory(),getString(c,"zip"),c.getSource().getServer().registryAccess());
                PresetStudio.save(ModpackRuntime.directory(),getString(c,"name"),draft,c.getSource().getServer().registryAccess());tell(c.getSource(),"Imported preset version "+getString(c,"name")+" / "+draft.manifest().release());return 1;
            })))));
            root.then(literal("duplicate").then(argument("name",word()).then(argument("release",word()).then(argument("copy",word()).then(argument("copy_release",word()).executes(c->run(c.getSource(),()->{
                var draft=PresetStudio.load(ModpackRuntime.directory(),getString(c,"name"),getString(c,"release"));
                var copy=PresetStudio.duplicate(draft,getString(c,"copy"),getString(c,"copy_release"));
                PresetStudio.save(ModpackRuntime.directory(),getString(c,"copy"),copy,c.getSource().getServer().registryAccess());tell(c.getSource(),"Duplicated preset version "+getString(c,"copy"));return 1;
            })))))));
            root.then(literal("export").then(argument("name",word()).then(argument("release",word()).executes(c->run(c.getSource(),()->{
                var file=PresetStudio.export(ModpackRuntime.directory(),getString(c,"name"),getString(c,"release"),c.getSource().getServer().registryAccess());tell(c.getSource(),"Exported Studio bundle "+file.getFileName());return 1;
            })))));
        }
        root.then(literal("validate").then(argument("name",word()).then(argument("release",word()).executes(c->run(c.getSource(),()->{
            var draft=PresetStudio.load(ModpackRuntime.directory(),getString(c,"name"),getString(c,"release"));
            PresetStudio.validate(draft,c.getSource().getServer().registryAccess()).report().forEach(line->tell(c.getSource(),line));return 1;
        })))));
        event.getDispatcher().register(root);
    }
    private static int help(CommandSourceStack source){tell(source,"Preset Studio: /wa_preset list | capture <name> <release> | import <zip> <name> | duplicate <name> <release> <copy> <new_release> | derive <parent> <release> <child> <child_release> | override <name> <release> <new_release> <quoted_pointer> <json> | validate <name> <release> | export <name> <release>. Authoring requires developer_mode. Local files live in config/worldarchitect/{studio,imports,exports}. Saved-world generation is immutable.");return 1;}
    private static void tell(CommandSourceStack source,String text){source.sendSuccess(()->Component.literal(text),false);}
    @FunctionalInterface private interface Operation{int run()throws IOException;}
    private static int run(CommandSourceStack source,Operation action){try{return action.run();}catch(IOException|IllegalArgumentException|IllegalStateException|com.google.gson.JsonParseException e){source.sendFailure(Component.literal("Preset Studio: "+e.getMessage()));return 0;}}
}

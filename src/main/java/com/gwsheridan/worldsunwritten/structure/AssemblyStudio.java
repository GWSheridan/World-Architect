package com.gwsheridan.worldsunwritten.structure;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.minecraft.commands.arguments.IdentifierArgument.*;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import com.gwsheridan.worldsunwritten.authoring.*;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-thread authoring sessions. Neither draft edits nor exports mutate generation settings. */
public final class AssemblyStudio {
    private static final long SHARED_BUDGET = 64L * 1024 * 1024;
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new WeakHashMap<>();
    private static final java.util.concurrent.ThreadPoolExecutor PREVIEWS=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),Thread.ofPlatform().daemon().name("worldsunwritten-pool-preview").factory(),new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private static final class Session {
        final UUID token = UUID.randomUUID(); AssemblyDraft draft; int serial, savedSerial, selected; long bytes, expires, previewSeed; boolean poolEditor,themeEditor;
        DraftHistory<AssemblyDraft> history; String base; boolean recoveryPending,recoveryAvailable; String recoveryProblem="";List<String> previewReport=List.of();java.util.concurrent.Future<?> previewJob;UUID previewToken;
        Session(AssemblyDraft draft) { this.draft = draft; history=new DraftHistory<>(draft.atRevision(0),v->v,AssemblyStudio::historyBytes,32,SHARED_BUDGET); }
    }
    private AssemblyStudio() {}
    public static void register(RegisterCommandsEvent event) {
        var root = literal("assembly").requires(s -> ModpackRuntime.allowStructureLibrary(s.getServer()) && hasPermission(LEVEL_GAMEMASTERS).test(s))
                .executes(run(c -> { show(c.getSource().getPlayerOrException(), null); return 1; }))
                .then(literal("new").then(argument("assembly", id()).then(argument("biome", id()).executes(run(c -> {
                    var player = c.getSource().getPlayerOrException();
                    var draft = AssemblyDraft.save(player.level().getServer(), new AssemblyDraft(getId(c,"assembly"),0,getId(c,"biome"),0,4,true,List.of()), -1);
                    open(player,draft); return 1;
                })))))
                .then(literal("open").then(argument("assembly", id()).executes(run(c -> { var p=c.getSource().getPlayerOrException();open(p,AssemblyDraft.load(p.level().getServer(),getId(c,"assembly")));return 1; }))))
                .then(command("save", serial -> serial.executes(run(c -> {
                    var s=session(c);resolved(s);s.draft=AssemblyDraft.save(c.getSource().getServer(),s.draft,s.draft.revision());s.base=DraftRecovery.fingerprint(document(c.getSource().getServer(),s.draft));s.serial++;s.savedSerial=s.serial;String status="Draft saved.";try{discardRecovery(c.getSource().getPlayerOrException(),s);}catch(IOException e){status="Draft saved; recovery cleanup unavailable: "+e.getMessage();}show(c.getSource().getPlayerOrException(),status);return 1;
                }))))
                .then(command("refresh", serial -> serial.executes(run(c -> {session(c);show(c.getSource().getPlayerOrException(),null);return 1;}))))
                .then(command("close", serial -> serial.executes(run(c -> {var s=session(c);discardRecovery(c.getSource().getPlayerOrException(),s);logout(c.getSource().getPlayerOrException());return 1;}))))
                .then(command("add", serial -> serial.then(partArguments(false))))
                .then(command("edit", serial -> serial.then(argument("index", integer(0,15)).then(partArguments(true)))))
                .then(command("remove", serial -> serial.then(argument("index",integer(0,15)).executes(run(c -> {
                    var s=session(c);var parts=new ArrayList<>(s.draft.parts());parts.remove(getInteger(c,"index"));change(c,s.draft.withParts(parts),Math.max(0,getInteger(c,"index")-1));return 1;
                })))))
                .then(command("settings", serial -> serial.then(argument("biome",id()).then(argument("y",integer(-64,64)).then(argument("slope",integer(0,32)).then(argument("random",BoolArgumentType.bool()).executes(run(c -> {
                    var s=session(c);change(c,s.draft.withSettings(getId(c,"biome"),getInteger(c,"y"),getInteger(c,"slope"),BoolArgumentType.getBool(c,"random")),s.selected);return 1;
                }))))))))
                .then(command("datapack", serial -> serial.then(argument("target",id()).executes(run(c -> {
                    var s=session(c);var path=AssemblyExports.datapack(c.getSource().getServer(),s.draft,getId(c,"target"));
                    c.getSource().sendSuccess(()->Component.literal("Exported assembly datapack to generated/worldsunwritten/assembly-exports/"+getId(c,"target")+"-datapack.zip"),false);show(c.getSource().getPlayerOrException(),"Datapack exported. Install the ZIP in a new world or pack.");return 1;
                })))))
                .then(command("bundle", serial -> serial.requires(hasPermission(LEVEL_ADMINS)).then(argument("target",id()).then(argument("dimension",id()).then(argument("release",word()).then(argument("seed",LongArgumentType.longArg()).executes(run(c -> {
                    var s=session(c);var path=AssemblyExports.bundle(c.getSource().getServer(),s.draft,getId(c,"target"),getId(c,"dimension"),getString(c,"release"),LongArgumentType.getLong(c,"seed"),c.getSource().getPlayerOrException().getPlainTextName());
                    c.getSource().sendSuccess(()->Component.literal("Exported assembly preset to config/worldsunwritten/exports/"+path.getFileName()),false);show(c.getSource().getPlayerOrException(),"Preset exported: "+path.getFileName());return 1;
                }))))))));
        root.then(command("module_collision",serial->serial.then(argument("index",integer(0,15)).then(argument("collision",word()).executes(run(c->{
            var s=session(c);var parts=new ArrayList<>(s.draft.parts());int index=getInteger(c,"index");if(index>=parts.size())throw new IOException("That module no longer exists.");
            var p=parts.get(index);var r=p.original().rules();var rules=new TemplateRules(r.dimension(),r.biome(),r.minY(),r.maxY(),r.terrain(),TemplateRules.Collision.parse(getString(c,"collision")));
            parts.set(index,new AssemblyDraft.Part(p.name(),p.source(),p.offset(),p.rotation(),p.mirror(),p.original().withRules(rules)));change(c,s.draft.withParts(parts),index);return 1;
        }))))));
        root.then(command("anchor",serial->serial.then(argument("mode",word()).then(argument("fixed_y",integer(net.minecraft.world.level.dimension.DimensionType.MIN_Y,net.minecraft.world.level.dimension.DimensionType.MAX_Y)).executes(run(c->{
            var s=session(c);var key=getString(c,"mode");var next=key.equals("legacy")?Optional.<PlacementAnchor>empty():Optional.of(new PlacementAnchor(PlacementAnchor.Mode.valueOf(key.toUpperCase(Locale.ROOT)),getInteger(c,"fixed_y")));
            if(key.equals("legacy")&&getInteger(c,"fixed_y")!=0)throw new IOException("Legacy anchoring does not accept a fixed height.");change(c,s.draft.withAnchor(next),s.selected);return 1;
        }))))));
        optionCommands(root,event);
        poolCommands(root);
        historyCommands(root);
        com.gwsheridan.worldsunwritten.commands.ModCommands.register(event,root);
    }
    private static void optionCommands(LiteralArgumentBuilder<CommandSourceStack> root,RegisterCommandsEvent event){
        root.then(command("foundation",serial->serial.then(argument("mode",word()).then(argument("material",net.minecraft.commands.arguments.blocks.BlockStateArgument.block(event.getBuildContext())).then(argument("depth",integer(1,16)).then(argument("clearance",integer(0,8)).then(argument("water",BoolArgumentType.bool()).executes(run(c->{var s=session(c);String mode=getString(c,"mode");var foundation=mode.equals("none")?Optional.<StructureFoundation>empty():Optional.of(new StructureFoundation(StructureFoundation.Mode.valueOf(mode.toUpperCase(Locale.ROOT)),net.minecraft.commands.arguments.blocks.BlockStateArgument.getBlock(c,"material").getState(),getInteger(c,"depth"),getInteger(c,"clearance"),BoolArgumentType.getBool(c,"water")));change(c,s.draft.withFoundation(foundation),s.selected);return 1;})))))))));
        root.then(command("theme_view",serial->serial.executes(run(c->{var s=session(c);resolved(s);s.themeEditor=true;s.poolEditor=false;show(c.getSource().getPlayerOrException(),null);return 1;}))));
        root.then(command("theme_back",serial->serial.executes(run(c->{var s=session(c);s.themeEditor=false;show(c.getSource().getPlayerOrException(),null);return 1;}))));
        root.then(command("palette",serial->serial.then(argument("name",word()).then(argument("weight",integer(1,100)).then(argument("from",id()).then(argument("to",id()).then(argument("preserve",BoolArgumentType.bool()).executes(run(c->{var s=session(c);var old=s.draft.themes().orElse(new StructureThemes(List.of(),List.of()));var palettes=new ArrayList<>(old.palettes());String name=getString(c,"name");var matches=palettes.stream().filter(p->p.name().equals(name)).findFirst();var mappings=new ArrayList<>(matches.map(StructureThemes.Palette::mappings).orElse(List.of()));mappings.removeIf(m->m.from().equals(getId(c,"from")));mappings.add(new StructureThemes.Mapping(getId(c,"from"),getId(c,"to"),BoolArgumentType.getBool(c,"preserve")));palettes.removeIf(p->p.name().equals(name));palettes.add(new StructureThemes.Palette(name,getInteger(c,"weight"),mappings));change(c,s.draft.withThemes(Optional.of(new StructureThemes(palettes,old.variants()))),s.selected);return 1;})))))))));
        root.then(command("variant",serial->serial.then(argument("name",word()).then(argument("module",word()).then(argument("weight",integer(1,100)).then(argument("source",id()).executes(run(c->{var s=session(c);String module=getString(c,"module");if(s.draft.parts().stream().noneMatch(p->p.name().equals(module)))throw new IOException("Choose an existing module.");var source=getId(c,"source");var snapshot=StructureLibrary.load(c.getSource().getServer(),source);var old=s.draft.themes().orElse(new StructureThemes(List.of(),List.of()));var variants=new ArrayList<>(old.variants());variants.removeIf(v->v.name().equals(getString(c,"name")));variants.add(new StructureThemes.Variant(getString(c,"name"),module,getInteger(c,"weight"),source,snapshot));change(c,s.draft.withThemes(Optional.of(new StructureThemes(old.palettes(),variants))),s.selected);return 1;}))))))));
        for(String type:List.of("palette","variant"))root.then(command(type+"_remove",serial->serial.then(argument("name",word()).executes(run(c->{var s=session(c);var old=s.draft.themes().orElseThrow(()->new IOException("No themes are configured."));var palettes=new ArrayList<>(old.palettes());var variants=new ArrayList<>(old.variants());boolean removed=type.equals("palette")?palettes.removeIf(p->p.name().equals(getString(c,"name"))):variants.removeIf(v->v.name().equals(getString(c,"name")));if(!removed)throw new IOException("That theme entry is no longer present.");change(c,s.draft.withThemes(palettes.isEmpty()&&variants.isEmpty()?Optional.empty():Optional.of(new StructureThemes(palettes,variants))),s.selected);return 1;})))));
        root.then(command("palette_mapping_remove",serial->serial.then(argument("name",word()).then(argument("from",id()).executes(run(c->{var s=session(c);var old=s.draft.themes().orElseThrow(()->new IOException("No palettes are configured."));var palettes=new ArrayList<>(old.palettes());var palette=palettes.stream().filter(p->p.name().equals(getString(c,"name"))).findFirst().orElseThrow(()->new IOException("Missing palette."));var mappings=new ArrayList<>(palette.mappings());if(!mappings.removeIf(m->m.from().equals(getId(c,"from"))))throw new IOException("Missing palette mapping.");palettes.remove(palette);if(!mappings.isEmpty())palettes.add(new StructureThemes.Palette(palette.name(),palette.weight(),mappings));change(c,s.draft.withThemes(palettes.isEmpty()&&old.variants().isEmpty()?Optional.empty():Optional.of(new StructureThemes(palettes,old.variants()))),s.selected);return 1;}))))));
    }
    private static long historyBytes(AssemblyDraft draft){try{return draft.estimatedBytes();}catch(IOException e){throw new IllegalArgumentException(e.getMessage(),e);}}
    private static JsonElement document(MinecraftServer server,AssemblyDraft draft)throws IOException{return AssemblyDraft.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),draft).getOrThrow(IOException::new);}
    private static java.nio.file.Path recoveryDirectory(MinecraftServer server){return com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.directory(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATA)).resolve("assembly-recovery");}
    private static String recoveryKey(ServerPlayer player,Session s){return player.getUUID()+"/"+s.draft.id();}
    private static void resolved(Session s)throws IOException{if(s.recoveryPending)throw new IOException("Restore or discard the pending assembly recovery before editing or saving.");}
    private static void discardRecovery(ServerPlayer p,Session s)throws IOException{DraftRecovery.discard(recoveryDirectory(p.level().getServer()),recoveryKey(p,s));s.recoveryAvailable=false;s.recoveryPending=false;s.recoveryProblem="";}
    private static void writeRecovery(ServerPlayer p,Session s)throws IOException{DraftRecovery.write(recoveryDirectory(p.level().getServer()),recoveryKey(p,s),s.base,document(p.level().getServer(),s.draft));}
    private static void historyCommands(LiteralArgumentBuilder<CommandSourceStack> root){
        for(String op:List.of("undo","redo"))root.then(command(op,serial->serial.executes(run(c->{
            var s=session(c);resolved(s);var next=s.history.fork();var restored=(op.equals("undo")?next.undo():next.redo()).atRevision(s.draft.revision());
            changeHistory(c,restored,s.selected,next);return 1;
        }))));
        root.then(command("recover",serial->serial.executes(run(c->{var s=session(c);if(!s.recoveryAvailable)throw new IOException("No compatible recovery draft is available.");var json=DraftRecovery.read(recoveryDirectory(c.getSource().getServer()),recoveryKey(c.getSource().getPlayerOrException(),s),s.base).orElseThrow(()->new IOException("Recovery file is missing."));var recovered=AssemblyDraft.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,c.getSource().getServer().registryAccess()),json).getOrThrow(IOException::new).check();if(!recovered.id().equals(s.draft.id())||recovered.revision()!=s.draft.revision())throw new IOException("Recovery identity or revision differs.");var next=s.history.fork();next.checkpoint(recovered.atRevision(0));changeHistory(c,recovered,s.selected,next,true);return 1;}))));
        root.then(command("discard_recovery",serial->serial.executes(run(c->{var s=session(c);discardRecovery(c.getSource().getPlayerOrException(),s);s.serial++;show(c.getSource().getPlayerOrException(),"Recovery discarded.");return 1;}))));
        root.then(command("duplicate",serial->serial.then(argument("index",integer(0,15)).executes(run(c->{
            var s=session(c);var parts=new ArrayList<>(s.draft.parts());var part=parts.get(getInteger(c,"index"));String prefix=part.name().substring(0,Math.min(24,part.name().length()))+"_copy";int n=1;var names=parts.stream().map(AssemblyDraft.Part::name).toList();while(names.contains(prefix+n))n++;
            parts.add(new AssemblyDraft.Part(prefix+n,part.source(),part.offset(),part.rotation(),part.mirror(),part.original()));change(c,s.draft.withParts(parts),parts.size()-1);return 1;
        })))));
        root.then(command("move",serial->serial.then(argument("index",integer(0,15)).then(argument("to",integer(0,15)).executes(run(c->{var s=session(c);var parts=new ArrayList<>(s.draft.parts());var part=parts.remove(getInteger(c,"index"));parts.add(getInteger(c,"to"),part);change(c,s.draft.withParts(parts),getInteger(c,"to"));return 1;}))))));
    }
    private static ProceduralPool pool(CommandContext<CommandSourceStack> c) throws IOException,CommandSyntaxException { return session(c).draft.pool().orElseThrow(()->new IOException("Enable pool settings first.")); }
    private static void changePool(CommandContext<CommandSourceStack> c, Optional<ProceduralPool> pool) throws IOException,CommandSyntaxException { var s=session(c);s.poolEditor=true;change(c,s.draft.withPool(pool),s.selected); }
    private static void poolCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(command("constraints",serial->serial.then(argument("module",word()).then(argument("min",integer(0,16)).then(argument("max",integer(0,16)).then(argument("depth",integer(1,15)).executes(run(c->{
            var old=pool(c);String module=getString(c,"module");int min=getInteger(c,"min"),max=getInteger(c,"max");if(session(c).draft.parts().stream().noneMatch(p->p.name().equals(module)))throw new IOException("Choose an existing source module.");
            var limits=new ArrayList<>(old.constraints().counts());limits.removeIf(v->v.module().equals(module));if(min!=0||max!=16)limits.add(new ProceduralPool.ModuleCount(module,min,max));
            changePool(c,Optional.of(old.withConstraints(new ProceduralPool.Constraints(getInteger(c,"depth"),limits))));return 1;
        }))))))));
        root.then(command("move_choice",serial->serial.then(argument("index",integer(0,63)).then(argument("to",integer(0,63)).executes(run(c->{var old=pool(c);var choices=new ArrayList<>(old.choices());var choice=choices.remove(getInteger(c,"index"));choices.add(getInteger(c,"to"),choice);changePool(c,Optional.of(old.withChoices(choices)));return 1;}))))));
        root.then(command("preview_batch",serial->serial.then(argument("seed",LongArgumentType.longArg()).then(argument("samples",integer(1,16)).executes(run(AssemblyStudio::previewBatch))))));
        root.then(command("pool_view",serial->serial.executes(run(c->{var s=session(c);s.poolEditor=true;show(c.getSource().getPlayerOrException(),null);return 1;}))));
        root.then(command("pool_back",serial->serial.executes(run(c->{var s=session(c);s.poolEditor=false;show(c.getSource().getPlayerOrException(),null);return 1;}))));
        root.then(command("static",serial->serial.executes(run(c->{changePool(c,Optional.empty());return 1;}))));
        root.then(command("preview_seed",serial->serial.then(argument("seed",LongArgumentType.longArg()).executes(run(c->{var s=session(c);s.previewSeed=LongArgumentType.getLong(c,"seed");show(c.getSource().getPlayerOrException(),null);return 1;})))));
        root.then(command("pool",serial->serial.then(argument("root",word()).then(argument("max",integer(2,16)).then(argument("attempts",integer(1,256)).then(argument("salt",LongArgumentType.longArg()).executes(run(c->{
            var old=session(c).draft.pool();changePool(c,Optional.of(old.orElse(new ProceduralPool(getString(c,"root"),8,128,0,List.of(),List.of())).withSettings(getString(c,"root"),getInteger(c,"max"),getInteger(c,"attempts"),LongArgumentType.getLong(c,"salt"))));return 1;
        }))))))));
        root.then(command("connection",serial->serial.then(argument("module",word()).then(argument("name",word()).then(argument("position",blockPos()).then(argument("facing",word()).then(argument("pool",word()).then(argument("channel",word()).executes(run(c->{
            var old=pool(c);var ports=new ArrayList<>(old.ports());var direction=net.minecraft.core.Direction.byName(getString(c,"facing"));if(direction==null)throw new IOException("Use north, south, east or west.");
            var port=new ProceduralPool.Port(getString(c,"module"),getString(c,"name"),getBlockPos(c,"position"),direction,getString(c,"pool"),getString(c,"channel"));ports.removeIf(p->p.module().equals(port.module())&&p.name().equals(port.name()));ports.add(port);changePool(c,Optional.of(old.withPorts(ports)));return 1;
        }))))))))));
        root.then(command("remove_connection",serial->serial.then(argument("module",word()).then(argument("name",word()).executes(run(c->{var old=pool(c);var ports=old.ports().stream().filter(p->!p.module().equals(getString(c,"module"))||!p.name().equals(getString(c,"name"))).toList();changePool(c,Optional.of(old.withPorts(ports)));return 1;}))))));
        root.then(command("choice",serial->serial.then(argument("pool",word()).then(argument("module",word()).then(argument("weight",integer(1,100)).then(argument("endcap",BoolArgumentType.bool()).executes(run(c->{
            var old=pool(c);var choice=new ProceduralPool.Choice(getString(c,"pool"),getString(c,"module"),getInteger(c,"weight"),BoolArgumentType.getBool(c,"endcap"));var choices=new ArrayList<>(old.choices());choices.removeIf(v->v.pool().equals(choice.pool())&&v.module().equals(choice.module())&&v.endcap()==choice.endcap());choices.add(choice);changePool(c,Optional.of(old.withChoices(choices)));return 1;
        }))))))));
        root.then(command("remove_choice",serial->serial.then(argument("index",integer(0,63)).executes(run(c->{var old=pool(c);var choices=new ArrayList<>(old.choices());choices.remove(getInteger(c,"index"));changePool(c,Optional.of(old.withChoices(choices)));return 1;})))));
    }
    private static int previewBatch(CommandContext<CommandSourceStack> c)throws IOException,CommandSyntaxException{
        var s=session(c);resolved(s);if(s.previewToken!=null)throw new IOException("A pool preview is already running.");
        var structure=s.draft.structure(c.getSource().getServer());var server=c.getSource().getServer();var owner=c.getSource().getPlayerOrException().getUUID();int expected=s.serial,samples=getInteger(c,"samples");long seed=LongArgumentType.getLong(c,"seed");PREVIEWS.purge();
        UUID request=UUID.randomUUID();s.previewToken=request;try{s.previewJob=PREVIEWS.submit(()->{
            var rows=new ArrayList<String>();long start=System.nanoTime();
            try{for(int i=0;i<samples;i++){
                if(Thread.currentThread().isInterrupted())return;
                if(i>0&&System.nanoTime()-start>500_000_000L){rows.add("Sampling truncated at the preview time budget.");break;}
                var report=structure.previewReport(seed+i);rows.add("Seed "+(seed+i)+": "+(report.modules().isPresent()?report.modules().orElseThrow().size()+" modules":"rejected")+" · "+report.reasons().getLast());
            }}catch(IOException|RuntimeException e){rows.add("Preview failed: "+clip(e.getMessage()==null?"Invalid layout":e.getMessage(),256));}
            server.execute(()->{var current=sessions(server).get(owner);var player=server.getPlayerList().getPlayer(owner);if(current!=s||!request.equals(s.previewToken))return;s.previewToken=null;if(s.serial!=expected||player==null||!hasPermission(LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())||!ModpackRuntime.allowStructureLibrary(server))return;s.previewReport=List.copyOf(rows);s.previewSeed=seed;s.serial++;try{show(player,"Batch layout preview complete.");}catch(IOException e){player.sendSystemMessage(Component.literal("Cannot display completed preview."));}});
        });}catch(java.util.concurrent.RejectedExecutionException e){s.previewToken=null;throw new IOException("Pool preview worker is busy. Try again after the current job.");}
        c.getSource().sendSuccess(()->Component.literal("Sampling detached layouts in the preview worker."),false);return 1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> command(String name, Consumer<RequiredArgumentBuilder<CommandSourceStack,Integer>> configure) {
        var serial=argument("serial",integer(0));configure.accept(serial);return literal(name).then(argument("token",word()).then(serial));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,Identifier> partArguments(boolean edit) {
        return argument("template",id()).then(argument("module",word()).then(argument("offset",blockPos()).then(argument("rotation",word()).then(argument("mirror",word()).executes(run(c -> {
            var s=session(c);var parts=new ArrayList<>(s.draft.parts());int index=edit?getInteger(c,"index"):parts.size();
            if(edit && index>=parts.size())throw new IOException("That module is no longer in the draft.");
            if(!edit && parts.size()>=ModularStructure.MAX_MODULES)throw new IOException("Assemblies support at most 16 modules.");
            var source=getId(c,"template");var original=edit && parts.get(index).source().equals(source)?parts.get(index).original():StructureLibrary.load(c.getSource().getServer(),source);
            var part=new AssemblyDraft.Part(getString(c,"module"),source,getBlockPos(c,"offset"),rotation(getString(c,"rotation")),mirror(getString(c,"mirror")),original);
            if(edit)parts.set(index,part);else parts.add(part);change(c,s.draft.withParts(parts),index);return 1;
        }))))));
    }
    static Rotation rotation(String key) {return switch(key){case "none"->Rotation.NONE;case "cw90"->Rotation.CLOCKWISE_90;case "cw180"->Rotation.CLOCKWISE_180;case "ccw90"->Rotation.COUNTERCLOCKWISE_90;default->throw new IllegalArgumentException("Use none, cw90, cw180 or ccw90.");};}
    static Mirror mirror(String key) {return switch(key){case "none"->Mirror.NONE;case "left_right"->Mirror.LEFT_RIGHT;case "front_back"->Mirror.FRONT_BACK;default->throw new IllegalArgumentException("Use none, left_right or front_back.");};}
    static String rotationKey(Rotation rotation) {return switch(rotation){case NONE->"none";case CLOCKWISE_90->"cw90";case CLOCKWISE_180->"cw180";case COUNTERCLOCKWISE_90->"ccw90";};}
    static String mirrorKey(Mirror mirror) {return switch(mirror){case NONE->"none";case LEFT_RIGHT->"left_right";case FRONT_BACK->"front_back";};}
    private static Map<UUID,Session> sessions(MinecraftServer server) {
        var sessions=SESSIONS.computeIfAbsent(server,unused->new HashMap<>());sessions.values().removeIf(s->{boolean expired=s.expires<System.nanoTime();if(expired&&s.previewJob!=null)s.previewJob.cancel(true);return expired;});return sessions;
    }
    private static Session session(CommandContext<CommandSourceStack> c) throws CommandSyntaxException,IOException {
        var p=c.getSource().getPlayerOrException();var s=sessions(p.level().getServer()).get(p.getUUID());
        if(s==null||!s.token.toString().equals(getString(c,"token"))||s.serial!=getInteger(c,"serial"))throw new IOException("This editor view is stale. Reopen Assembly Studio.");
        s.expires=System.nanoTime()+1_800_000_000_000L;return s;
    }
    private static void reserve(ServerPlayer player, Session candidate, AssemblyDraft draft) throws IOException {
        long current=draft.check().estimatedBytes();var all=sessions(player.level().getServer());
        long total=all.entrySet().stream().filter(e->!e.getKey().equals(player.getUUID())).mapToLong(e->e.getValue().bytes).sum();
        try{candidate.history.trimTo(SHARED_BUDGET-total);}catch(IllegalArgumentException e){throw new IOException("Assembly editor memory is full. Save and close another editing session.");}
        long bytes=Math.max(current,candidate.history.bytes());
        if(total+bytes>SHARED_BUDGET||!all.containsKey(player.getUUID())&&all.size()>=16)throw new IOException("Assembly editor memory is full. Save and close another editing session.");
        candidate.bytes=bytes;candidate.expires=System.nanoTime()+1_800_000_000_000L;
    }
    private static void open(ServerPlayer p,AssemblyDraft draft) throws IOException {
        var server=p.level().getServer();var previous=sessions(server).get(p.getUUID());
        var s=new Session(draft);s.base=DraftRecovery.fingerprint(document(server,draft));
        try{var saved=previous==null?DraftRecovery.read(recoveryDirectory(server),recoveryKey(p,s),s.base):Optional.<JsonElement>empty();s.recoveryPending=saved.isPresent();s.recoveryAvailable=saved.isPresent();}
        catch(IOException|RuntimeException e){s.recoveryPending=true;s.recoveryProblem=e.getMessage();}
        reserve(p,s,draft);if(previous!=null){discardRecovery(p,previous);if(previous.previewJob!=null)previous.previewJob.cancel(true);}sessions(server).put(p.getUUID(),s);show(p,null);
    }
    private static void change(CommandContext<CommandSourceStack> c,AssemblyDraft draft,int selected) throws IOException,CommandSyntaxException {
        var s=session(c);resolved(s);var next=s.history.fork();next.checkpoint(draft.atRevision(0));changeHistory(c,draft,selected,next);
    }
    private static void changeHistory(CommandContext<CommandSourceStack> c,AssemblyDraft draft,int selected,DraftHistory<AssemblyDraft> history)throws IOException,CommandSyntaxException{
        changeHistory(c,draft,selected,history,false);
    }
    private static void changeHistory(CommandContext<CommandSourceStack> c,AssemblyDraft draft,int selected,DraftHistory<AssemblyDraft> history,boolean consumeRecovery)throws IOException,CommandSyntaxException{
        var s=session(c);var player=c.getSource().getPlayerOrException();
        if(AssemblyDraft.load(c.getSource().getServer(),s.draft.id()).revision()!=s.draft.revision())throw new IOException("The saved draft changed. Reopen it before restoring history.");
        var budget=new Session(draft);budget.history=history;reserve(player,budget,draft);
        if(consumeRecovery){s.recoveryAvailable=false;s.recoveryPending=false;s.recoveryProblem="";}
        if(s.previewJob!=null)s.previewJob.cancel(true);s.previewToken=null;s.previewReport=List.of();
        s.history=history;s.bytes=budget.bytes;s.expires=budget.expires;s.draft=draft;s.serial++;s.selected=selected;
        String status=null;try{writeRecovery(player,s);}catch(IOException e){status="Draft edited; recovery unavailable: "+e.getMessage();}show(player,status);
    }
    public static void logout(ServerPlayer player) {var all=SESSIONS.get(player.level().getServer());if(all!=null){var s=all.remove(player.getUUID());if(s!=null&&s.previewJob!=null)s.previewJob.cancel(true);}}
    private static void show(ServerPlayer player,String status) throws IOException {
        var server=player.level().getServer();var s=sessions(server).get(player.getUUID());var data=new CompoundTag();
        data.putString("kind","assembly");data.putBoolean("active",s!=null);data.putBoolean("can_bundle",hasPermission(LEVEL_ADMINS).test(player.createCommandSourceStack())&&ModpackRuntime.developerMode());
        var library=new ListTag();StructureLibrary.list(server).stream().limit(128).forEach(id->library.add(StringTag.valueOf(id.toString())));data.put("library",library);
        var drafts=new ListTag();AssemblyDraft.list(server).forEach(id->drafts.add(StringTag.valueOf(id.toString())));data.put("drafts",drafts);
        if(s!=null){
            data.putBoolean("can_undo",s.history.canUndo());data.putBoolean("can_redo",s.history.canRedo());data.putBoolean("recovery_pending",s.recoveryPending);data.putBoolean("can_recover",s.recoveryAvailable);data.putString("recovery_problem",clip(s.recoveryProblem,256));
            var d=s.draft;data.putBoolean("pool_editor",s.poolEditor);data.putLong("preview_seed",s.previewSeed);d.pool().ifPresent(pool->data.put("pool",ProceduralPool.CODEC.encodeStart(NbtOps.INSTANCE,pool).getOrThrow()));data.putString("id",d.id().toString());data.putString("token",s.token.toString());data.putInt("serial",s.serial);data.putInt("revision",d.revision());data.putInt("selected",s.selected);data.putBoolean("dirty",s.serial!=s.savedSerial);
            data.putString("biome",d.biome().toString());data.putInt("y_offset",d.yOffset());data.putInt("slope",d.maxSlope());data.putBoolean("random",d.randomRotation());
            data.putString("anchor",d.anchor().map(a->a.mode().getSerializedName()).orElse("legacy"));data.putInt("fixed_y",d.anchor().map(PlacementAnchor::fixedY).orElse(0));
            data.putBoolean("theme_editor",s.themeEditor);data.putString("foundation_mode",d.foundation().map(f->f.mode().getSerializedName()).orElse("none"));data.putString("foundation_material",d.foundation().map(f->net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(f.material())).orElse("minecraft:stone_bricks"));data.putInt("foundation_depth",d.foundation().map(StructureFoundation::maxDepth).orElse(8));data.putInt("foundation_clearance",d.foundation().map(StructureFoundation::clearHeight).orElse(0));data.putBoolean("foundation_water",d.foundation().map(StructureFoundation::replaceWater).orElse(false));
            var palettes=new ListTag();var variants=new ListTag();d.themes().ifPresent(t->{for(var palette:t.palettes()){var row=new CompoundTag();row.putString("name",palette.name());row.putInt("weight",palette.weight());var mappings=new ListTag();for(var mapping:palette.mappings()){var m=new CompoundTag();m.putString("from",mapping.from().toString());m.putString("to",mapping.to().toString());m.putBoolean("preserve",mapping.preserveProperties());mappings.add(m);}row.put("mappings",mappings);palettes.add(row);}for(var variant:t.variants()){var row=new CompoundTag();row.putString("name",variant.name());row.putString("module",variant.module());row.putString("source",variant.source().toString());row.putInt("weight",variant.weight());variants.add(row);}});data.put("palettes",palettes);data.put("variants",variants);
            String problem="Assembly valid · templates and markers are embedded snapshots.";boolean valid=true;
            try{d.structure(server);}catch(IOException|RuntimeException e){problem=e.getMessage()==null?"Check assembly settings.":e.getMessage();valid=false;}
            data.putBoolean("valid",valid);data.putString("status",clip(status==null?problem:status,1536));
            var cells=new ArrayList<Integer>();var markers=new ListTag();var modules=new ListTag();var dependencies=new TreeSet<String>();int sx=1,sy=1,sz=1;
            for(var part:d.parts()){
                var entry=part.transformed();var offset=part.offset();var size=entry.template().size();sx=Math.max(sx,offset.getX()+size.getX());sy=Math.max(sy,offset.getY()+size.getY());sz=Math.max(sz,offset.getZ()+size.getZ());
                var module=new CompoundTag();module.putString("name",part.name());module.putString("source",part.source().toString());module.putString("rotation",rotationKey(part.rotation()));module.putString("mirror",mirrorKey(part.mirror()));module.putIntArray("offset",new int[]{offset.getX(),offset.getY(),offset.getZ()});module.putIntArray("size",new int[]{size.getX(),size.getY(),size.getZ()});module.putString("rules",describe(entry.rules()));module.putInt("snapshot_revision",part.original().revision());modules.add(module);
            }
            List<ModularStructure.Module> displayed=List.of();
            try { var report=d.structure(server).previewReport(s.previewSeed);displayed=report.modules().orElse(List.of());var lines=new ListTag();report.lines().forEach(line->lines.add(StringTag.valueOf(line)));s.previewReport.forEach(line->lines.add(StringTag.valueOf(line)));data.put("pool_report",lines); } catch(IOException|RuntimeException e) {
                if(d.pool().isEmpty()) { var preview=new ArrayList<ModularStructure.Module>();for(var part:d.parts())preview.add(new ModularStructure.Module(part.name(),part.offset(),part.transformed()));displayed=preview; }
            }
            sx=1;sy=1;sz=1;
            for(var part:displayed){var entry=part.entry();var offset=part.offset();var size=entry.template().size();sx=Math.max(sx,offset.getX()+size.getX());sy=Math.max(sy,offset.getY()+size.getY());sz=Math.max(sz,offset.getZ()+size.getZ());
                for(var cell:entry.template().blocks()) {var pos=offset.offset(cell.pos());cells.add(pos.getX()|pos.getY()<<7|pos.getZ()<<14);cells.add(cell.state().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).col);dependencies.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getNamespace());}
                for(var named:entry.markers().entrySet()){var marker=new CompoundTag();var pos=offset.offset(named.getValue().pos());marker.putString("name",part.name()+":"+named.getKey());marker.putString("facing",named.getValue().facing().getSerializedName());marker.putIntArray("pos",new int[]{pos.getX(),pos.getY(),pos.getZ()});markers.add(marker);}
            }
            if(d.pool().isPresent()&&displayed.isEmpty()&&valid)data.putString("status","Definition valid; no complete layout for this sample seed. Try another seed or revise connections.");
            dependencies.remove("minecraft");data.putString("dependencies",clip(String.join(", ",dependencies),1024));data.put("modules",modules);data.put("markers",markers);data.putIntArray("cells",cells.stream().mapToInt(Integer::intValue).toArray());data.putIntArray("size",new int[]{sx,sy,sz});
        }
        PacketDistributor.sendToPlayer(player,new StudioViewPayload(data));
    }
    private static String describe(TemplateRules rules){return "Dimension "+rules.dimension().map(Object::toString).orElse("any")+"; biome "+rules.biome().map(Object::toString).orElse("any")+"; Y "+rules.minY()+" to "+rules.maxY()+"; terrain "+rules.terrain().key()+"; collision "+rules.collision().key()+".";}
    private static String clip(String value,int max){return value.substring(0,Math.min(value.length(),max));}
    private static Command<CommandSourceStack> run(Action action){return c->{try{return action.run(c);}catch(IOException|RuntimeException e){c.getSource().sendFailure(Component.literal(clip(e.getMessage()==null?"Assembly action failed.":e.getMessage(),1536)));return 0;}};}
    @FunctionalInterface private interface Action {int run(CommandContext<CommandSourceStack> c)throws IOException,CommandSyntaxException;}
}

package com.gwsheridan.worldsunwritten.structure;

import net.minecraft.world.entity.player.Player;
import com.gwsheridan.worldsunwritten.platform.Platform;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.minecraft.commands.arguments.IdentifierArgument.*;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.gwsheridan.worldsunwritten.commands.CommandRegistration;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.*;
import java.nio.file.*;

/** Bounded server-owned sessions bind every request to an immutable saved preset. */
public final class SiteStudio {
    private static final long BUDGET=64L*1024*1024;
    private static final Map<MinecraftServer,Map<UUID,Session>> SESSIONS=new WeakHashMap<>();
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),Thread.ofPlatform().daemon().name("worldsunwritten-site-preview").factory(),new ThreadPoolExecutor.AbortPolicy());
    private static final class Session {
        final UUID token=UUID.randomUUID();final String name,release,hash;final PresetStudio.Checked source;final long bytes;int serial;long expires;Future<?> future;CompoundTag searchReport;
        Session(String name,String release,String hash,PresetStudio.Checked source,long bytes){this.name=name;this.release=release;this.hash=hash;this.source=source;this.bytes=bytes;touch();}
        void touch(){expires=System.nanoTime()+1_800_000_000_000L;}
    }
    private SiteStudio(){}
    private static boolean allowed(ServerPlayer p){return ModpackRuntime.developerMode()&&ModpackRuntime.allowStructureLibrary(p.level().getServer())&&hasPermission(LEVEL_ADMINS).test(p.createCommandSourceStack());}
    private static Map<UUID,Session> sessions(MinecraftServer server){var all=SESSIONS.computeIfAbsent(server,k->new HashMap<>());all.values().removeIf(s->{if(s.expires<System.nanoTime()){if(s.future!=null)s.future.cancel(true);return true;}return false;});return all;}
    private static String hash(String name,String release)throws IOException{
        var path=ModpackRuntime.directory().resolve("studio").resolve(name).resolve(release+".zip");
        for(var p=path.toAbsolutePath().normalize();p!=null;p=p.getParent())if(java.nio.file.Files.isSymbolicLink(p))throw new IOException("Preset paths cannot contain symbolic links.");
        if(!java.nio.file.Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)||java.nio.file.Files.size(path)>32L*1024*1024)throw new IOException("Expected a bounded saved bundle.");
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(path)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static void register(CommandRegistration event){
        var root=literal("site").requires(s->ModpackRuntime.developerMode()&&ModpackRuntime.allowStructureLibrary(s.getServer())&&hasPermission(LEVEL_ADMINS).test(s));
        root.then(literal("studio").then(argument("name",word()).then(argument("release",word()).executes(c->{try{
            var p=c.getSource().getPlayerOrException();open(p,getString(c,"name"),getString(c,"release"),null);return 1;
        }catch(IOException|RuntimeException e){c.getSource().sendFailure(Component.literal("Cannot open site inspector: "+e.getMessage()));return 0;}}).then(argument("sha256",word()).executes(c->{try{open(c.getSource().getPlayerOrException(),getString(c,"name"),getString(c,"release"),getString(c,"sha256"));return 1;}catch(IOException|RuntimeException e){c.getSource().sendFailure(Component.literal("Cannot open site inspector: "+e.getMessage()));return 0;}})))));
        root.then(literal("request").then(argument("token",word()).then(argument("serial",integer(0)).then(argument("dimension",id()).then(argument("structure",id()).then(argument("seed",LongArgumentType.longArg()).then(argument("x",integer()).then(argument("z",integer()).executes(c->{try{
            var p=c.getSource().getPlayerOrException();var s=sessions(c.getSource().getServer()).get(p.getUUID());
            if(s==null||!s.token.toString().equals(getString(c,"token"))||s.serial!=getInteger(c,"serial"))throw new IOException("This site view is stale. Reopen it.");
            if(!s.hash.equals(hash(s.name,s.release)))throw new IOException("The saved preset fingerprint changed. Reopen the inspector.");
            var input=SiteInspector.prepare(s.source,getId(c,"dimension"),getId(c,"structure"),LongArgumentType.getLong(c,"seed"),getInteger(c,"x"),getInteger(c,"z"),c.getSource().getServer().registryAccess(),c.getSource().getServer().getStructureTemplateManager());
            if(s.future!=null)s.future.cancel(true);WORKER.purge();int serial=++s.serial;s.touch();var server=c.getSource().getServer();var owner=p.getUUID();
            try{s.future=WORKER.submit(()->{var report=SiteInspector.inspect(input);server.execute(()->{
                var current=sessions(server).get(owner);var player=server.getPlayerList().getPlayer(owner);if(current!=s||s.serial!=serial||player==null||!allowed(player))return;
                try{if(!s.hash.equals(hash(s.name,s.release)))throw new IOException("The saved preset changed during sampling.");decorate(s,report);send(player,report);}catch(IOException e){player.sendSystemMessage(Component.literal(e.getMessage()));}
            });});}catch(RejectedExecutionException e){send(p,view(s,"incomplete","The preview worker is busy; retry this site."));}
            return 1;
        }catch(IOException|RuntimeException e){var p=c.getSource().getPlayerOrException();var s=sessions(c.getSource().getServer()).get(p.getUUID());if(s!=null)send(p,view(s,"rejected","Site request refused: "+e.getMessage()));c.getSource().sendFailure(Component.literal("Site request refused: "+e.getMessage()));return 0;}})))))))));
        root.then(sessionCommand("search",argument("dimension",id()),argument("structure",id()),argument("seed",LongArgumentType.longArg()),argument("x",integer()),argument("z",integer()),argument("radius",integer(1,SiteSearch.MAX_RADIUS)),argument("limit",integer(1,SiteSearch.MAX_CANDIDATES)).executes(action(SiteStudio::search))));
        root.then(sessionCommand("cancel",argument("confirm",word()).executes(action(c->{var s=current(c);if(!getString(c,"confirm").equals("search"))throw new IOException("Choose the active search.");if(s.future!=null)s.future.cancel(true);s.serial++;s.searchReport=null;s.touch();send(c.getSource().getPlayerOrException(),view(s,"cancelled","Search cancelled. Late results are discarded."));return 1;}))));
        root.then(sessionCommand("export",argument("report",word()).executes(action(SiteStudio::exportSearch))));
        root.then(literal("close").then(argument("token",word()).executes(c->{var p=c.getSource().getPlayerOrException();var all=sessions(c.getSource().getServer());var s=all.get(p.getUUID());if(s!=null&&s.token.toString().equals(getString(c,"token"))){if(s.future!=null)s.future.cancel(true);all.remove(p.getUUID());}return 1;})));
        com.gwsheridan.worldsunwritten.commands.ModCommands.register(event,root);
    }
    @SafeVarargs private static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> sessionCommand(String name,ArgumentBuilder<net.minecraft.commands.CommandSourceStack,?>... fields){ArgumentBuilder<net.minecraft.commands.CommandSourceStack,?> tail=fields[fields.length-1];for(int i=fields.length-2;i>=0;i--){fields[i].then(tail);tail=fields[i];}return literal(name).then(argument("token",word()).then(argument("serial",integer(0)).then(tail)));}
    @FunctionalInterface private interface Action{int run(CommandContext<net.minecraft.commands.CommandSourceStack> c)throws IOException,com.mojang.brigadier.exceptions.CommandSyntaxException;}
    private static Command<net.minecraft.commands.CommandSourceStack> action(Action action){return c->{try{return action.run(c);}catch(IOException|RuntimeException e){c.getSource().sendFailure(Component.literal("Site operation refused: "+e.getMessage()));return 0;}};}
    private static Session current(CommandContext<net.minecraft.commands.CommandSourceStack> c)throws IOException,com.mojang.brigadier.exceptions.CommandSyntaxException{var p=c.getSource().getPlayerOrException();if(!allowed(p))throw new IOException("Current permissions or pack policy do not allow this operation.");var s=sessions(c.getSource().getServer()).get(p.getUUID());if(s==null||!s.token.toString().equals(getString(c,"token"))||s.serial!=getInteger(c,"serial"))throw new IOException("This site view is stale. Reopen it.");if(!s.hash.equals(hash(s.name,s.release)))throw new IOException("The saved preset fingerprint changed.");return s;}
    private static int search(CommandContext<net.minecraft.commands.CommandSourceStack> c)throws IOException,com.mojang.brigadier.exceptions.CommandSyntaxException{
        var s=current(c);var dimension=getId(c,"dimension");var structure=getId(c,"structure");long seed=LongArgumentType.getLong(c,"seed");int x=getInteger(c,"x"),z=getInteger(c,"z"),radius=getInteger(c,"radius");var bounds=new SiteSearch.Bounds(Math.subtractExact(x,radius),Math.subtractExact(z,radius),Math.addExact(x,radius),Math.addExact(z,radius),getInteger(c,"limit"),SiteSearch.MAX_RESULTS);
        if(s.future!=null)s.future.cancel(true);WORKER.purge();int serial=++s.serial;s.touch();s.searchReport=null;var server=c.getSource().getServer();var owner=c.getSource().getPlayerOrException().getUUID();
        var pending=view(s,"pending","Searching native candidates without creating chunks.");pending.putString("dimension",dimension.toString());pending.putString("structure",structure.toString());pending.putString("seed",Long.toString(seed));pending.putInt("chunk_x",x);pending.putInt("chunk_z",z);send(c.getSource().getPlayerOrException(),pending);
        try{s.future=WORKER.submit(()->{CompoundTag report;try{var input=SiteInspector.prepare(s.source,dimension,structure,seed,x,z,server.registryAccess(),server.getStructureTemplateManager());report=SiteSearch.search(input,SiteInspector.placements(s.source,dimension,structure),bounds,s.source.draft().manifest().options().generateStructures());}catch(IOException|RuntimeException e){report=new CompoundTag();report.putString("kind","site");report.putString("status","incomplete");var rows=new ListTag();rows.add(StringTag.valueOf("Search failed: "+Objects.toString(e.getMessage(),"invalid inputs")));report.put("rows",rows);}var result=report;server.execute(()->{
            var player=server.getPlayerList().getPlayer(owner);if(sessions(server).get(owner)!=s||s.serial!=serial||player==null||!allowed(player))return;
            try{if(!s.hash.equals(hash(s.name,s.release)))throw new IOException("The saved preset changed during search.");result.putString("source_name",s.name);result.putString("source_release",s.release);result.putString("source_sha256",s.hash);result.putInt("format",1);if(result.contains("results"))s.searchReport=result.copy();decorate(s,result);send(player,result);}catch(IOException e){send(player,view(s,"rejected",e.getMessage()));}
        });});}catch(RejectedExecutionException e){send(c.getSource().getPlayerOrException(),view(s,"incomplete","The site worker is busy; retry later."));}return 1;
    }
    private static int exportSearch(CommandContext<net.minecraft.commands.CommandSourceStack> c)throws IOException,com.mojang.brigadier.exceptions.CommandSyntaxException{
        var s=current(c);String name=getString(c,"report");if(!name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))throw new IOException("Use a simple report name of at most 64 characters.");if(s.searchReport==null)throw new IOException("Complete a search before exporting its report.");
        Path output=ModpackRuntime.directory().resolve("site-reports").resolve(name+".json").toAbsolutePath().normalize();for(var p=output;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Report paths cannot contain symbolic links.");Files.createDirectories(output.getParent());var report=s.searchReport.copy();report.putString("kind","worldsunwritten_site_search");String json=net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,report).toString();if(json.length()>131072)throw new IOException("Search report exceeds 128 KiB.");Files.writeString(output,json+"\n",java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);c.getSource().sendSuccess(()->Component.literal("Exported search report to config/worldsunwritten/site-reports/"+name+".json"),false);return 1;
    }
    private static void open(ServerPlayer p,String name,String release,String expected)throws IOException{
        if(!name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")||!release.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}"))throw new IOException("Use a valid saved library name and release.");
        String fingerprint=hash(name,release);
        var server=p.level().getServer();var source=PresetStudio.validate(PresetStudio.load(ModpackRuntime.directory(),name,release),server.registryAccess());
        if(!fingerprint.equals(hash(name,release)))throw new IOException("The saved preset changed during validation. Reopen the inspector.");
        if(expected!=null&&(!expected.matches("[a-f0-9]{64}")||!fingerprint.equals(expected)))throw new IOException("The server does not have the identical saved preset. Import that version on the authoring server first.");
        long bytes=524288+source.draft().preset().toString().length()*4L+source.draft().templates().values().stream().mapToLong(v->v.length*4L).sum();var all=sessions(server);
        if(all.size()>=16&&!all.containsKey(p.getUUID())||all.entrySet().stream().filter(e->!e.getKey().equals(p.getUUID())).mapToLong(e->e.getValue().bytes).sum()+bytes>BUDGET)throw new IOException("Site preview sessions exceed the shared 64 MiB working budget.");
        var s=new Session(name,release,fingerprint,source,bytes);var old=all.put(p.getUUID(),s);if(old!=null&&old.future!=null)old.future.cancel(true);send(p,view(s,"ready","Choose a managed dimension, assembly and candidate chunk."));
    }
    private static CompoundTag view(Session s,String status,String text){var data=new CompoundTag();data.putString("kind","site");data.putString("status",status);var rows=new ListTag();rows.add(StringTag.valueOf(text));data.put("rows",rows);decorate(s,data);return data;}
    private static void decorate(Session s,CompoundTag data){data.putString("token",s.token.toString());data.putInt("serial",s.serial);data.putString("name",s.name);data.putString("release",s.release);data.putString("sha256",s.hash);
        if(!data.contains("results")&&s.searchReport!=null&&s.searchReport.getStringOr("dimension","").equals(data.getStringOr("dimension",""))&&s.searchReport.getStringOr("structure","").equals(data.getStringOr("structure",""))&&s.searchReport.getStringOr("seed","").equals(data.getStringOr("seed","")))data.put("results",s.searchReport.getListOrEmpty("results").copy());
        var dims=new ListTag();s.source.preset().createWorldDimensions().dimensions().forEach((id,stem)->{if(stem.generator() instanceof com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator)dims.add(StringTag.valueOf(id.identifier().toString()));});data.put("dimensions",dims);
        var structures=new ListTag();s.source.registries().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).listElements().filter(h->h.value() instanceof ModularStructure).map(h->h.key().identifier().toString()).sorted().limit(256).forEach(id->structures.add(StringTag.valueOf(id)));data.put("structures",structures);
        if(!data.contains("seed"))data.putString("seed",Long.toString(s.source.draft().manifest().options().seed()));
    }
    private static void send(ServerPlayer p,CompoundTag data){Platform.get().sendToPlayer(p,new StudioViewPayload(data));}
    public static void logout(Player actor){if(actor instanceof ServerPlayer p){var all=SESSIONS.get(p.level().getServer());if(all!=null){var s=all.remove(p.getUUID());if(s!=null&&s.future!=null)s.future.cancel(true);}}}
}

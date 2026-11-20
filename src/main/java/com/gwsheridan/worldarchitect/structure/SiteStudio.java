package com.gwsheridan.worldarchitect.structure;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.minecraft.commands.arguments.IdentifierArgument.*;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.gwsheridan.worldarchitect.modpack.*;
import com.gwsheridan.worldarchitect.studio.StudioViewPayload;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Bounded server-owned sessions bind every request to an immutable saved preset. */
public final class SiteStudio {
    private static final long BUDGET=64L*1024*1024;
    private static final Map<MinecraftServer,Map<UUID,Session>> SESSIONS=new WeakHashMap<>();
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),Thread.ofPlatform().daemon().name("worldarchitect-site-preview").factory(),new ThreadPoolExecutor.AbortPolicy());
    private static final class Session {
        final UUID token=UUID.randomUUID();final String name,release,hash;final PresetStudio.Checked source;final long bytes;int serial;long expires;Future<?> future;
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
    public static void register(RegisterCommandsEvent event){
        var root=literal("wa_site").requires(s->ModpackRuntime.developerMode()&&ModpackRuntime.allowStructureLibrary(s.getServer())&&hasPermission(LEVEL_ADMINS).test(s));
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
        root.then(literal("close").then(argument("token",word()).executes(c->{var p=c.getSource().getPlayerOrException();var all=sessions(c.getSource().getServer());var s=all.get(p.getUUID());if(s!=null&&s.token.toString().equals(getString(c,"token"))){if(s.future!=null)s.future.cancel(true);all.remove(p.getUUID());}return 1;})));
        event.getDispatcher().register(root);
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
        var dims=new ListTag();s.source.preset().createWorldDimensions().dimensions().forEach((id,stem)->{if(stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.SolidColumnGenerator)dims.add(StringTag.valueOf(id.identifier().toString()));});data.put("dimensions",dims);
        var structures=new ListTag();s.source.registries().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).listElements().filter(h->h.value() instanceof ModularStructure).map(h->h.key().identifier().toString()).sorted().limit(256).forEach(id->structures.add(StringTag.valueOf(id)));data.put("structures",structures);
        if(!data.contains("seed"))data.putString("seed",Long.toString(s.source.draft().manifest().options().seed()));
    }
    private static void send(ServerPlayer p,CompoundTag data){PacketDistributor.sendToPlayer(p,new StudioViewPayload(data));}
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p){var all=SESSIONS.get(p.level().getServer());if(all!=null){var s=all.remove(p.getUUID());if(s!=null&&s.future!=null)s.future.cancel(true);}}}
}

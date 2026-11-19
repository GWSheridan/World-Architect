package com.gwsheridan.worldarchitect.structure;

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
import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.gwsheridan.worldarchitect.studio.StudioViewPayload;
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
    private static final class Session {
        final UUID token = UUID.randomUUID(); AssemblyDraft draft; int serial, savedSerial, selected; long bytes, expires;
        Session(AssemblyDraft draft) { this.draft = draft; }
    }
    private AssemblyStudio() {}
    public static void register(RegisterCommandsEvent event) {
        var root = literal("wa_assembly").requires(s -> ModpackRuntime.allowStructureLibrary(s.getServer()) && hasPermission(LEVEL_GAMEMASTERS).test(s))
                .executes(run(c -> { show(c.getSource().getPlayerOrException(), null); return 1; }))
                .then(literal("new").then(argument("assembly", id()).then(argument("biome", id()).executes(run(c -> {
                    var player = c.getSource().getPlayerOrException();
                    var draft = AssemblyDraft.save(player.level().getServer(), new AssemblyDraft(getId(c,"assembly"),0,getId(c,"biome"),0,4,true,List.of()), -1);
                    open(player,draft); return 1;
                })))))
                .then(literal("open").then(argument("assembly", id()).executes(run(c -> { var p=c.getSource().getPlayerOrException();open(p,AssemblyDraft.load(p.level().getServer(),getId(c,"assembly")));return 1; }))))
                .then(command("save", serial -> serial.executes(run(c -> {
                    var s=session(c);s.draft=AssemblyDraft.save(c.getSource().getServer(),s.draft,s.draft.revision());s.serial++;s.savedSerial=s.serial;show(c.getSource().getPlayerOrException(),"Draft saved.");return 1;
                }))))
                .then(command("refresh", serial -> serial.executes(run(c -> {session(c);show(c.getSource().getPlayerOrException(),null);return 1;}))))
                .then(command("close", serial -> serial.executes(run(c -> {session(c);logout(c.getSource().getPlayerOrException());return 1;}))))
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
                    c.getSource().sendSuccess(()->Component.literal("Exported assembly datapack to generated/worldarchitect/assembly-exports/"+getId(c,"target")+"-datapack.zip"),false);show(c.getSource().getPlayerOrException(),"Datapack exported. Install the ZIP in a new world or pack.");return 1;
                })))))
                .then(command("bundle", serial -> serial.requires(hasPermission(LEVEL_ADMINS)).then(argument("target",id()).then(argument("dimension",id()).then(argument("release",word()).then(argument("seed",LongArgumentType.longArg()).executes(run(c -> {
                    var s=session(c);var path=AssemblyExports.bundle(c.getSource().getServer(),s.draft,getId(c,"target"),getId(c,"dimension"),getString(c,"release"),LongArgumentType.getLong(c,"seed"),c.getSource().getPlayerOrException().getPlainTextName());
                    c.getSource().sendSuccess(()->Component.literal("Exported assembly preset to config/worldarchitect/exports/"+path.getFileName()),false);show(c.getSource().getPlayerOrException(),"Preset exported: "+path.getFileName());return 1;
                }))))))));
        event.getDispatcher().register(root);
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
        var sessions=SESSIONS.computeIfAbsent(server,unused->new HashMap<>());sessions.values().removeIf(s->s.expires<System.nanoTime());return sessions;
    }
    private static Session session(CommandContext<CommandSourceStack> c) throws CommandSyntaxException,IOException {
        var p=c.getSource().getPlayerOrException();var s=sessions(p.level().getServer()).get(p.getUUID());
        if(s==null||!s.token.toString().equals(getString(c,"token"))||s.serial!=getInteger(c,"serial"))throw new IOException("This editor view is stale. Reopen Assembly Studio.");
        s.expires=System.nanoTime()+1_800_000_000_000L;return s;
    }
    private static void reserve(ServerPlayer player, Session candidate, AssemblyDraft draft) throws IOException {
        long bytes=draft.check().estimatedBytes();var all=sessions(player.level().getServer());
        long total=all.entrySet().stream().filter(e->!e.getKey().equals(player.getUUID())).mapToLong(e->e.getValue().bytes).sum();
        if(total+bytes>SHARED_BUDGET||!all.containsKey(player.getUUID())&&all.size()>=16)throw new IOException("Assembly editor memory is full. Save and close another editing session.");
        candidate.bytes=bytes;candidate.expires=System.nanoTime()+1_800_000_000_000L;
    }
    private static void open(ServerPlayer p,AssemblyDraft draft) throws IOException {var s=new Session(draft);reserve(p,s,draft);sessions(p.level().getServer()).put(p.getUUID(),s);show(p,null);}
    private static void change(CommandContext<CommandSourceStack> c,AssemblyDraft draft,int selected) throws IOException,CommandSyntaxException {
        var s=session(c);var player=c.getSource().getPlayerOrException();reserve(player,s,draft);s.draft=draft;s.serial++;s.selected=selected;show(player,null);
    }
    public static void logout(ServerPlayer player) {var all=SESSIONS.get(player.level().getServer());if(all!=null)all.remove(player.getUUID());}
    private static void show(ServerPlayer player,String status) throws IOException {
        var server=player.level().getServer();var s=sessions(server).get(player.getUUID());var data=new CompoundTag();
        data.putString("kind","assembly");data.putBoolean("active",s!=null);data.putBoolean("can_bundle",hasPermission(LEVEL_ADMINS).test(player.createCommandSourceStack())&&ModpackRuntime.developerMode());
        var library=new ListTag();StructureLibrary.list(server).stream().limit(128).forEach(id->library.add(StringTag.valueOf(id.toString())));data.put("library",library);
        var drafts=new ListTag();AssemblyDraft.list(server).forEach(id->drafts.add(StringTag.valueOf(id.toString())));data.put("drafts",drafts);
        if(s!=null){
            var d=s.draft;data.putString("id",d.id().toString());data.putString("token",s.token.toString());data.putInt("serial",s.serial);data.putInt("revision",d.revision());data.putInt("selected",s.selected);data.putBoolean("dirty",s.serial!=s.savedSerial);
            data.putString("biome",d.biome().toString());data.putInt("y_offset",d.yOffset());data.putInt("slope",d.maxSlope());data.putBoolean("random",d.randomRotation());
            String problem="Assembly valid · templates and markers are embedded snapshots.";boolean valid=true;
            try{d.structure(server);}catch(IOException|RuntimeException e){problem=e.getMessage()==null?"Check assembly settings.":e.getMessage();valid=false;}
            data.putBoolean("valid",valid);data.putString("status",clip(status==null?problem:status,1536));
            var cells=new ArrayList<Integer>();var markers=new ListTag();var modules=new ListTag();var dependencies=new TreeSet<String>();int sx=1,sy=1,sz=1;
            for(var part:d.parts()){
                var entry=part.transformed();var offset=part.offset();var size=entry.template().size();sx=Math.max(sx,offset.getX()+size.getX());sy=Math.max(sy,offset.getY()+size.getY());sz=Math.max(sz,offset.getZ()+size.getZ());
                var module=new CompoundTag();module.putString("name",part.name());module.putString("source",part.source().toString());module.putString("rotation",rotationKey(part.rotation()));module.putString("mirror",mirrorKey(part.mirror()));module.putIntArray("offset",new int[]{offset.getX(),offset.getY(),offset.getZ()});module.putIntArray("size",new int[]{size.getX(),size.getY(),size.getZ()});module.putString("rules",describe(entry.rules()));module.putInt("snapshot_revision",part.original().revision());modules.add(module);
                for(var cell:entry.template().blocks()){
                    var pos=offset.offset(cell.pos());cells.add(pos.getX()|pos.getY()<<7|pos.getZ()<<14);cells.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).toString().hashCode());
                    dependencies.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getNamespace());
                }
                for(var named:entry.markers().entrySet()){
                    var marker=new CompoundTag();var p=offset.offset(named.getValue().pos());marker.putString("name",part.name()+":"+named.getKey());marker.putString("facing",named.getValue().facing().getSerializedName());marker.putIntArray("pos",new int[]{p.getX(),p.getY(),p.getZ()});markers.add(marker);
                }
            }
            dependencies.remove("minecraft");data.putString("dependencies",clip(String.join(", ",dependencies),1024));data.put("modules",modules);data.put("markers",markers);data.putIntArray("cells",cells.stream().mapToInt(Integer::intValue).toArray());data.putIntArray("size",new int[]{sx,sy,sz});
        }
        PacketDistributor.sendToPlayer(player,new StudioViewPayload(data));
    }
    private static String describe(TemplateRules rules){return "Dimension "+rules.dimension().map(Object::toString).orElse("any")+"; biome "+rules.biome().map(Object::toString).orElse("any")+"; Y "+rules.minY()+" to "+rules.maxY()+"; terrain "+rules.terrain().key()+"; collision "+rules.collision().key()+".";}
    private static String clip(String value,int max){return value.substring(0,Math.min(value.length(),max));}
    private static Command<CommandSourceStack> run(Action action){return c->{try{return action.run(c);}catch(IOException|RuntimeException e){c.getSource().sendFailure(Component.literal(clip(e.getMessage()==null?"Assembly action failed.":e.getMessage(),1536)));return 0;}};}
    @FunctionalInterface private interface Action {int run(CommandContext<CommandSourceStack> c)throws IOException,CommandSyntaxException;}
}

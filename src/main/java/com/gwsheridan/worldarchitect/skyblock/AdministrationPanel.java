package com.gwsheridan.worldarchitect.skyblock;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.gwsheridan.worldarchitect.config.IslandProfile;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import com.gwsheridan.worldarchitect.studio.StudioViewPayload;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import java.io.*;
import java.security.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Views are permissioned; confirmation uses fresh ownership and the actor's original command source. */
public final class AdministrationPanel {
    private record Snapshot(int capacity,Map<UUID,Integer> players,Map<String,Integer> teams,WorldAdministration.Settings settings,String teamDigest,Set<String> online) {}
    private record Pending(UUID token,AdministrationAction action,long expires) {}
    private static final class View {
        final UUID token=UUID.randomUUID();final Snapshot snapshot;final int page;final long expires=System.nanoTime()+600_000_000_000L;Pending pending;
        View(Snapshot snapshot,int page){this.snapshot=snapshot;this.page=page;}
    }
    private static final Map<MinecraftServer,Map<UUID,View>> VIEWS=new WeakHashMap<>();
    private AdministrationPanel() {}
    public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(literal("wa_admin").executes(run(c->open(c.getSource().getPlayerOrException(),0)))
                .then(literal("page").then(argument("page",IntegerArgumentType.integer(0,4095)).executes(run(c->open(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"page"))))))
                .then(literal("stage").requires(hasPermission(LEVEL_ADMINS)).then(argument("view",word()).then(argument("action",greedyString()).executes(run(AdministrationPanel::stage)))))
                .then(literal("confirm").requires(hasPermission(LEVEL_ADMINS)).then(argument("view",word()).then(argument("confirmation",word()).executes(run(AdministrationPanel::confirm)))))
                .then(literal("cancel").then(argument("view",word()).executes(run(c->{var p=c.getSource().getPlayerOrException();view(c).pending=null;return open(p,view(c).page);}))))
                .then(literal("close").then(argument("view",word()).executes(run(c->{view(c);logout(c.getSource().getPlayerOrException());return 1;})))));
    }
    private static Map<UUID,View> views(MinecraftServer server){var all=VIEWS.computeIfAbsent(server,unused->new HashMap<>());all.values().removeIf(v->v.expires<System.nanoTime());return all;}
    private static boolean administrator(ServerPlayer player){return hasPermission(LEVEL_ADMINS).test(player.createCommandSourceStack());}
    private static Optional<com.gwsheridan.worldarchitect.api.IslandLayout> islands(MinecraftServer server){return server.overworld().getChunkSource().getGenerator() instanceof VoidGenerator empty?empty.layout().filter(i->i.personalSlots()>0):Optional.empty();}
    private static Set<String> online(MinecraftServer server){return Set.copyOf(Arrays.asList(server.getPlayerNames()));}
    private static Snapshot snapshot(MinecraftServer server)throws IOException{
        int capacity=islands(server).map(com.gwsheridan.worldarchitect.api.IslandLayout::personalSlots).orElse(0);var data=server.getWorldPath(LevelResource.DATA);
        Map<UUID,Integer> players=capacity==0?Map.of():IslandAllocations.players(data,capacity);Map<String,Integer> teams=capacity==0?Map.of():IslandAllocations.teams(data,capacity);
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))){
            var sorted=server.getScoreboard().getPlayerTeams().stream().sorted(Comparator.comparing(net.minecraft.world.scores.PlayerTeam::getName)).toList();out.writeInt(sorted.size());
            for(var team:sorted){write(out,team.getName());var members=team.getPlayers().stream().sorted().toList();out.writeInt(members.size());for(var member:members)write(out,member);}
        }
        return new Snapshot(capacity,players,teams,WorldAdministration.get(server),HexFormat.of().formatHex(digest.digest()),online(server));
    }
    private static void write(DataOutputStream out,String text)throws IOException{byte[] bytes=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    public static int open(ServerPlayer player,int requestedPage)throws IOException{return show(player,requestedPage,"");}
    private static int show(ServerPlayer player,int requestedPage,String status)throws IOException{
        var server=player.level().getServer();boolean admin=administrator(player);var state=snapshot(server);var all=views(server);
        var playerClaims=state.players.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();
        var scoreboardTeams=server.getScoreboard().getPlayerTeams().stream().sorted(Comparator.comparing(net.minecraft.world.scores.PlayerTeam::getName)).toList();
        int pages=Math.max(1,(Math.max(playerClaims.size(),scoreboardTeams.size())+127)/128),page=admin?Math.clamp(requestedPage,0,pages-1):0;
        View view=null;if(admin){if(!all.containsKey(player.getUUID())&&all.size()>=32)throw new IOException("Close another administration panel before opening a new one.");view=new View(state,page);all.put(player.getUUID(),view);}else all.remove(player.getUUID());
        var data=new CompoundTag();data.putString("kind","administration");data.putBoolean("administrator",admin);data.putBoolean("islands",state.capacity>0);data.putString("status",status);
        var occupied = new java.util.BitSet(); state.players.values().forEach(occupied::set); state.teams.values().forEach(occupied::set); int firstFree = occupied.nextClearBit(1); data.putInt("first_free",firstFree<=state.capacity?firstFree:0);
        data.putInt("capacity",state.capacity);data.putInt("used",state.players.size()+state.teams.size());data.putInt("page",page);data.putInt("pages",admin?pages:1);
        data.putInt("personal",state.players.getOrDefault(player.getUUID(),0));String ownTeam=player.getTeam()==null?"":player.getTeam().getName();data.putString("own_team",ownTeam);data.putInt("team_slot",state.teams.getOrDefault(ownTeam,0));
        if(admin){
            data.putString("token",view.token.toString());var rows=new ListTag();
            for(var claim:playerClaims.subList(Math.min(page*128,playerClaims.size()),Math.min((page+1)*128,playerClaims.size()))){var row=new CompoundTag();var online=server.getPlayerList().getPlayer(claim.getKey());row.putString("id",claim.getKey().toString());row.putString("name",online==null?claim.getKey().toString():online.getPlainTextName());row.putInt("slot",claim.getValue());rows.add(row);}data.put("players",rows);
            var teams=new ListTag();for(var team:scoreboardTeams.subList(Math.min(page*128,scoreboardTeams.size()),Math.min((page+1)*128,scoreboardTeams.size()))){var row=new CompoundTag();row.putString("name",team.getName());row.putInt("slot",state.teams.getOrDefault(team.getName(),0));row.putInt("members",team.getPlayers().size());teams.add(row);}data.put("teams",teams);
            var names=new ListTag();state.online.stream().sorted().limit(128).forEach(name->names.add(StringTag.valueOf(name)));data.put("online",names);
            var settings=state.settings;var protection=settings.protection();data.putBoolean("breaking",protection.breaking());data.putBoolean("placement",protection.placement());data.putBoolean("containers",protection.containers());data.putBoolean("shared",protection.shared());
            var blocks=new ListTag();protection.containerBlocks().forEach(block->blocks.add(StringTag.valueOf(block.toString())));data.put("container_blocks",blocks);
            var policies=new ListTag();policies.add(StringTag.valueOf("Initial: "+settings.initial().map(AdministrationPanel::describe).orElse("default")));policies.add(StringTag.valueOf("Respawn: "+settings.respawn().map(AdministrationPanel::describe).orElse("default")));settings.portals().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString))).forEach(e->policies.add(StringTag.valueOf("Portal "+e.getKey()+": "+describe(e.getValue()))));data.put("policies",policies);
        }
        PacketDistributor.sendToPlayer(player,new StudioViewPayload(data));return 1;
    }
    private static String describe(com.gwsheridan.worldarchitect.spawn.SpawnPolicy policy) {
        String text=policy.kind().getSerializedName()+" in "+policy.dimension();
        if(policy.kind()==com.gwsheridan.worldarchitect.spawn.SpawnPolicy.Kind.FIXED)text+=" at "+policy.position().toShortString();
        if(policy.template().isPresent())text+=" · "+policy.template().orElseThrow()+" · "+policy.module()+" "+policy.marker()+" · origin "+policy.position().toShortString();
        return text;
    }
    private static View view(CommandContext<CommandSourceStack> context)throws IOException,CommandSyntaxException{
        var player=context.getSource().getPlayerOrException();var view=views(player.level().getServer()).get(player.getUUID());
        if(view==null||!view.token.toString().equals(getString(context,"view")))throw new IOException("This panel expired. Reopen /wa_admin.");return view;
    }
    private static int stage(CommandContext<CommandSourceStack> context)throws IOException,CommandSyntaxException{
        var player=context.getSource().getPlayerOrException();var view=view(context);
        if(!administrator(player))throw new IOException("Administrator permission is required.");
        if(!view.snapshot.equals(snapshot(player.level().getServer())))return show(player,view.page,"Administration changed. Review the latest state before staging another action.");
        var action=AdministrationAction.parse(getString(context,"action"),online(player.level().getServer()));
        var parsed=player.level().getServer().getCommands().getDispatcher().parse(action.command(),player.createCommandSourceStack());
        if(parsed.getReader().canRead()||!parsed.getExceptions().isEmpty())throw new IOException("The command is unavailable or its fields are invalid.");
        view.pending=new Pending(UUID.randomUUID(),action,System.nanoTime()+60_000_000_000L);
        var data=new CompoundTag();data.putString("kind","administration_confirmation");data.putString("token",view.token.toString());data.putString("confirmation",view.pending.token.toString());data.putString("description",action.description());data.putInt("page",view.page);
        PacketDistributor.sendToPlayer(player,new StudioViewPayload(data));return 1;
    }
    private static int confirm(CommandContext<CommandSourceStack> context)throws IOException,CommandSyntaxException{
        var player=context.getSource().getPlayerOrException();var view=view(context);var pending=view.pending;
        if(!administrator(player))throw new IOException("Administrator permission is required.");
        if(pending==null||pending.expires<System.nanoTime()||!pending.token.toString().equals(getString(context,"confirmation")))throw new IOException("That confirmation expired. Stage the action again.");
        view.pending=null;
        if(!view.snapshot.equals(snapshot(player.level().getServer())))return show(player,view.page,"Confirmation refused: ownership, teams or settings changed. Review and stage again.");
        var action=AdministrationAction.parse(pending.action.command(),online(player.level().getServer()));int result;
        try{result=player.level().getServer().getCommands().getDispatcher().execute(action.command(),player.createCommandSourceStack());}
        catch(CommandSyntaxException e){context.getSource().sendFailure(Component.literal(e.getRawMessage().getString()));return show(player,view.page,"Action refused. Review the message in chat.");}
        IslandProtection.revalidate(player.level().getServer());
        return show(player,view.page,result>0?"Administration change committed.":"No change committed. Review the message in chat.");
    }
    public static void logout(ServerPlayer player){var all=VIEWS.get(player.level().getServer());if(all!=null)all.remove(player.getUUID());}
    private static Command<CommandSourceStack> run(Action action){return context->{try{return action.run(context);}catch(IOException|RuntimeException e){context.getSource().sendFailure(Component.literal(e.getMessage()==null?"Administration panel failed.":e.getMessage()));return 0;}};}
    @FunctionalInterface private interface Action{int run(CommandContext<CommandSourceStack> context)throws IOException,CommandSyntaxException;}
}

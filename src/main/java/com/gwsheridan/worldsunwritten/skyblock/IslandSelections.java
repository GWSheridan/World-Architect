package com.gwsheridan.worldsunwritten.skyblock;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import com.gwsheridan.worldsunwritten.worldgen.*;
import com.gwsheridan.worldsunwritten.structure.IslandBuild;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned choices reserve pre-generated slots; they never replace island blocks. */
public final class IslandSelections {
    private static final Map<MinecraftServer,Map<UUID,View>> VIEWS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Set<UUID>> PENDING=new WeakHashMap<>();
    private record View(String token,String team,String pin,String ledger,long expires){}
    private IslandSelections(){}
    private static Path data(MinecraftServer s){return s.getWorldPath(LevelResource.DATA);}
    private static Map<UUID,View> views(MinecraftServer s){return VIEWS.computeIfAbsent(s,k->new HashMap<>());}
    private static Set<UUID> pending(MinecraftServer s){return PENDING.computeIfAbsent(s,k->new HashSet<>());}
    private static SkyblockGenerator generator(MinecraftServer s){if(s.overworld().getChunkSource().getGenerator() instanceof SkyblockGenerator g)return g;throw new IllegalStateException("This world has no island catalog.");}
    private static String team(MinecraftServer s,SkyblockGenerator g,String name){var t=g.catalog().teams()?s.getScoreboard().getPlayersTeam(name):null;return t==null?"":t.getName();}
    public static int assigned(MinecraftServer s,SkyblockGenerator g,UUID player,String name){
        if(g.catalog().personalSlots()==0)return 0;String team=team(s,g,name);
        return team.isEmpty()?IslandAllocations.assignedSlot(data(s),g.catalog().personalSlots(),player):IslandAllocations.teams(data(s),g.catalog().personalSlots()).getOrDefault(team,0);
    }
    public static int resolveSlot(MinecraftServer s,VoidGenerator g,UUID player,String name,boolean teamPolicy){
        var layout=g.layout().orElseThrow();if(layout.personalSlots()==0)return 0;
        String teamName;
        if(g instanceof SkyblockGenerator rich){
            int assigned=assigned(s,rich,player,name);if(assigned>0||rich.catalog().mode().equals("choice"))return assigned;
            teamName=team(s,rich,name);
        }else {var t=teamPolicy?s.getScoreboard().getPlayersTeam(name):null;teamName=t==null?"":t.getName();}
        return teamName.isEmpty()?IslandAllocations.claim(data(s),layout.personalSlots(),player):IslandAllocations.claimTeam(data(s),layout.personalSlots(),teamName);
    }
    public static boolean pending(ServerPlayer p){return pending(p.level().getServer()).contains(p.getUUID());}
    public static void register(RegisterCommandsEvent e){
        com.gwsheridan.worldsunwritten.commands.ModCommands.register(e,literal("island").then(literal("choose").executes(c->safeOpen(c.getSource().getPlayerOrException(),""))
                .then(argument("token",word()).then(argument("catalog",word()).then(argument("type",word()).executes(c->{var p=c.getSource().getPlayerOrException();try{return choose(p,getString(c,"token"),getString(c,"catalog"),getString(c,"type"));}catch(RuntimeException error){p.sendSystemMessage(Component.literal(Objects.toString(error.getMessage(),"Island selection failed.")));return safeOpen(p,"Refresh the available island types.");}}))))));
    }
    private static int safeOpen(ServerPlayer player,String status){try{return open(player,status);}catch(RuntimeException error){player.sendSystemMessage(Component.literal(Objects.toString(error.getMessage(),"Island selection is unavailable.")));return 0;}}
    private static String ledger(MinecraftServer server){try{return com.gwsheridan.worldsunwritten.modpack.PresetInheritance.fingerprint(Files.readAllBytes(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(data(server), "-islands-v1.dat")));}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}}
    public static int open(ServerPlayer p,String status){
        var s=p.level().getServer();var g=generator(s);
        if(!g.catalog().mode().equals("choice")||g.catalog().personalSlots()==0)throw new IllegalStateException("This pack does not offer island selection.");
        if(assigned(s,g,p.getUUID(),p.getPlainTextName())>0){p.sendSystemMessage(Component.literal("Your player or team already has an island. Use /worldsunwritten island home."));return 0;}
        var all=views(s);all.values().removeIf(v->v.expires<System.nanoTime());if(!all.containsKey(p.getUUID())&&all.size()>=256)throw new IllegalStateException("Island selection is busy. Try again shortly.");
        long seed=s.getWorldGenSettings().options().seed();String pin=IslandAllocations.catalogPin(g.catalog(),seed);String token=UUID.randomUUID().toString();
        all.put(p.getUUID(),new View(token,team(s,g,p.getPlainTextName()),pin,ledger(s),System.nanoTime()+300_000_000_000L));pending(s).add(p.getUUID());
        var available=IslandAllocations.availableTypes(data(s),g.catalog().personalSlots());var root=new CompoundTag();root.putString("kind","island_choice");root.putBoolean("team_mode",g.catalog().teams());root.putString("token",token);root.putString("catalog",pin);root.putString("status",status);var entries=new ListTag();
        for(var type:g.catalog().entries()){
            var row=new CompoundTag();row.putString("id",type.id());row.putString("title",type.title());row.putString("description",type.description());row.putString("icon",type.icon());row.putInt("available",available.getOrDefault(type.id(),0));
            row.putIntArray("size",new int[]{type.build().size().getX(),type.build().size().getY(),type.build().size().getZ()});row.putIntArray("cells",preview(type.build()));row.putIntArray("states",previewStates(type.build()));entries.add(row);
        }
        root.put("types",entries);PacketDistributor.sendToPlayer(p,new StudioViewPayload(root));return 1;
    }
    /** Preview data omits inventories, sign text and all other block-entity metadata. */
    public static int[] preview(IslandBuild build){
        var cells=build.cells();int stride=Math.max(1,(cells.size()+4095)/4096);var result=new ArrayList<Integer>();
        for(int i=0;i<cells.size();i+=stride){var c=cells.get(i);var pos=c.pos();result.add(pos.getX()|pos.getY()<<7|pos.getZ()<<14);int color=c.state().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).col;result.add(color==0?0x92a4a7:color);}
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
    private static int[] previewStates(IslandBuild build){var cells=build.cells();int stride=Math.max(1,(cells.size()+4095)/4096);var result=new ArrayList<Integer>();for(int i=0;i<cells.size();i+=stride)result.add(net.minecraft.world.level.block.Block.getId(cells.get(i).state()));return result.stream().mapToInt(Integer::intValue).toArray();}
    public static int choose(ServerPlayer p,String token,String pin,String type){
        var s=p.level().getServer();var g=generator(s);var view=views(s).get(p.getUUID());
        if(view==null||!view.token.equals(token)||!view.pin.equals(pin)||view.expires<System.nanoTime()||!view.team.equals(team(s,g,p.getPlainTextName()))||!view.ledger.equals(ledger(s)))throw new IllegalStateException("The island list changed. Select from a refreshed list.");
        if(!g.catalog().mode().equals("choice")||g.catalog().entries().stream().noneMatch(e->e.id().equals(type)))throw new IllegalStateException("That island type is not available.");
        int slot=IslandAllocations.claimType(data(s),g.catalog().personalSlots(),p.getUUID(),view.team,type);
        views(s).remove(p.getUUID());pending(s).remove(p.getUUID());home(p,g,slot);return slot;
    }
    private static void home(ServerPlayer p,SkyblockGenerator g,int slot){
        var s=p.level().getServer();var pos=IslandSpawns.position(s.overworld(),g.layout().orElseThrow(),slot).orElseThrow(()->new SkyblockJoinException("The island has no safe spawn. Ask an administrator to repair its marker."));
        if(!p.teleportTo(s.overworld(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),0,0,true))throw new IllegalStateException("The server refused island travel.");
        if(slot>0){var done=new CompoundTag();done.putString("kind","island_choice_done");PacketDistributor.sendToPlayer(p,new StudioViewPayload(done));}
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.level().getServer().overworld().getChunkSource().getGenerator() instanceof SkyblockGenerator g&&g.catalog().mode().equals("choice")&&assigned(p.level().getServer(),g,p.getUUID(),p.getPlainTextName())==0){pending(p.level().getServer()).add(p.getUUID());safeOpen(p,"");}}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){var s=p.level().getServer();views(s).remove(p.getUUID());pending(s).remove(p.getUUID());}}
    public static void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!pending(p)||p.tickCount%20!=0)return;
        var s=p.level().getServer();var g=generator(s);int slot=assigned(s,g,p.getUUID(),p.getPlainTextName());
        if(slot>0){pending(s).remove(p.getUUID());views(s).remove(p.getUUID());home(p,g,slot);return;}
        var origin=g.layout().orElseThrow().spawn(0);
        if(p.level()!=s.overworld()||Math.abs(p.getX()-origin.getX())>g.catalog().spacing()/2.0||Math.abs(p.getZ()-origin.getZ())>g.catalog().spacing()/2.0||p.getY()<g.catalog().originY()-8)home(p,g,0);
    }
}

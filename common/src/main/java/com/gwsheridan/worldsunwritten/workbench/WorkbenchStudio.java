package com.gwsheridan.worldsunwritten.workbench;

import net.minecraft.world.entity.player.Player;
import com.gwsheridan.worldsunwritten.platform.Platform;
import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import com.google.gson.JsonParser;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/** Sessions bind a client-owned detached scenario to one immutable server library version. */
public final class WorkbenchStudio {
    private record Session(UUID token,WorkbenchAuthoring.Source source,long expires){}
    private static final Map<MinecraftServer,Map<UUID,Session>> SESSIONS=new WeakHashMap<>();
    private WorkbenchStudio(){}
    private static Map<UUID,Session> sessions(MinecraftServer server){var all=SESSIONS.computeIfAbsent(server,k->new HashMap<>());all.values().removeIf(s->s.expires<System.nanoTime());return all;}
    public static void commands(LiteralArgumentBuilder<CommandSourceStack> root){
        if(!ModpackRuntime.developerMode())return;
        root.then(literal("studio").then(argument("name",word()).then(argument("release",word()).executes(c->{
            try{
                var p=c.getSource().getPlayerOrException();var server=c.getSource().getServer();var all=sessions(server);
                if(all.size()>=16&&!all.containsKey(p.getUUID()))throw new IOException("Workbench editor sessions are full.");
                var source=WorkbenchAuthoring.source(getString(c,"name"),getString(c,"release"),server.registryAccess());var session=new Session(UUID.randomUUID(),source,System.nanoTime()+1_800_000_000_000L);all.put(p.getUUID(),session);
                var data=new CompoundTag();data.putString("kind","workbench");data.putString("token",session.token.toString());data.putInt("serial",0);data.putString("scenario",source.scenario().toString());
                Platform.get().sendToPlayer(p,new StudioViewPayload(data));return 1;
            }catch(IOException|RuntimeException e){c.getSource().sendFailure(Component.literal("Cannot open workbench: "+e.getMessage()));return 0;}
        }))));
    }
    public static void handle(ServerPlayer player,WorkbenchEditPayload input){
        boolean success=false;String message;
        try{
            var server=player.level().getServer();
            var all=sessions(server);var session=all.get(player.getUUID());
            if(input.operation().equals("close")&&session!=null&&session.token.toString().equals(input.token())&&input.serial()==0){all.remove(player.getUUID());return;}
            if(!ModpackRuntime.developerMode()||!hasPermission(LEVEL_OWNERS).test(player.createCommandSourceStack()))throw new IOException("Workbench authoring requires developer mode and permission level 4.");
            if(session==null||!session.token.toString().equals(input.token())||input.serial()!=0)throw new IOException("This workbench session is stale. Reopen it.");
            if(input.operation().equals("close")){all.remove(player.getUUID());return;}
            if(!input.operation().equals("export")||input.document().getBytes(StandardCharsets.UTF_8).length>WorkbenchScenario.MAX_BYTES)throw new IOException("Invalid workbench request.");
            var path=WorkbenchAuthoring.export(session.source,input.name(),JsonParser.parseString(input.document()),server.registryAccess());
            all.put(player.getUUID(),new Session(session.token,session.source,System.nanoTime()+1_800_000_000_000L));success=true;message="Exported "+path.getFileName()+" and its bound preset bundle to config/worldsunwritten/exports.";
        }catch(IOException|RuntimeException e){message="Workbench export refused: "+e.getMessage();}
        var data=new CompoundTag();data.putString("kind","workbench_export");data.putString("token",input.token());data.putBoolean("success",success);data.putString("status",message.substring(0,Math.min(message.length(),1000)));
        Platform.get().sendToPlayer(player,new StudioViewPayload(data));
    }
    public static void logout(Player actor){if(actor instanceof ServerPlayer p){var all=SESSIONS.get(p.level().getServer());if(all!=null)all.remove(p.getUUID());}}
}

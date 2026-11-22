package com.gwsheridan.worldsunwritten.skyblock;

import static net.minecraft.commands.Commands.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.gwsheridan.worldsunwritten.structure.*;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class IslandCaptures {
    private record Selection(ResourceKey<Level> dimension,BlockPos first,BlockPos second){}
    private static final Map<MinecraftServer,Map<UUID,Selection>> SELECTIONS=new WeakHashMap<>();
    private IslandCaptures(){}
    private static Map<UUID,Selection> selections(MinecraftServer s){return SELECTIONS.computeIfAbsent(s,k->new HashMap<>());}
    public static void allow(ServerPlayer player){if(!ModpackRuntime.developerMode()||!hasPermission(LEVEL_ADMINS).test(player.createCommandSourceStack()))throw new IllegalStateException("Island authoring requires server developer mode and administrator permission.");}
    public static void corner(ServerPlayer player,boolean first){
        allow(player);var values=selections(player.level().getServer());var old=values.get(player.getUUID());var pos=player.blockPosition().immutable();
        if(old!=null&&!old.dimension.equals(player.level().dimension()))old=null;
        values.put(player.getUUID(),new Selection(player.level().dimension(),first?pos:old==null?null:old.first,first?old==null?null:old.second:pos));
        player.sendSystemMessage(Component.literal((first?"First":"Second")+" island corner saved at "+pos.toShortString()+". Reopen /worldsunwritten island studio to capture."));
    }
    public static IslandBuild capture(ServerPlayer player,boolean contents)throws IOException{
        allow(player);var selection=selections(player.level().getServer()).get(player.getUUID());if(selection==null||selection.first==null||selection.second==null||!selection.dimension.equals(player.level().dimension()))throw new IOException("Set both island corners in the same dimension first.");
        return IslandAssets.capture(player.level(),selection.first,selection.second,contents);
    }
    public static void register(RegisterCommandsEvent event){
        if(!ModpackRuntime.developerMode())return;
        var root=literal("island");
        root.then(literal("studio").requires(hasPermission(LEVEL_ADMINS)).executes(c->{var p=c.getSource().getPlayerOrException();try{allow(p);var data=new CompoundTag();data.putString("kind","island_editor");PacketDistributor.sendToPlayer(p,new StudioViewPayload(data));return 1;}catch(RuntimeException error){c.getSource().sendFailure(Component.literal(error.getMessage()));return 0;}}));
        for(boolean first:new boolean[]{true,false})root.then(literal(first?"pos1":"pos2").requires(hasPermission(LEVEL_ADMINS)).executes(c->{try{corner(c.getSource().getPlayerOrException(),first);return 1;}catch(RuntimeException error){c.getSource().sendFailure(Component.literal(error.getMessage()));return 0;}}));
        root.then(literal("capture_selected").requires(hasPermission(LEVEL_ADMINS)).then(argument("name",word()).then(argument("release",word()).then(argument("contents",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{try{var p=c.getSource().getPlayerOrException();var output=IslandAssets.save(ModpackRuntime.directory(),getString(c,"name"),getString(c,"release"),capture(p,com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"contents")),p.level().getServer().registryAccess());c.getSource().sendSuccess(()->Component.literal("Saved island revision "+output.getFileName()),false);return 1;}catch(IOException|RuntimeException error){c.getSource().sendFailure(Component.literal(error.getMessage()));return 0;}})))));
        com.gwsheridan.worldsunwritten.commands.ModCommands.register(event,root);
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)selections(p.level().getServer()).remove(p.getUUID());}
}

package com.gwsheridan.worldsunwritten.testing;

import com.mojang.authlib.GameProfile;
import com.gwsheridan.worldsunwritten.modpack.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.*;
import net.neoforged.neoforge.common.util.FakePlayer;

final class Cycle17AuthoringAcceptance {
    private static final class Author extends FakePlayer{
        PermissionSet granted=LevelBasedPermissionSet.OWNER;
        Author(MinecraftServer server){super(server.overworld(),new GameProfile(UUID.fromString("e418cb63-e6e2-416c-91d7-14a4c524ff57"),"PresetAuthor"));}
        @Override public PermissionSet permissions(){return granted==null?PermissionSet.NO_PERMISSIONS:granted;}
    }
    private static int command(MinecraftServer server,Author p,String text){try{return server.getCommands().getDispatcher().execute(text,p.createCommandSourceStack());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return 0;}}
    static void check(MinecraftServer server)throws Exception{
        var p=new Author(server);var directory=ModpackRuntime.directory();String derive="wa_preset derive reusable_base 1.7.0 permission_child 1.7.0";
        for(var permission:List.of(PermissionSet.NO_PERMISSIONS,LevelBasedPermissionSet.GAMEMASTER)){p.granted=permission;try{com.gwsheridan.worldsunwritten.api.PresetApi.derive(p.createCommandSourceStack(),"reusable_base","1.7.0",PresetStudio.fingerprint(directory,"reusable_base","1.7.0"),"api_denied","1.7.0");throw new AssertionError("API bypassed administrator permissions");}catch(SecurityException expected){}require(command(server,p,derive)==0,"Non-owner derived a preset");}
        require(PresetStudio.list(directory).stream().noneMatch(r->r.name().equals("permission_child")),"Denied command wrote a revision");p.granted=LevelBasedPermissionSet.OWNER;require(command(server,p,derive)==1,"Owner could not derive a preset");
        String before=PresetStudio.fingerprint(directory,"permission_child","1.7.0");require(command(server,p,"wa_preset override permission_child 1.7.0 denied_revision \"/dimensions/minecraft:overworld/generator/profile/material\" \"minecraft:gold_block\"")==0,"Command weakened inherited terrain lock");require(PresetStudio.list(directory).stream().noneMatch(r->r.release().equals("denied_revision"))&&before.equals(PresetStudio.fingerprint(directory,"permission_child","1.7.0")),"Rejected command changed the library");
        require(command(server,p,"wa_preset override permission_child 1.7.0 1.7.1 \"/dimensions/minecraft:overworld/generator/dimension_profile/content/structures/0/spacing\" 22")==1,"Allowed inherited structure override failed");
        var child=PresetStudio.load(directory,"permission_child","1.7.0");var source=p.createCommandSourceStack();var failure=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{com.gwsheridan.worldsunwritten.api.PresetApi.override(source,child,"/dimensions",new com.google.gson.JsonObject(),false);return false;}catch(IllegalStateException expected){return true;}catch(Exception unexpected){throw new IllegalStateException(unexpected);}}).join();require(failure,"API accepted preset mutation from a worker thread");System.out.println("WA17_INHERITANCE_PERMISSIONS_IMMUTABLE_COMMANDS_PASS");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

package com.gwsheridan.worldsunwritten.testing;

import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;

/** Explicit test identities cover command guards; real-client delivery is checked separately. */
final class Cycle15AuthoringAcceptance {
    private static final class Author extends FakePlayer {
        PermissionSet granted=LevelBasedPermissionSet.OWNER;
        Author(MinecraftServer server){super(server.overworld(),new GameProfile(UUID.fromString("9875bfce-b78c-41ed-98de-159469bafc35"),"SiteAuthor"));}
        @Override public PermissionSet permissions(){return granted==null?PermissionSet.NO_PERMISSIONS:granted;}
    }
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static Object session(MinecraftServer server,Author p)throws Exception{var f=Class.forName("com.gwsheridan.worldsunwritten.structure.SiteStudio").getDeclaredField("SESSIONS");f.setAccessible(true);return ((Map<?,?>)((Map<?,?>)f.get(null)).get(server)).get(p.getUUID());}
    private static int command(MinecraftServer server,Author p,String text){try{return server.getCommands().getDispatcher().execute(text,p.createCommandSourceStack());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return 0;}}
    static void check(MinecraftServer server)throws Exception{
        var p=new Author(server);String open="wa_site studio vertical_pool 1.5.0";
        for(var permission:List.of(PermissionSet.NO_PERMISSIONS,LevelBasedPermissionSet.GAMEMASTER)){p.granted=permission;require(command(server,p,open)==0,"A non-administrator opened site authoring");}
        p.granted=LevelBasedPermissionSet.OWNER;require(command(server,p,open)==1,"Administrator could not open site authoring");var s=session(server,p);String token=field(s,"token").toString();
        require(command(server,p,"wa_site studio ../vertical_pool 1.5.0")==0&&session(server,p)==s,"Unsafe source name replaced the active session");
        require(command(server,p,open+" "+"0".repeat(64))==0&&session(server,p)==s,"Mismatched client fingerprint replaced the active site source");
        String fields=" minecraft:overworld validation:vertical_tower 18 32 32";
        require(command(server,p,"wa_site request "+UUID.randomUUID()+" 0"+fields)==0&&(int)field(s,"serial")==0,"Stale token advanced a site request");
        require(command(server,p,"wa_site request "+token+" 1"+fields)==0&&(int)field(s,"serial")==0,"Stale serial advanced a site request");
        require(command(server,p,"wa_site request "+token+" 0"+fields)==1&&(int)field(s,"serial")==1,"Valid site request failed");var first=(Future<?>)field(s,"future");
        p.granted=LevelBasedPermissionSet.GAMEMASTER;require(command(server,p,"wa_site request "+token+" 1"+fields)==0&&(int)field(s,"serial")==1,"Revoked permission submitted a site request");
        p.granted=LevelBasedPermissionSet.OWNER;require(command(server,p,"wa_site request "+token+" 1"+fields)==1,"Replacement site request failed");require(first.isDone()||first.isCancelled(),"Replaced site work remained active");
        Path archive=ModpackRuntime.directory().resolve("studio/vertical_pool/1.5.0.zip");byte[] before=Files.readAllBytes(archive);
        try{Files.write(archive,new byte[]{42},StandardOpenOption.APPEND);require(command(server,p,"wa_site request "+token+" 2"+fields)==0&&(int)field(s,"serial")==2,"Changed saved source fingerprint was accepted");}finally{Files.write(archive,before);}
        require(command(server,p,"wa_site close "+token)==1&&session(server,p)==null,"Closing site authoring retained its session");require(command(server,p,"wa_site request "+token+" 2"+fields)==0,"Closed site session submitted work");
        System.out.println("WA15_SITE_PERMISSIONS_TOKENS_SERIALS_FINGERPRINT_CANCEL_PASS");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

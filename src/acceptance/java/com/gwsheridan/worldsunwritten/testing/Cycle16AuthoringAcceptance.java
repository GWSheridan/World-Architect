package com.gwsheridan.worldsunwritten.testing;

import com.mojang.authlib.GameProfile;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.*;
import net.neoforged.neoforge.common.util.FakePlayer;

final class Cycle16AuthoringAcceptance {
    private static final class Author extends FakePlayer{PermissionSet granted=LevelBasedPermissionSet.OWNER;Author(MinecraftServer server){super(server.overworld(),new GameProfile(UUID.fromString("0875bfce-b78c-41ed-98de-159469bafc36"),"SearchAuthor"));}@Override public PermissionSet permissions(){return granted==null?PermissionSet.NO_PERMISSIONS:granted;}}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static Object session(MinecraftServer server,Author p)throws Exception{var f=Class.forName("com.gwsheridan.worldsunwritten.structure.SiteStudio").getDeclaredField("SESSIONS");f.setAccessible(true);return ((Map<?,?>)((Map<?,?>)f.get(null)).get(server)).get(p.getUUID());}
    private static int command(MinecraftServer server,Author p,String command){try{return server.getCommands().getDispatcher().execute(command,p.createCommandSourceStack());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return 0;}}
    static void check(MinecraftServer server,CompoundTag report)throws Exception{var p=new Author(server);require(command(server,p,"wa_site studio prepared_house 1.6.0")==1,"Search studio failed to open");var s=session(server,p);String token=field(s,"token").toString(),prefix="wa_site search "+token+" ",fields=" minecraft:overworld validation:prepared_house 18 0 0 64 64";
        require(command(server,p,prefix+"1"+fields)==0&&(int)field(s,"serial")==0,"Stale search serial accepted");require(command(server,p,prefix+"0"+fields)==1&&(int)field(s,"serial")==1,"Search command failed");var first=(Future<?>)field(s,"future");require(command(server,p,"wa_site cancel "+token+" 1 search")==1&&(int)field(s,"serial")==2,"Search cancellation failed");require(first.isCancelled()||first.isDone(),"Cancelled search retained worker work");
        var f=s.getClass().getDeclaredField("searchReport");f.setAccessible(true);var exported=report.copy();exported.putString("source_sha256",field(s,"hash").toString());f.set(s,exported);var path=ModpackRuntime.directory().resolve("site-reports/validation-search.json");require(command(server,p,"wa_site export "+token+" 2 ../invalid")==0&&!Files.exists(path),"Unsafe report filename accepted");p.granted=LevelBasedPermissionSet.GAMEMASTER;require(command(server,p,"wa_site export "+token+" 2 validation-search")==0&&!Files.exists(path),"Revoked search permission exported a report");p.granted=LevelBasedPermissionSet.OWNER;require(command(server,p,"wa_site export "+token+" 2 validation-search")==1,"Search report export failed");byte[] before=Files.readAllBytes(path);require(command(server,p,"wa_site export "+token+" 2 validation-search")==0&&Arrays.equals(before,Files.readAllBytes(path)),"Export overwrote a saved report");String json=Files.readString(path);require(json.contains("worldsunwritten_site_search")&&json.contains(field(s,"hash").toString())&&!json.contains(token),"Report omitted its source or exposed a session token");
        Path archive=ModpackRuntime.directory().resolve("studio/prepared_house/1.6.0.zip");byte[] source=Files.readAllBytes(archive);try{Files.write(archive,new byte[]{42},StandardOpenOption.APPEND);require(command(server,p,"wa_site export "+token+" 2 changed")==0&&command(server,p,prefix+"2"+fields)==0,"Changed source fingerprint permitted search or export");}finally{Files.write(archive,source);}require(command(server,p,"wa_site close "+token)==1&&session(server,p)==null,"Search session remained after close");require(command(server,p,prefix+"2"+fields)==0,"Closed search accepted work");System.out.println("WA16_SEARCH_PERMISSIONS_CANCEL_EXPORT_FINGERPRINT_PASS");}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

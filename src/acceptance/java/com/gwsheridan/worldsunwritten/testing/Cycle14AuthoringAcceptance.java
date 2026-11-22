package com.gwsheridan.worldsunwritten.testing;

import static com.gwsheridan.worldsunwritten.testing.AssemblyAcceptance.*;
import com.google.gson.*;
import com.gwsheridan.worldsunwritten.authoring.*;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.gwsheridan.worldsunwritten.structure.*;
import com.gwsheridan.worldsunwritten.workbench.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Server assertions use explicit test identities; real connected-player checks run separately. */
final class Cycle14AuthoringAcceptance {
    private static final UUID OWNER=UUID.fromString("54f931b2-0d30-44dd-8d25-7c02054f8014");
    private static final Identifier ID=Identifier.parse("validation:history");
    private static final class Author extends FakePlayer {
        PermissionSet granted=LevelBasedPermissionSet.OWNER;
        Author(MinecraftServer server,UUID id,String name){super(server.overworld(),new GameProfile(id,name));}
        @Override public PermissionSet permissions(){return granted==null?PermissionSet.NO_PERMISSIONS:granted;}
    }
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static Object session(MinecraftServer server,Author player,String type)throws Exception{var clazz=Class.forName(type);var f=clazz.getDeclaredField("SESSIONS");f.setAccessible(true);return ((Map<?,?>)((Map<?,?>)f.get(null)).get(server)).get(player.getUUID());}
    private static int command(MinecraftServer server,Author player,String text){try{return server.getCommands().getDispatcher().execute(text,player.createCommandSourceStack());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return 0;}}
    private static String edit(MinecraftServer server,Author p,String operation)throws Exception{Object s=session(server,p,"com.gwsheridan.worldsunwritten.structure.AssemblyStudio");return "wa_assembly "+operation+" "+field(s,"token")+" "+field(s,"serial");}
    private static Path recovery(MinecraftServer server){return server.getWorldPath(LevelResource.DATA).resolve("worldsunwritten/assembly-recovery").resolve(DraftRecovery.fingerprint(OWNER+"/"+ID)+".json");}
    static void fresh(MinecraftServer server)throws Exception{
        var owner=new Author(server,OWNER,"HistoryAuthor");var template=new NativeTemplate(new BlockPos(1,3,1),List.of(new NativeTemplate.Cell(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),null)));var entry=stat("LibraryEntry","read",template,new CompoundTag(),"History","Validation");
        var draft=make("AssemblyDraft",ID,0,Identifier.withDefaultNamespace("plains"),0,4,true,List.of(part("one",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry)));stat("AssemblyDraft","save",server,draft,-1);
        owner.granted=PermissionSet.NO_PERMISSIONS;require(command(server,owner,"wa_assembly open "+ID)==0,"Non-operator opened authoring");owner.granted=LevelBasedPermissionSet.OWNER;
        require(command(server,owner,"wa_assembly open "+ID)==1,"Cannot open server authoring");String stale=edit(server,owner,"settings")+" minecraft:plains 3 4 true";
        require(command(server,owner,stale)==1,"Edit failed");require(command(server,owner,stale)==0,"Stale edit succeeded");
        require(command(server,owner,edit(server,owner,"undo"))==1&&y(server,owner)==0,"Server undo failed");require(command(server,owner,edit(server,owner,"redo"))==1&&y(server,owner)==3,"Server redo failed");
        byte[] recovered=Files.readAllBytes(recovery(server));AssemblyStudio.logout(owner);require(Arrays.equals(recovered,Files.readAllBytes(recovery(server))),"Disconnect removed recovery");
        require(command(server,owner,"wa_assembly open "+ID)==1,"Cannot reopen interrupted editor");Object s=session(server,owner,"com.gwsheridan.worldsunwritten.structure.AssemblyStudio");require((boolean)field(s,"recoveryPending")&&y(server,owner)==0,"Recovery auto-applied or disappeared");require(command(server,owner,edit(server,owner,"save"))==0,"Unresolved recovery was discarded by saving");
        require(command(server,owner,edit(server,owner,"recover"))==1&&y(server,owner)==3,"Explicit recovery failed");require((int)call(stat("AssemblyDraft","load",server,ID),"yOffset")==0,"Recovery wrote the saved draft");
        Object original=stat("AssemblyDraft","load",server,ID);stat("AssemblyDraft","save",server,original,0);byte[] before=Files.readAllBytes(recovery(server));require(command(server,owner,edit(server,owner,"undo"))==0,"Undo bypassed a concurrent saved revision");require(Arrays.equals(before,Files.readAllBytes(recovery(server))),"Refused undo rewrote recovery");
        require(command(server,owner,"wa_assembly open "+ID)==1,"Cannot reopen latest revision");require(command(server,owner,edit(server,owner,"settings")+" minecraft:plains 6 4 true")==1,"Second edit failed");require(command(server,owner,edit(server,owner,"save"))==1,"Save failed");
        require(command(server,owner,edit(server,owner,"undo"))==1&&y(server,owner)==0,"Undo after save rolled back revision metadata");require(command(server,owner,edit(server,owner,"save"))==1,"Saving after undo lost optimistic revision protection");
        require(command(server,owner,edit(server,owner,"settings")+" minecraft:plains 7 4 true")==1,"Recovery seed edit failed");
        owner.granted=PermissionSet.NO_PERMISSIONS;require(command(server,owner,edit(server,owner,"undo"))==0,"Revoked permissions retained history mutation");owner.granted=LevelBasedPermissionSet.OWNER;
        AssemblyStudio.logout(owner);workbench(server,owner);
        // Leave the last unsaved offset available for the next server process.
        require(Files.exists(recovery(server)),"Persistent recovery is missing");
        System.out.println("WA_HISTORY_RECOVERY_STALE_PERMISSIONS_WORKBENCH_PASS");
    }
    private static int y(MinecraftServer server,Author p)throws Exception{return (int)call(field(session(server,p,"com.gwsheridan.worldsunwritten.structure.AssemblyStudio"),"draft"),"yOffset");}
    private static void workbench(MinecraftServer server,Author owner)throws Exception{
        var source=WorkbenchAuthoring.source("inspection_fixture","one",server.registryAccess());var doc=source.scenario().deepCopy();String expected=doc.toString();
        require(command(server,owner,"wa_workbench studio inspection_fixture one")==1,"Server workbench did not open");Object session=session(server,owner,"com.gwsheridan.worldsunwritten.workbench.WorkbenchStudio");String token=String.valueOf(call(session,"token"));
        Path exported=ModpackRuntime.directory().resolve("exports/history-workbench.scenario.json");
        owner.granted=LevelBasedPermissionSet.GAMEMASTER;WorkbenchStudio.handle(owner,new WorkbenchEditPayload(token,0,"export","history-workbench",expected));require(!Files.exists(exported),"Permission level 2 exported a workbench scenario");owner.granted=LevelBasedPermissionSet.OWNER;
        WorkbenchStudio.handle(owner,new WorkbenchEditPayload(UUID.randomUUID().toString(),0,"export","history-workbench",expected));require(!Files.exists(exported),"Stale workbench token exported data");
        WorkbenchStudio.handle(owner,new WorkbenchEditPayload(token,1,"export","history-workbench",expected));require(!Files.exists(exported),"Stale workbench serial exported data");
        var changed=doc.deepCopy();changed.getAsJsonObject("bundle").addProperty("sha256","0".repeat(64));WorkbenchStudio.handle(owner,new WorkbenchEditPayload(token,0,"export","history-workbench",changed.toString()));require(!Files.exists(exported),"Changed bundle binding exported data");
        WorkbenchStudio.handle(owner,new WorkbenchEditPayload(token,0,"export","history-workbench",expected));require(Files.exists(exported),"Valid workbench export failed");byte[] before=Files.readAllBytes(exported);
        WorkbenchStudio.handle(owner,new WorkbenchEditPayload(token,0,"export","history-workbench",expected));require(Arrays.equals(before,Files.readAllBytes(exported)),"Duplicate scenario export overwrote data");
        require(WorkbenchScenario.validate(JsonParser.parseString(Files.readString(exported))).equals(doc),"Exported scenario changed inputs");
        Path revision=ModpackRuntime.directory().resolve("studio/inspection_fixture/one.zip");byte[] original=Files.readAllBytes(revision);
        try{Files.write(revision,new byte[]{42},StandardOpenOption.APPEND);boolean refused=false;try{WorkbenchAuthoring.export(source,"tampered-workbench",doc,server.registryAccess());}catch(java.io.IOException e){refused=true;}require(refused&&!Files.exists(exported.resolveSibling("tampered-workbench.scenario.json")),"Changed archive fingerprint was accepted");}finally{Files.write(revision,original);}
        WorkbenchStudio.handle(owner,new WorkbenchEditPayload(token,0,"close","","{}"));WorkbenchStudio.handle(owner,new WorkbenchEditPayload(token,0,"export","closed-workbench",expected));require(!Files.exists(exported.resolveSibling("closed-workbench.scenario.json")),"Closed workbench session exported data");
    }
    static void restart(MinecraftServer server)throws Exception{
        var owner=new Author(server,OWNER,"HistoryAuthor");require(command(server,owner,"wa_assembly open "+ID)==1,"Persistent history draft did not reopen");var s=session(server,owner,"com.gwsheridan.worldsunwritten.structure.AssemblyStudio");require((boolean)field(s,"recoveryPending")&&y(server,owner)==0,"Recovery failed across server processes");
        require(command(server,owner,edit(server,owner,"recover"))==1&&y(server,owner)==7,"Recovered offset changed across server restart");require(command(server,owner,edit(server,owner,"save"))==1,"Recovered draft could not be saved at the current revision");AssemblyStudio.logout(owner);System.out.println("WA_PERSISTENT_AUTHORING_RECOVERY_PASS");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

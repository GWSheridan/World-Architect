package com.gwsheridan.worldarchitect.cells;

import com.gwsheridan.worldarchitect.api.CellLayout.Cell;
import com.gwsheridan.worldarchitect.compatibility.SaveProtection;
import com.gwsheridan.worldarchitect.modpack.PresetInheritance;
import com.gwsheridan.worldarchitect.worldgen.CellGenerator;
import com.gwsheridan.worldarchitect.worldgen.CellGenerator.Key;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.*;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Authoritative progression and activation. Network clients can choose earned cells, never grant rewards. */
public final class CellWorlds {
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static final Map<MinecraftServer,Map<Identifier,State>> SERVERS=new WeakHashMap<>();
    private record WritePermit(ServerLevel level,Key key,BlockPos position,net.minecraft.world.level.block.state.BlockState block){}
    private static final ThreadLocal<WritePermit> WRITING=new ThreadLocal<>();
    private CellWorlds(){}
    public static final class State {
        public final CellGenerator generator;
        public final CellLedger ledger;
        private CellActivation activation;
        private final Set<UUID> joined=new HashSet<>();
        private final Set<UUID> waiting=new HashSet<>();
        State(CellGenerator generator,CellLedger ledger){this.generator=generator;this.ledger=ledger;}
    }
    private static CellLedger ledger(Path data,Identifier id,CellGenerator generator,long seed,net.minecraft.core.HolderLookup.Provider registries,boolean fresh)throws IOException{
        String name=PresetInheritance.fingerprint(id.toString().getBytes(StandardCharsets.UTF_8)).substring(0,24);
        String encoded=CellWorldProfile.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,registries),generator.cells()).getOrThrow().toString();
        String pin=PresetInheritance.fingerprint((encoded+"\n"+seed).getBytes(StandardCharsets.UTF_8));
        return new CellLedger(data.resolve("worldarchitect-cells").resolve(name+".json"),generator.cells(),pin,fresh);
    }
    /** Read existing journals before Minecraft can refresh level.dat or create levels. */
    public static void verifySaved(Path data,net.minecraft.world.level.levelgen.WorldDimensions dimensions,long seed,net.minecraft.core.HolderLookup.Provider registries){
        for(var entry:dimensions.dimensions().entrySet())if(entry.getValue().generator() instanceof CellGenerator generator&&generator.cells().progressive())try{
            ledger(data,entry.getKey().identifier(),generator,seed,registries,false);
        }catch(IOException error){throw SaveProtection.incompatible(error.getMessage());}
    }
    /** Called after immutable snapshot checks, before any level starts generating chunks. */
    public static void prepare(MinecraftServer server,boolean fresh){
        var states=new LinkedHashMap<Identifier,State>();var generators=new IdentityHashMap<CellGenerator,Identifier>();
        for(var entry:server.getWorldGenSettings().dimensions().dimensions().entrySet())if(entry.getValue().generator() instanceof CellGenerator generator&&generator.cells().progressive()){
            try{
                for(var goal:generator.cells().growth().goals())if(goal.trigger()==GrowthRules.Trigger.ADVANCEMENT&&server.getAdvancements().get(goal.source())==null)throw new IOException("Cell objective "+goal.id()+" requires missing advancement "+goal.source());
                var id=entry.getKey().identifier();if(generators.put(generator,id)!=null)throw new IOException("Each progressive dimension needs its own generator instance.");
                var ledger=ledger(server.getWorldPath(LevelResource.DATA),id,generator,server.getWorldGenSettings().options().seed(),server.registryAccess(),fresh);
                generator.active(ledger.ready());states.put(id,new State(generator,ledger));
            }catch(IOException error){throw SaveProtection.incompatible(error.getMessage());}
        }
        SERVERS.put(server,states);
    }
    public static State state(ServerLevel level){return SERVERS.getOrDefault(level.getServer(),Map.of()).get(level.dimension().identifier());}
    public static boolean activating(ServerLevel level,BlockPos pos){var state=state(level);if(state==null)return false;var a=state.generator.cells().address(pos.getX(),pos.getZ());return a!=null&&state.ledger.status(new Key(a.territory(),a.cell()))==CellLedger.Status.APPLYING;}
    public static void stopped(ServerStoppedEvent event){SERVERS.remove(event.getServer());}
    static void write(ServerLevel level,Key key,BlockPos position,net.minecraft.world.level.block.state.BlockState block,Runnable action){if(WRITING.get()!=null)throw new IllegalStateException("Nested terrain activation");WRITING.set(new WritePermit(level,key,position.immutable(),block));try{action.run();}finally{WRITING.remove();}}
    public static boolean outsideWritePermit(Level level,BlockPos position,net.minecraft.world.level.block.state.BlockState block){var permit=WRITING.get();return permit!=null&&(level!=permit.level()||!position.equals(permit.position())||block!=permit.block());}
    /** Used by world writes, ticks and automation; locks include the void volume, not just occupied blocks. */
    public static boolean locked(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel serverLevel)||!(serverLevel.getChunkSource().getGenerator() instanceof CellGenerator generator)||!generator.cells().progressive())return false;
        var a=generator.cells().address(pos.getX(),pos.getZ());if(a==null)return true;var key=new Key(a.territory(),a.cell());
        var permit=WRITING.get();if(permit!=null&&permit.level()==serverLevel&&permit.key().equals(key)&&permit.position().equals(pos))return false;
        return !generator.open(pos.getX(),pos.getZ());
    }
    public static boolean fluidOutside(Level level,BlockPos pos,net.minecraft.world.level.block.state.BlockState block){
        if(block.getFluidState().isEmpty()||!(level instanceof ServerLevel serverLevel)||!(serverLevel.getChunkSource().getGenerator() instanceof CellGenerator generator))return false;
        if(generator.endProgression().isPresent()&&com.gwsheridan.worldarchitect.config.EndProgression.contains(pos.getX(),pos.getZ()))return false;
        var a=generator.cells().address(pos.getX(),pos.getZ());return a==null||!generator.cells().layout().contains(a.cell(),a.localX(),a.localZ());
    }
    public static int territory(State state,ServerPlayer player,boolean claim) throws IOException {
        var scope=state.generator.cells().growth().scope();if(scope==GrowthRules.Scope.SHARED)return 0;
        String owner;if(scope==GrowthRules.Scope.TEAM){var team=player.getTeam();if(team==null)return 0;owner="team:"+team.getName();}else owner="player:"+player.getUUID();
        return claim?state.ledger.claim(owner):state.ledger.assigned(owner);
    }
    public static int arrival(State state,ServerPlayer player)throws IOException{int slot=territory(state,player,true);if(state.ledger.status(new Key(slot,Cell.ORIGIN))==CellLedger.Status.READY)return slot;state.waiting.add(player.getUUID());return 0;}
    public static boolean denied(ServerPlayer player,BlockPos pos){
        return denied(player.level(),player,pos);
    }
    public static boolean denied(ServerLevel level,ServerPlayer player,BlockPos pos){
        if(level.getServer()!=player.level().getServer()||locked(level,pos))return true;var state=state(level);if(state==null||state.generator.cells().growth().scope()==GrowthRules.Scope.SHARED||Commands.hasPermission(Commands.LEVEL_ADMINS).test(player.createCommandSourceStack()))return false;
        var address=state.generator.cells().address(pos.getX(),pos.getZ());if(address==null)return true;
        try{return address.territory()==0||address.territory()!=territory(state,player,false);}catch(IOException error){return true;}
    }
    public static void breaking(BreakBlockEvent event){if(event.getPlayer() instanceof ServerPlayer player&&denied(player,event.getPos())){event.setCanceled(true);event.setNotifyClient(true);}}
    public static void placing(BlockEvent.EntityPlaceEvent event){if(event.getEntity() instanceof ServerPlayer player&&(event instanceof BlockEvent.EntityMultiPlaceEvent multi?multi.getReplacedBlockSnapshots().stream().anyMatch(s->denied(player,s.getPos())):denied(player,event.getPos())))event.setCanceled(true);}
    public static void interacting(PlayerInteractEvent.RightClickBlock event){if(event.getEntity() instanceof ServerPlayer player&&denied(player,event.getPos())){event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);}}
    public static void spawning(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck event){if(locked(event.getLevel().getLevel(),event.getEntity().blockPosition()))event.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL);}
    public static void piston(PistonEvent.Pre event){
        if(!(event.getLevel() instanceof ServerLevel level)||state(level)==null)return;
        var helper=event.getStructureHelper();helper.resolve();
        if(locked(level,event.getPos())||locked(level,event.getFaceOffsetPos())||helper.getToPush().stream().anyMatch(p->locked(level,p)||locked(level,p.relative(event.getDirection())))||helper.getToDestroy().stream().anyMatch(p->locked(level,p)))event.setCanceled(true);
    }
    public static void earned(AdvancementEvent.AdvancementEarnEvent event){if(event.getEntity() instanceof ServerPlayer player)for(var state:SERVERS.getOrDefault(player.level().getServer(),Map.of()).values())observe(state,player,GrowthRules.Trigger.ADVANCEMENT,event.getAdvancement().id());}
    public static List<String> signal(ServerPlayer player,GrowthRules.Trigger trigger,Identifier source){
        return signal(player.level(),player,trigger,source);
    }
    public static List<String> signal(ServerLevel dimension,ServerPlayer player,GrowthRules.Trigger trigger,Identifier source){if(dimension.getServer()!=player.level().getServer())throw new IllegalArgumentException("The player and territory must belong to the same server.");var state=state(dimension);if(state==null)throw new IllegalArgumentException("This dimension has no progressive cells.");return observe(state,player,trigger,source);}
    private static List<String> observe(State state,ServerPlayer player,GrowthRules.Trigger trigger,Identifier source){
        if(!player.level().getServer().isSameThread())throw new IllegalStateException("Cell progression must run on the server thread.");
        if(state.generator.cells().growth().goals().stream().noneMatch(g->g.trigger()==trigger&&g.source().equals(source)))return List.of();
        try{int slot=territory(state,player,true);if(slot==0&&state.generator.cells().growth().scope()!=GrowthRules.Scope.SHARED)return List.of();var awarded=state.ledger.observe(slot,trigger,source.toString());for(var id:awarded)player.sendSystemMessage(Component.literal("World expanded: "+state.generator.cells().growth().goals().stream().filter(g->g.id().equals(id)).findFirst().orElseThrow().title()));return awarded;}
        catch(IOException error){throw new UncheckedIOException(error);}
    }
    public static void home(ServerPlayer player) throws IOException {var state=state(player.level());if(state==null)throw new IllegalArgumentException("This dimension has no progressive cells.");int slot=territory(state,player,true);if(state.ledger.status(new Key(slot,Cell.ORIGIN))!=CellLedger.Status.READY){state.waiting.add(player.getUUID());slot=0;}teleport(player,state,slot);}
    private static void teleport(ServerPlayer player,State state,int slot){var marker=state.generator.spawn(new Key(slot,Cell.ORIGIN),player.level());var pos=com.gwsheridan.worldarchitect.spawn.SafeSpawns.nearby(player.level(),marker).filter(p->state.generator.open(p.getX(),p.getZ()));if(pos.isEmpty()){player.connection.disconnect(Component.literal("World Architect could not find a safe home. Ask an administrator to repair the starting cell."));return;}player.teleportTo(player.level(),pos.orElseThrow().getX()+.5,pos.orElseThrow().getY(),pos.orElseThrow().getZ()+.5,Set.of(),0,0,true);}
    public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();for(var level:server.getAllLevels()){
            var state=state(level);if(state==null)continue;
            try{
                // Move players away from a reserved cell before its next write batch.
                for(var player:level.players()){
                    int slot;try{slot=territory(state,player,true);}catch(IllegalStateException error){player.connection.disconnect(Component.literal(error.getMessage()));continue;}boolean first=state.joined.add(player.getUUID());
                    if(first){for(var goal:state.generator.cells().growth().goals())if(goal.trigger()==GrowthRules.Trigger.ADVANCEMENT){var advancement=server.getAdvancements().get(goal.source());if(advancement!=null&&player.getAdvancements().getOrStartProgress(advancement).isDone())signal(player,GrowthRules.Trigger.ADVANCEMENT,goal.source());}}
                    var address=state.generator.cells().address(player.blockPosition().getX(),player.blockPosition().getZ());boolean pending=address!=null&&state.ledger.status(new Key(address.territory(),address.cell()))!=CellLedger.Status.READY;
                    if(state.waiting.contains(player.getUUID())&&state.ledger.status(new Key(slot,Cell.ORIGIN))==CellLedger.Status.READY){state.waiting.remove(player.getUUID());teleport(player,state,slot);}
                    else if(first&&(address==null||address.territory()!=slot)||pending||player.getY()<level.getMinY()-8){if(state.ledger.status(new Key(slot,Cell.ORIGIN))==CellLedger.Status.READY)teleport(player,state,slot);else{state.waiting.add(player.getUUID());teleport(player,state,0);}}
                }
                if(state.activation==null&&!state.ledger.pending().isEmpty())state.activation=new CellActivation(level,state.generator,state.ledger,state.ledger.pending().getFirst());
                if(state.activation!=null&&state.activation.tick(state.generator.cells().growth().blocksPerTick())){if(!state.activation.conflict().isEmpty())LOGGER.warn("Cell {} was preserved: {}",state.activation.key().id(),state.activation.conflict());state.activation=null;}
            }catch(IOException error){throw new UncheckedIOException("Cell progression could not be saved; terrain remains locked.",error);}
        }
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){for(var state:SERVERS.getOrDefault(player.level().getServer(),Map.of()).values()){state.joined.remove(player.getUUID());state.waiting.remove(player.getUUID());}}}
}

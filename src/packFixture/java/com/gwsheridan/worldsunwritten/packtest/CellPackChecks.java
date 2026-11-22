package com.gwsheridan.worldsunwritten.packtest;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Progression checks run only in the test kit's disposable world. */
final class CellPackChecks {
    @FunctionalInterface interface Check{void run()throws Exception;}
    private static final Map<MinecraftServer,Check> PENDING=new WeakHashMap<>();
    static void request(MinecraftServer server,int stage,Check check)throws Exception{
        if(PENDING.containsKey(server))throw new IllegalStateException("A cell test is already running.");boolean waiting=false;
        for(var level:server.getAllLevels())if(level.getChunkSource().getGenerator() instanceof CellGenerator generator&&generator.cells().progressive()){
            var state=CellWorlds.state(level);require(state!=null,"Missing saved cell journal");var key=new Key(0,new Cell(1,0));
            if(stage==1&&state.ledger.status(key)==null){var centre=generator.cells().layout().centre(key.cell());var pos=new net.minecraft.core.BlockPos((int)centre.x(),generator.getMinY()+1,(int)centre.z());require(level.getBlockState(pos).isAir(),"Reserved cell is not void");state.ledger.queue(key);}
            require(state.ledger.status(key)!=CellLedger.Status.CONFLICT,"Terrain conflict in disposable test cell");waiting|=state.ledger.status(key)!=CellLedger.Status.READY;
        }
        if(waiting)PENDING.put(server,check);else check.run();
    }
    static void tick(ServerTickEvent.Post event){var server=event.getServer();var check=PENDING.get(server);if(check==null)return;try{
        for(var level:server.getAllLevels())if(level.getChunkSource().getGenerator() instanceof CellGenerator generator&&generator.cells().progressive()){var state=CellWorlds.state(level);var status=state.ledger.status(new Key(0,new Cell(1,0)));require(status!=CellLedger.Status.CONFLICT,"Cell activation preserved a conflicting block");if(status!=CellLedger.Status.READY)return;}
        PENDING.remove(server);check.run();
    }catch(Exception error){PENDING.remove(server);error.printStackTrace();System.err.println("WA_PACK_TEST_FAILED "+error.getMessage());}}
    static int[] audit(MinecraftServer server,int stage,CompoundTag consumed)throws Exception{
        int markers=0,blocks=0,containers=0;
        for(var level:server.getAllLevels())if(level.getChunkSource().getGenerator() instanceof CellGenerator generator){
            if(generator.cells().layout().gap()>0){
                var sampleY=new TreeSet<Integer>();sampleY.add(level.getMinY()+1);for(var tile:generator.cells().tiles()){sampleY.add(tile.terrain().elevation());sampleY.add(tile.terrain().elevation()+3);tile.content().sea().ifPresent(sea->sampleY.add(sea.level()));}
                boolean checkedFluid=false;for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++){
                    if(generator.endProgression().isPresent()&&com.gwsheridan.worldsunwritten.config.EndProgression.contains(x,z))continue;
                    var address=generator.cells().address(x,z);if(address==null||generator.cells().layout().contains(address.cell(),address.localX(),address.localZ()))continue;
                    for(int y:sampleY)if(level.isInsideBuildHeight(y))require(level.getBlockState(new net.minecraft.core.BlockPos(x,y,z)).isAir(),"Cell terrain or fluid crossed a void gap");
                    if(!checkedFluid){require(!level.setBlock(new net.minecraft.core.BlockPos(x,70,z),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),2),"Fluid write filled a reserved gap");checkedFluid=true;}
                }
            }
            if(generator.endProgression().isPresent())continue; // The native arena owns the origin, including its spawn.
            var keys=generator.cells().progressive()?List.of(new Key(0,Cell.ORIGIN),new Key(0,new Cell(1,0))):List.of(new Key(0,Cell.ORIGIN));
            for(var key:keys){var tile=generator.cells().tile(generator.seed(),key.territory(),key.cell());var marker=generator.spawn(key,level);require(com.gwsheridan.worldsunwritten.spawn.SafeSpawns.safe(level,marker),"Unsafe cell spawn marker");markers++;
                if(tile.template().isPresent()){var origin=generator.origin(tile,key);for(var cell:tile.template().orElseThrow().cells()){blocks++;if(cell.data().isEmpty())continue;var pos=origin.offset(cell.pos());var entity=level.getBlockEntity(pos);require(entity!=null,"Cell template metadata is missing");if(entity instanceof Container container){containers++;for(var value:cell.data().orElseThrow().getListOrEmpty("Items")){var item=(CompoundTag)value;int slot=item.getIntOr("Slot",-1);var expected=ItemStack.CODEC.parse(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE,server.registryAccess()),item).getOrThrow();String id=level.dimension().identifier()+"/"+pos.toShortString()+"/"+slot;var actual=container.getItem(slot);int count=expected.getCount()-(consumed.contains(id)?1:0);require(actual.getCount()==count&&(count==0||ItemStack.isSameItemSameComponents(expected,actual)),"Cell starter inventory changed or refilled");if(stage==1){container.removeItem(slot,1);entity.setChanged();consumed.putBoolean(id,true);}}}}
                }
            }
        }
        return new int[]{markers,blocks,containers};
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}

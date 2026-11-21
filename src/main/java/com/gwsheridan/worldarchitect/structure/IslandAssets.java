package com.gwsheridan.worldarchitect.structure;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Capture, import and immutable local revisions for the island editor. */
public final class IslandAssets {
    private IslandAssets(){}
    public static IslandBuild capture(ServerLevel level,BlockPos first,BlockPos second,boolean contents)throws IOException{
        if(!level.getServer().isSameThread())throw new IllegalStateException("Capture must run on the server thread.");
        var min=new BlockPos(Math.min(first.getX(),second.getX()),Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ()));
        var max=new BlockPos(Math.max(first.getX(),second.getX()),Math.max(first.getY(),second.getY()),Math.max(first.getZ(),second.getZ()));var size=max.subtract(min).offset(1,1,1);
        if(size.getX()>64||size.getY()>64||size.getZ()>64||!level.isInWorldBounds(min)||!level.isInWorldBounds(max))throw new IOException("Capture must fit 64 blocks per side inside the world.");
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)if(!level.hasChunkAt(new BlockPos(x<<4,min.getY(),z<<4)))throw new IOException("Load the entire island selection before capture.");
        var cells=new ArrayList<IslandBuild.Cell>();
        for(var pos:BlockPos.betweenClosed(min,max)){
            var state=level.getBlockState(pos);if(state.isAir())continue;
            if(cells.size()==IslandBuild.MAX_CELLS)throw new IOException("The selection exceeds 65,536 occupied cells.");
            var entity=level.getBlockEntity(pos);CompoundTag data=null;
            if(entity!=null){data=IslandBuild.importMetadata(entity.saveWithoutMetadata(level.registryAccess()),contents);data.putString("id",BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString());}
            cells.add(new IslandBuild.Cell(pos.subtract(min),state,Optional.ofNullable(data)));
        }
        return withAutomaticSpawn(size,cells).checked(level.registryAccess());
    }
    public static IslandBuild withAutomaticSpawn(BlockPos size,List<IslandBuild.Cell> cells)throws IOException{
        var states=new HashMap<BlockPos,BlockState>();cells.forEach(c->states.put(c.pos(),c.state()));
        var candidates=cells.stream().map(c->c.pos().above()).filter(p->NativeTemplate.inside(size,p.above())&&!states.containsKey(p)&&!states.containsKey(p.above())&&com.gwsheridan.worldarchitect.config.IslandTemplate.safeFloor(states.get(p.below()),net.minecraft.world.level.EmptyBlockGetter.INSTANCE,p.below())).sorted(Comparator.comparingDouble(p->p.distSqr(new BlockPos(size.getX()/2,size.getY()/2,size.getZ()/2)))).toList();
        if(candidates.isEmpty())throw new IOException("Include a safe floor and two air cells inside the selection for a spawn marker.");
        return new IslandBuild(size,Map.of("spawn",candidates.getFirst()),"spawn",cells).validate().getOrThrow(IOException::new);
    }
    public static IslandBuild read(Path file,boolean contents,HolderLookup.Provider registries)throws IOException{
        if(!Files.isRegularFile(file)||Files.size(file)>4L*1024*1024)throw new IOException("Choose an island asset within 4 MiB.");
        if(file.getFileName().toString().endsWith(".island.json"))return IslandBuild.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(Files.readString(file))).getOrThrow(IOException::new).checked(registries);
        var root=NbtIo.readCompressed(file,NbtAccounter.create(32L*1024*1024));
        if(file.getFileName().toString().endsWith(".schem"))root=SpongeSchematic.island(root);
        int version=NbtUtils.getDataVersion(root,500);if(version>SharedConstants.getCurrentVersion().dataVersion().version())throw new IOException("This island needs a newer Minecraft data version.");
        root=net.minecraft.util.datafix.DataFixTypes.STRUCTURE.updateToCurrentVersion(net.minecraft.util.datafix.DataFixers.getDataFixer(),root,version);
        if(!root.getListOrEmpty("entities").isEmpty())throw new IOException("Island imports do not support entities.");
        var nativeBuild=NativeTemplate.readIsland(root);var cells=new ArrayList<IslandBuild.Cell>();
        for(var cell:nativeBuild.blocks())if(cell.isSolidContent()){
            var data=IslandBuild.importMetadata(cell.metadata(),contents);
            cells.add(new IslandBuild.Cell(cell.pos(),cell.state(),Optional.ofNullable(data)));
        }
        var build=withAutomaticSpawn(nativeBuild.size(),cells);
        var saved=root.getCompoundOrEmpty("worldarchitect").getCompoundOrEmpty("markers");
        if(!saved.isEmpty()){
            var markers=new TreeMap<String,BlockPos>();for(String name:saved.keySet())markers.put(name,NativeTemplate.readPosition(saved.getCompoundOrEmpty(name).getListOrEmpty("pos")));
            String spawn=markers.containsKey("spawn")?"spawn":markers.firstKey();build=new IslandBuild(build.size(),markers,spawn,cells);
        }
        return build.checked(registries);
    }
    public static Path save(Path directory,String name,String release,IslandBuild build,HolderLookup.Provider registries)throws IOException{
        if(!name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")||!release.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}"))throw new IOException("Choose a simple island name and release.");
        var json=IslandBuild.CODEC.encodeStart(JsonOps.INSTANCE,build.checked(registries)).getOrThrow(IOException::new).toString();if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4*1024*1024)throw new IOException("The island revision exceeds 4 MiB.");
        var target=directory.resolve("islands").resolve(name+"-"+release+".island.json");Files.createDirectories(target.getParent());var temporary=Files.createTempFile(target.getParent(),".island-",".tmp");try{Files.writeString(temporary,json);try(var channel=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}Files.move(temporary,target);return target;}finally{Files.deleteIfExists(temporary);}
    }
    public static IslandBuild library(MinecraftServer server,Identifier id)throws IOException{
        var entry=StructureLibrary.load(server,id);var cells=entry.template().blocks().stream().filter(NativeTemplate.Cell::isSolidContent).map(c->new IslandBuild.Cell(c.pos(),c.state(),Optional.ofNullable(c.metadata()))).toList();
        var build=withAutomaticSpawn(entry.template().size(),cells);if(!entry.markers().isEmpty()){var markers=new TreeMap<String,BlockPos>();entry.markers().forEach((name,m)->markers.put(name,m.pos()));build=new IslandBuild(build.size(),markers,markers.containsKey("spawn")?"spawn":markers.firstKey(),cells);}return build.checked(server.registryAccess());
    }
    public static List<Identifier> library(MinecraftServer server)throws IOException{return StructureLibrary.list(server);}
    public static IslandBuild transform(IslandBuild build,Rotation rotation,Mirror mirror,HolderLookup.Provider registries)throws IOException{
        var placement=new TemplatePlacement(BlockPos.ZERO,rotation,mirror);var markers=new TreeMap<String,BlockPos>();build.markers().forEach((name,pos)->markers.put(name,placement.position(build.size(),pos)));
        return new IslandBuild(placement.size(build.size()),markers,build.spawnMarker(),build.cells().stream().map(c->new IslandBuild.Cell(placement.position(build.size(),c.pos()),placement.state(c.state()),c.data())).toList()).checked(registries);
    }
    public static IslandBuild inventory(IslandBuild build,BlockPos pos,List<ItemStack> slots,HolderLookup.Provider registries)throws IOException{
        if(slots.size()!=27)throw new IOException("Use 27 explicit starter-container slots.");
        var result=new ArrayList<IslandBuild.Cell>();boolean found=false;var ops=RegistryOps.create(NbtOps.INSTANCE,registries);
        for(var cell:build.cells()){
            if(!cell.pos().equals(pos)){result.add(cell);continue;}
            if(!IslandBuild.container(cell.state()))throw new IOException("Choose a chest or barrel inside this island.");
            found=true;var data=cell.data().orElseGet(CompoundTag::new);data.putString("id",cell.state().is(Blocks.BARREL)?"minecraft:barrel":cell.state().is(Blocks.TRAPPED_CHEST)?"minecraft:trapped_chest":"minecraft:chest");var items=new ListTag();
            for(int i=0;i<slots.size();i++)if(!slots.get(i).isEmpty()){var item=(CompoundTag)ItemStack.CODEC.encodeStart(ops,slots.get(i)).getOrThrow(IOException::new);item.putByte("Slot",(byte)i);items.add(item);}data.put("Items",items);result.add(new IslandBuild.Cell(cell.pos(),cell.state(),Optional.of(data)));
        }
        if(!found)throw new IOException("The selected starter container no longer exists.");return new IslandBuild(build.size(),build.markers(),build.spawnMarker(),result).checked(registries);
    }
}

package com.gwsheridan.worldsunwritten.testing;

import com.gwsheridan.worldsunwritten.structure.NativeTemplate;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;

/** Exercises the actual server codecs, registry overlay and immutable exports. */
final class AssemblyAcceptance {
    private static final Identifier ID=Identifier.parse("validation:assembly");
    static void fresh(MinecraftServer server) throws Exception {
        var chestData=new CompoundTag();chestData.putString("id","minecraft:chest");chestData.putString("CustomName","Assembly supplies");
        var template=new NativeTemplate(new BlockPos(3,4,3),List.of(
                new NativeTemplate.Cell(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),null),
                new NativeTemplate.Cell(new BlockPos(1,0,0),Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST),null),
                new NativeTemplate.Cell(new BlockPos(2,0,0),Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH),chestData)));
        Object entry=stat("LibraryEntry","read",template,new CompoundTag(),"Assembly fixture","Validation");
        entry=call(entry,"withMarker","entry",make("LibraryEntry$Marker",new BlockPos(0,1,0),Direction.NORTH));
        stat("StructureLibrary","saveNew",server,Identifier.parse("validation:module"),entry);
        Object a=part("rotated",BlockPos.ZERO,Rotation.CLOCKWISE_90,Mirror.LEFT_RIGHT,entry);
        Object b=part("original",new BlockPos(8,0,0),Rotation.NONE,Mirror.NONE,entry);
        Object draft=draft(List.of(a,b));
        call(draft,"structure",server);
        Object saved=stat("AssemblyDraft","save",server,draft,-1);
        stat("AssemblyDraft","save",server,saved,0);
        denied(()->stat("AssemblyDraft","save",server,saved,0));
        Object loaded=stat("AssemblyDraft","load",server,ID);
        require((int)call(loaded,"revision")==1,"Revision conflict changed saved draft");
        verify(loaded);
        Object overlapping=draft(List.of(a,part("overlap",BlockPos.ZERO,Rotation.CLOCKWISE_90,Mirror.LEFT_RIGHT,entry)));
        denied(()->call(overlapping,"structure",server));
        Object tooWide=draft(List.of(part("wide",new BlockPos(95,0,0),Rotation.NONE,Mirror.NONE,entry)));
        denied(()->call(tooWide,"structure",server));
        var tooMany=new ArrayList<Object>();for(int i=0;i<17;i++)tooMany.add(part("p"+i,new BlockPos(i,0,0),Rotation.NONE,Mirror.NONE,entry));
        Object crowded=draft(tooMany);denied(()->call(crowded,"check"));
        Object missing=make("AssemblyDraft",Identifier.parse("validation:missing"),0,Identifier.parse("validation:missing_biome"),0,4,true,List.of(a));
        denied(()->call(missing,"structure",server));
        var malformed=new CompoundTag();malformed.putInt("CustomName",42);
        Object invalid=stat("LibraryEntry","read",new NativeTemplate(new BlockPos(1,3,1),List.of(new NativeTemplate.Cell(BlockPos.ZERO,Blocks.CHEST.defaultBlockState(),malformed))),new CompoundTag(),"Invalid","Validation");
        Object invalidDraft=draft(List.of(part("invalid",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,invalid)));denied(()->call(invalidDraft,"structure",server));
        var cube=new ArrayList<NativeTemplate.Cell>();for(var pos:BlockPos.betweenClosed(BlockPos.ZERO,new BlockPos(31,31,31)))cube.add(new NativeTemplate.Cell(pos,Blocks.STONE.defaultBlockState(),null));
        Object large=stat("LibraryEntry","read",new NativeTemplate(new BlockPos(32,32,32),cube),new CompoundTag(),"Maximum","Validation");
        Object maximum=draft(List.of(part("a",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,large),part("b",new BlockPos(32,0,0),Rotation.NONE,Mirror.NONE,large)));
        require((int)call(call(maximum,"structure",server),"blockCount")==65536,"Maximum-sized assembly lost cells");
        Object oversized=draft(List.of(part("a",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,large),part("b",new BlockPos(32,0,0),Rotation.NONE,Mirror.NONE,large),part("c",new BlockPos(64,0,0),Rotation.NONE,Mirror.NONE,large)));denied(()->call(oversized,"check"));
        denied(()->stat("AssemblyExports","datapack",server,overlapping,Identifier.parse("validation:invalid")));
        var archive=(Path)stat("AssemblyExports","datapack",server,loaded,Identifier.parse("validation:assembly_export"));
        byte[] pinned=Files.readAllBytes(archive);
        denied(()->stat("AssemblyExports","datapack",server,loaded,Identifier.parse("validation:assembly_export")));
        require(Arrays.equals(pinned,Files.readAllBytes(archive)),"Refused export changed archive");
        try(var zip=new ZipFile(archive.toFile())){require(zip.getEntry("data/validation/worldgen/structure/assembly_export.json")!=null,"Missing structure definition");require(zip.getEntry("data/validation/worldgen/structure_set/assembly_export.json")!=null,"Missing native placement set");require(zip.size()==3,"Unexpected datapack resources");}
        require(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(Identifier.parse("validation:assembly_export")).isEmpty(),"Export installed a live registry entry");
        var dimensions=new ArrayList<>(List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end"));if(server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,Identifier.parse("wa_api_fixture:lab")))!=null)dimensions.add("wa_api_fixture:lab");
        for(String dim:dimensions){
            var bundle=(Path)stat("AssemblyExports","bundle",server,loaded,Identifier.parse("validation:assembly_"+dim.replace(':','_')) ,Identifier.parse(dim),"test-1",18L,"Validation");
            require(Files.size(bundle)>100,"Missing preset bundle");
            System.out.println("WA_ASSEMBLY_BUNDLE "+dim+" "+bundle);
        }
        // The draft retains its own captured templates after the library file is removed.
        Path library=server.getWorldPath(LevelResource.DATA).resolve("worldsunwritten/structures/validation/module.nbt");
        require(Files.exists(library),"Saved template file missing");Files.delete(library);
        verify(stat("AssemblyDraft","load",server,ID));
        System.out.println("WA_ASSEMBLY_STATES_MARKERS_CAS_EXPORTS_PASS");
    }
    static void restart(MinecraftServer server)throws Exception{Object loaded=stat("AssemblyDraft","load",server,ID);require((int)call(loaded,"revision")==1,"Saved revision lost");verify(loaded);call(loaded,"structure",server);System.out.println("WA_ASSEMBLY_RESTART_PASS");}
    static void generated(MinecraftServer server,Identifier id,Identifier dimension,int region)throws Exception{
        var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,dimension));require(level!=null,"Missing assembly dimension");
        var holder=server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,id));
        var state=level.getChunkSource().getGeneratorState();var placements=state.getPlacementsForStructure(holder);require(!placements.isEmpty(),"Assembly not selected by dimension");
        for(var placement:placements)if(placement instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread){
            var candidate=spread.getPotentialStructureChunk(state.getLevelSeed(),region*spread.spacing(),region*spread.spacing());
            for(int x=candidate.x()-1;x<=candidate.x()+1;x++)for(int z=candidate.z()-1;z<=candidate.z()+1;z++)level.getChunk(x,z);
            var start=level.getChunk(candidate.x(),candidate.z()).getStartForStructure(holder.value());require(start!=null&&start.isValid(),"Missing generated assembly at "+candidate);
            require(start.getPieces().size()==2,"Generated assembly omitted modules");int cells=0,markers=0;
            for(var piece:start.getPieces()){
                var entry=call(piece,"entry");var origin=(BlockPos)call(piece,"origin");var template=(NativeTemplate)call(entry,"template");
                for(var cell:template.blocks()){var pos=origin.offset(cell.pos());require(level.getBlockState(pos)==cell.state(),"Generated state mismatch at "+pos);if(cell.metadata()!=null&&cell.metadata().contains("CustomName")){var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);require(chest.getName().getString().equals("Assembly supplies")&&chest.isEmpty(),"Chest metadata or empty inventory changed");}cells++;}
                for(var marker:((Map<?,?>)call(entry,"markers")).values()){var pos=origin.offset((BlockPos)call(marker,"pos"));require(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir()&&!level.getBlockState(pos.below()).isAir(),"Generated marker unsafe");markers++;}
            }
            require(cells==6&&markers==2,"Generated snapshots lost cells or markers");System.out.println("WA_ASSEMBLY_GENERATION_PASS "+dimension+" "+id+" region="+region+" cells="+cells+" markers="+markers);return;
        }
        throw new AssertionError("No random-spread placement for exported assembly");
    }
    private static void verify(Object draft)throws Exception{
        var parts=(List<?>)call(draft,"parts");require(parts.size()==2,"Module snapshots lost");
        var transformed=call(parts.getFirst(),"transformed");var template=(NativeTemplate)call(transformed,"template");
        var states=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(var cell:template.blocks())states.put(cell.pos(),cell.state());
        require(states.get(new BlockPos(0,0,1)).getValue(StairBlock.FACING)==Direction.SOUTH,"Stair transform lost");
        require(states.get(new BlockPos(0,0,2)).getValue(ChestBlock.FACING)==Direction.WEST,"Chest transform lost");
        require(template.blocks().stream().filter(c->c.state().is(Blocks.CHEST)).findFirst().orElseThrow().metadata().getStringOr("CustomName","").equals("Assembly supplies"),"Decorative chest metadata lost");
        Object marker=((Map<?,?>)call(transformed,"markers")).get("entry");
        require(call(marker,"pos").equals(new BlockPos(0,1,0))&&call(marker,"facing")==Direction.WEST,"Marker transform lost");
    }
    private static Object draft(List<?> parts)throws Exception{return make("AssemblyDraft",ID,0,Identifier.withDefaultNamespace("plains"),0,4,true,parts);}
    static Object part(String name,BlockPos offset,Rotation rotation,Mirror mirror,Object entry)throws Exception{return make("AssemblyDraft$Part",name,Identifier.parse("validation:module"),offset,rotation,mirror,entry);}
    @FunctionalInterface private interface Op{void run()throws Exception;}
    private static void denied(Op op)throws Exception{try{op.run();throw new AssertionError("Expected refusal");}catch(IOException expected){}}
    private static Class<?> type(String name)throws Exception{return Class.forName("com.gwsheridan.worldsunwritten.structure."+name);}
    static Object make(String name,Object...args)throws Exception{var c=Arrays.stream(type(name).getDeclaredConstructors()).filter(m->m.getParameterCount()==args.length).findFirst().orElseThrow();c.setAccessible(true);return c.newInstance(args);}
    static Object stat(String name,String method,Object...args)throws Exception{return invoke(type(name),null,method,args);}
    static Object call(Object target,String method,Object...args)throws Exception{return invoke(target.getClass(),target,method,args);}
    private static Object invoke(Class<?> type,Object target,String name,Object[] args)throws Exception{var m=Arrays.stream(type.getDeclaredMethods()).filter(v->v.getName().equals(name)&&v.getParameterCount()==args.length).findFirst().orElseThrow();m.setAccessible(true);try{return m.invoke(target,args);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

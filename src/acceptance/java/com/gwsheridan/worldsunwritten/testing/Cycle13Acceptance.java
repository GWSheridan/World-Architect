package com.gwsheridan.worldsunwritten.testing;

import static com.gwsheridan.worldsunwritten.testing.AssemblyAcceptance.*;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.inspection.*;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.gwsheridan.worldsunwritten.structure.*;
import com.gwsheridan.worldsunwritten.worldgen.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.storage.LevelResource;

/** Actual registries, exported archives, native structure starts and persisted pieces. */
final class Cycle13Acceptance {
    private static final Identifier POOL_ID=Identifier.parse("validation:procedural");
    private static String fingerprint(Object layout)throws Exception {
        var text=new StringBuilder();for(var module:(List<?>)layout){text.append(call(module,"name")).append(call(module,"offset"));text.append(call(call(module,"entry"),"toTag"));}return text.toString();
    }
    private static Object entry(Block floor,String title,boolean chest)throws Exception {
        var cells=new ArrayList<NativeTemplate.Cell>();for(int z=0;z<3;z++)for(int x=0;x<3;x++)cells.add(new NativeTemplate.Cell(new BlockPos(x,0,z),floor.defaultBlockState(),null));
        if(chest){var tag=new CompoundTag();tag.putString("id","minecraft:chest");tag.putString("CustomName","Pool supplies");cells.add(new NativeTemplate.Cell(new BlockPos(0,1,0),Blocks.CHEST.defaultBlockState(),tag));}
        Object entry=stat("LibraryEntry","read",new NativeTemplate(new BlockPos(3,4,3),cells),new CompoundTag(),title,"Validation");
        return call(entry,"withMarker","entry",make("LibraryEntry$Marker",new BlockPos(1,1,1),Direction.NORTH));
    }
    static void fresh(MinecraftServer server)throws Exception {
        var data=server.getWorldPath(LevelResource.DATA);var snapshot=Files.readAllBytes(data.resolve("worldsunwritten-generation-v1.dat"));var settings=Files.readAllBytes(data.resolve("minecraft/world_gen_settings.dat"));
        int loaded=server.overworld().getChunkSource().getLoadedChunksCount();
        var report=SavedWorldInspection.read(data);require(report.healthy(),"Inspector refused a valid snapshot");
        var options=server.getWorldGenSettings().options();var manifest=PresetStudio.defaults("inspection_fixture","one",options);
        var captured=PresetStudio.capture(manifest,server.getWorldGenSettings().dimensions(),server.registryAccess());PresetStudio.save(ModpackRuntime.directory(),"inspection_fixture",captured,server.registryAccess());
        require(server.getCommands().getDispatcher().execute("wa_inspect compare inspection_fixture one",server.createCommandSourceStack())==1,"Inspector compare failed");
        require(server.getCommands().getDispatcher().execute("wa_inspect compare inspection_fixture one report",server.createCommandSourceStack())==1,"Local comparison export failed");
        require(loaded==server.overworld().getChunkSource().getLoadedChunksCount(),"Inspector loaded chunks");
        var root=server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.OVERWORLD).value();
        var plains=server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);var forest=server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.FOREST);var desert=server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT);
        var baseTerrain=new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0);
        var regions=new RegionalTerrain(List.of(
                new RegionalTerrain.Zone("forest_plateau",RegionalTerrain.Mask.RECTANGLE,-512,-512,512,512,0,64,120,0,baseTerrain,Optional.of(forest)),
                new RegionalTerrain.Zone("desert_circle",RegionalTerrain.Mask.CIRCLE,-128,-128,128,128,10,32,180,0,baseTerrain,Optional.of(desert))));
        var shape=new TerrainShape(64,0,Optional.of(baseTerrain),Optional.of(regions));var dimension=new DimensionProfile(root.type().value().minY(),root.type().value().height(),Optional.empty());
        var generator=new LayeredGenerator(plains,new LayeredProfile(Blocks.STONE.defaultBlockState(),List.of(new LayeredProfile.Layer(Blocks.ANDESITE.defaultBlockState(),1),new LayeredProfile.Layer(Blocks.DIRT.defaultBlockState(),3)),shape,List.of()),Optional.of(dimension));
        var stem=new LevelStem(root.type(),generator);var height=LevelHeightAccessor.create(dimension.minY(),dimension.height());long columns=0;
        for(long seed:new long[]{0,194723,Long.MIN_VALUE}){
            var preview=GenerationPreview.forStem(stem,seed).orElseThrow();
            for(int x:new int[]{-1024,-513,-512,-480,-448,-129,-128,-96,-1,0,1,96,128,448,480,512,513})for(int z:new int[]{-512,-128,-1,0,127,512}){
                var column=generator.baseColumn(x,z,height,seed);var displayed=preview.column(x,z);
                for(int y=height.getMinY();y<=height.getMaxY();y++)require(column.getBlock(y)==displayed.getBlock(y),"Regional cross-section differs");
                require(preview.sample(x,z).surfaceY()==com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV3.sample(shape,seed,x,z,height).top(),"Regional preview height differs");
                var expected=regions.biomeAt(QuartPos.toBlock(QuartPos.fromBlock(x)),QuartPos.toBlock(QuartPos.fromBlock(z))).orElse(plains);
                require(preview.biomeAt(x,64,z).orElseThrow().equals(expected.unwrapKey().orElseThrow().identifier()),"Regional preview biome differs");columns++;
            }
        }
        require(loaded==server.overworld().getChunkSource().getLoadedChunksCount(),"Regional preview loaded chunks");
        var dims=new LinkedHashMap<>(server.getWorldGenSettings().dimensions().dimensions());dims.put(LevelStem.OVERWORLD,stem);
        var regionalManifest=PresetStudio.defaults("regional_demo","1.3.0",new WorldOptions(194723,false,false));
        var regional=PresetStudio.capture(regionalManifest,new WorldDimensions(dims),server.registryAccess());PresetStudio.save(ModpackRuntime.directory(),"regional_demo",regional,server.registryAccess());
        require(server.getCommands().getDispatcher().execute("wa_workbench export inspection_fixture one",server.createCommandSourceStack())==1,"Workbench scenario export failed");
        System.out.println("WA_REGIONAL_BUNDLE "+PresetStudio.export(ModpackRuntime.directory(),"regional_demo","1.3.0",server.registryAccess()));
        var parts=new ArrayList<Object>();parts.add(part("junction",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.STONE,"Junction",true)));
        parts.add(part("hall",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.DEEPSLATE,"Hall",false)));
        parts.add(part("moss_cap",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.MOSS_BLOCK,"Moss end cap",false)));
        parts.add(part("stone_cap",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.ANDESITE,"Stone end cap",false)));
        var ports=new ArrayList<ProceduralPool.Port>();
        for(var face:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST)){var pos=new BlockPos(face==Direction.WEST?0:face==Direction.EAST?2:1,1,face==Direction.NORTH?0:face==Direction.SOUTH?2:1);ports.add(new ProceduralPool.Port("junction",face.getSerializedName(),pos,face,"rooms","door"));}
        for(String module:List.of("hall","moss_cap","stone_cap"))ports.add(new ProceduralPool.Port(module,"west",new BlockPos(0,1,1),Direction.WEST,"rooms","door"));
        ports.add(new ProceduralPool.Port("hall","east",new BlockPos(2,1,1),Direction.EAST,"rooms","door"));
        var pool=new ProceduralPool("junction",8,128,37,ports,List.of(new ProceduralPool.Choice("rooms","hall",3,false),new ProceduralPool.Choice("rooms","junction",1,false),new ProceduralPool.Choice("rooms","moss_cap",1,true),new ProceduralPool.Choice("rooms","stone_cap",3,true)));
        Object draft=make("AssemblyDraft",POOL_ID,0,Identifier.withDefaultNamespace("plains"),0,4,true,parts,Optional.of(pool));var structure=call(draft,"structure",server);
        var signatures=new HashSet<String>();int completed=0;
        for(long seed=0;seed<256;seed++){
            var a=(Optional<?>)call(structure,"previewLayout",seed);var b=(Optional<?>)call(structure,"previewLayout",seed);
            require(a.isPresent()==b.isPresent(),"Pool determinism failed");if(a.isEmpty())continue;
            var layout=(List<?>)a.orElseThrow();require(layout.size()>=5&&layout.size()<=8,"Pool module budget exceeded");require(fingerprint(layout).equals(fingerprint(b.orElseThrow())),"Same-seed pool layout differs");signatures.add(fingerprint(layout));
            var cells=new HashSet<BlockPos>();for(var module:layout){var offset=(BlockPos)call(module,"offset");var template=(NativeTemplate)call(call(module,"entry"),"template");for(var cell:template.blocks())require(cells.add(offset.offset(cell.pos())),"Pool cells overlap");require(offset.getX()>=0&&offset.getY()>=0&&offset.getZ()>=0&&offset.getX()+template.size().getX()<=96&&offset.getY()+template.size().getY()<=96&&offset.getZ()+template.size().getZ()<=96,"Pool span exceeded");}require(cells.size()<=65536,"Pool cell budget exceeded");completed++;
        }
        require(completed>=200&&signatures.size()>8,"Insufficient valid varied pool layouts");
        for(var invalid:List.of(new ProceduralPool("missing",8,128,0,ports,pool.choices()),new ProceduralPool("junction",4,128,0,ports,pool.choices()),new ProceduralPool("junction",8,128,0,ports,pool.choices().stream().filter(c->!c.endcap()).toList()),new ProceduralPool("junction",8,257,0,ports,pool.choices()))){boolean refused=false;try{call(make("AssemblyDraft",POOL_ID,0,Identifier.withDefaultNamespace("plains"),0,4,true,parts,Optional.of(invalid)),"structure",server);}catch(java.io.IOException expected){refused=true;}require(refused,"Invalid pool accepted");}
        var scarce=new ProceduralPool("junction",8,1,37,ports,pool.choices());var scarceStructure=call(make("AssemblyDraft",POOL_ID,0,Identifier.withDefaultNamespace("plains"),0,4,true,parts,Optional.of(scarce)),"structure",server);require(((Optional<?>)call(scarceStructure,"previewLayout",0L)).isEmpty(),"Exhausted pool wrote a partial layout");
        var workers=java.util.concurrent.Executors.newFixedThreadPool(4);try{var jobs=new ArrayList<java.util.concurrent.Future<String>>();for(long seed=0;seed<64;seed++){long value=seed;jobs.add(workers.submit(()->fingerprint(((Optional<?>)call(structure,"previewLayout",value)).orElseThrow())));}for(int i=0;i<jobs.size();i++)require(jobs.get(i).get().equals(fingerprint(((Optional<?>)call(structure,"previewLayout",(long)i)).orElseThrow())),"Concurrent pool layout differs");}finally{workers.shutdownNow();}
        var saved=stat("AssemblyDraft","save",server,draft,-1);require(call(stat("AssemblyDraft","load",server,POOL_ID),"pool").equals(Optional.of(pool)),"Pool draft lost settings");
        var poolDimensions=new ArrayList<>(List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end"));if(server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse("wa_api_fixture:lab")))!=null)poolDimensions.add("wa_api_fixture:lab");
        for(String dim:poolDimensions)System.out.println("WA_POOL_BUNDLE "+dim+" "+stat("AssemblyExports","bundle",server,saved,Identifier.parse("validation:pool_"+dim.replace(':','_')),Identifier.parse(dim),"1.3.0",18L,"Validation"));
        require(Arrays.equals(snapshot,Files.readAllBytes(data.resolve("worldsunwritten-generation-v1.dat")))&&Arrays.equals(settings,Files.readAllBytes(data.resolve("minecraft/world_gen_settings.dat"))),"Authoring changed generation pins");
        System.out.println("WA_CYCLE13_INSPECTION_REGIONS_POOLS_PASS columns="+columns+" pool_seeds="+completed+" layouts="+signatures.size());
    }
    static void restart(MinecraftServer server)throws Exception {var draft=stat("AssemblyDraft","load",server,POOL_ID);require(((Optional<?>)call(draft,"pool")).isPresent(),"Pool draft lost after restart");call(draft,"structure",server);require(SavedWorldInspection.read(server.getWorldPath(LevelResource.DATA)).healthy(),"Snapshot invalid after restart");System.out.println("WA_CYCLE13_RESTART_PASS");}
    static void regions(MinecraftServer server,int stage){
        var level=server.overworld();require(level.getChunkSource().getGenerator() instanceof LayeredGenerator,"Expected regional Layered example");var g=(LayeredGenerator)level.getChunkSource().getGenerator();require(g.profile().generatorVersion()==3,"Wrong regional version");var region=g.profile().shape().regions().orElseThrow();long blocks=0;
        for(int cx:new int[]{-33,-32,-9,-8,-1,0,7,8,31,32}){int cz=stage==1?0:3;var chunk=level.getChunk(cx,cz);for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
            int top=com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV3.sample(g.profile().shape(),level.getSeed(),x,z,level).top();
            for(int y=level.getMinY();y<=level.getMaxY();y++){var expected=y>top?Blocks.AIR.defaultBlockState():y==top?Blocks.ANDESITE.defaultBlockState():top-y<4?Blocks.DIRT.defaultBlockState():Blocks.STONE.defaultBlockState();require(chunk.getBlockState(new BlockPos(x,y,z))==expected,"Regional generated block differs at "+x+","+y+","+z);blocks++;}
            var expected=region.biomeAt(QuartPos.toBlock(QuartPos.fromBlock(x)),QuartPos.toBlock(QuartPos.fromBlock(z))).orElse(g.fallbackBiome());require(chunk.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z)).equals(expected),"Generated regional biome differs at "+x+","+z);
        }}System.out.println("WA_REGIONAL_GENERATION_PASS stage="+stage+" blocks="+blocks);
    }
    static void generated(MinecraftServer server,Identifier id,Identifier dimension,int region)throws Exception {
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,dimension));require(level!=null,"Missing pool dimension");var holder=server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,id));var state=level.getChunkSource().getGeneratorState();
        var receipt=ModpackRuntime.directory().resolve("validation-pool-"+UUID.nameUUIDFromBytes((id+"/"+dimension+"/"+region).getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json");
        var previous=Files.exists(receipt)?com.google.gson.JsonParser.parseString(Files.readString(receipt)).getAsJsonObject():null;
        for(var placement:state.getPlacementsForStructure(holder))if(placement instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread){
            for(int attempt=0;attempt<(previous==null?8:1);attempt++){var candidate=previous==null?spread.getPotentialStructureChunk(state.getLevelSeed(),(region+attempt)*spread.spacing(),region*spread.spacing()):new ChunkPos(previous.get("x").getAsInt(),previous.get("z").getAsInt());var start=level.getChunk(candidate.x(),candidate.z()).getStartForStructure(holder.value());if(start==null||!start.isValid()){require(previous==null,"Saved pool start was lost");continue;}
                var bounds=start.getBoundingBox();for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++)level.getChunk(x,z);
                require(start.getPieces().size()>=5&&start.getPieces().size()<=8,"Saved pool module count differs");int cells=0,markers=0;
                for(var piece:start.getPieces()){var entry=call(piece,"entry");var origin=(BlockPos)call(piece,"origin");var template=(NativeTemplate)call(entry,"template");for(var cell:template.blocks()){var pos=origin.offset(cell.pos());require(level.getBlockState(pos)==cell.state(),"Generated pool state differs");if(cell.metadata()!=null&&cell.metadata().contains("CustomName")){var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);require(chest.getName().getString().equals("Pool supplies")&&chest.isEmpty(),"Pool chest metadata differs");}cells++;}
                    for(var marker:((Map<?,?>)call(entry,"markers")).values()){var pos=origin.offset((BlockPos)call(marker,"pos"));require(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir()&&!level.getBlockState(pos.below()).isAir(),"Pool marker unsafe");markers++;}}
                var signature=new StringBuilder();for(var piece:start.getPieces())signature.append(call(piece,"name")).append(call(piece,"origin")).append(call(call(piece,"entry"),"toTag"));
                String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(signature.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if(previous!=null)require(previous.get("pieces_sha256").getAsString().equals(sha),"Saved pool templates, transforms or markers changed");
                else{var saved=new com.google.gson.JsonObject();saved.addProperty("x",candidate.x());saved.addProperty("z",candidate.z());saved.addProperty("pieces_sha256",sha);Files.writeString(receipt,saved+"\n",StandardOpenOption.CREATE_NEW);}
                require(markers==start.getPieces().size(),"Pool marker missing");System.out.println("WA_POOL_GENERATION_PASS dimension="+dimension+" region="+region+" cells="+cells+" markers="+markers+" persisted="+(previous!=null));return;
            }
        }throw new AssertionError("No complete procedural structure generated");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

package com.gwsheridan.worldsunwritten.testing;

import static com.gwsheridan.worldsunwritten.testing.AssemblyAcceptance.*;
import com.google.gson.*;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.gwsheridan.worldsunwritten.rules.*;
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
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;

final class Cycle14Acceptance {
    private static final Identifier POOL=Identifier.parse("validation:constrained_pool");
    private static final int[][] CHUNKS={{-17,-1},{-16,-1},{-5,-1},{-4,-1},{3,-1},{4,-1},{15,-1},{16,-1},{17,-1},{-4,-4},{-3,-3},{0,0},{1,0},{0,1},{1,1}};
    private static Object entry(Block floor,String title,boolean chest)throws Exception{
        var cells=new ArrayList<NativeTemplate.Cell>();for(int z=0;z<3;z++)for(int x=0;x<3;x++)cells.add(new NativeTemplate.Cell(new BlockPos(x,0,z),floor.defaultBlockState(),null));
        if(chest){var tag=new CompoundTag();tag.putString("id","minecraft:chest");tag.putString("CustomName","Pool supplies");cells.add(new NativeTemplate.Cell(new BlockPos(0,1,0),Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.EAST),tag));}
        Object e=stat("LibraryEntry","read",new NativeTemplate(new BlockPos(3,4,3),cells),new CompoundTag(),title,"Validation");return call(e,"withMarker","entry",make("LibraryEntry$Marker",new BlockPos(1,1,1),Direction.NORTH));
    }
    static void fresh(MinecraftServer server)throws Exception{
        Path data=server.getWorldPath(LevelResource.DATA);byte[] snapshot=Files.readAllBytes(data.resolve("worldsunwritten-generation-v1.dat")),settings=Files.readAllBytes(data.resolve("minecraft/world_gen_settings.dat"));int loaded=server.overworld().getChunkSource().getLoadedChunksCount();
        pools(server);regionsExport(server);Cycle14AuthoringAcceptance.fresh(server);
        require(loaded==server.overworld().getChunkSource().getLoadedChunksCount(),"Detached 1.4 authoring created chunks");
        require(Arrays.equals(snapshot,Files.readAllBytes(data.resolve("worldsunwritten-generation-v1.dat")))&&Arrays.equals(settings,Files.readAllBytes(data.resolve("minecraft/world_gen_settings.dat"))),"1.4 authoring changed saved generation");
        System.out.println("WA_CYCLE14_HISTORY_REGIONS_CONSTRAINTS_WORKBENCH_PASS");
    }
    private static void pools(MinecraftServer server)throws Exception{
        var parts=new ArrayList<Object>();parts.add(part("junction",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.STONE,"Junction",true)));parts.add(part("hall",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.DEEPSLATE,"Hall",false)));parts.add(part("moss_cap",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.MOSS_BLOCK,"Moss end cap",false)));parts.add(part("stone_cap",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.ANDESITE,"Stone end cap",false)));
        var ports=new ArrayList<ProceduralPool.Port>();for(var face:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST))ports.add(new ProceduralPool.Port("junction",face.getSerializedName(),new BlockPos(face==Direction.WEST?0:face==Direction.EAST?2:1,1,face==Direction.NORTH?0:face==Direction.SOUTH?2:1),face,"rooms","door"));
        for(String name:List.of("hall","moss_cap","stone_cap"))ports.add(new ProceduralPool.Port(name,"west",new BlockPos(0,1,1),Direction.WEST,"rooms","door"));ports.add(new ProceduralPool.Port("hall","east",new BlockPos(2,1,1),Direction.EAST,"rooms","door"));
        var old=new ProceduralPool("junction",8,128,37,ports,List.of(new ProceduralPool.Choice("rooms","hall",3,false),new ProceduralPool.Choice("rooms","junction",1,false),new ProceduralPool.Choice("rooms","moss_cap",1,true),new ProceduralPool.Choice("rooms","stone_cap",3,true)));
        Object original=make("AssemblyDraft",POOL,0,Identifier.withDefaultNamespace("plains"),0,4,true,parts,Optional.of(old));var legacy=call(original,"structure",server);
        LegacyPoolComparison.check(server,legacy,Structure.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),(Structure)legacy).getOrThrow());
        var constraints=new ProceduralPool.Constraints(2,List.of(new ProceduralPool.ModuleCount("hall",1,2),new ProceduralPool.ModuleCount("stone_cap",1,16)));
        var pool=old.withConstraints(constraints);Object draft=make("AssemblyDraft",POOL,0,Identifier.withDefaultNamespace("plains"),0,4,true,parts,Optional.of(pool));var structure=call(draft,"structure",server);
        int complete=0;var signatures=new HashSet<String>();
        for(long seed=0;seed<128;seed++){
            var report=call(structure,"previewReport",seed);var result=(Optional<?>)call(report,"modules");var repeated=(Optional<?>)call(structure,"previewLayout",seed);
            require(result.isPresent()==repeated.isPresent(),"Constrained sample decisions differ");require((int)call(report,"attempts")<=128&&(int)call(report,"depth")<=2,"Constrained work/depth exceeded");
            if(result.isEmpty())continue;var counts=(Map<?,?>)call(report,"counts");require((int)counts.get("hall")>=1&&(int)counts.get("hall")<=2&&(int)counts.get("stone_cap")>=1,"Required room count failed");
            require(LegacyPoolComparison.signature(result.orElseThrow()).equals(LegacyPoolComparison.signature(repeated.orElseThrow())),"Constrained layout is not deterministic");
            var occupied=new HashSet<BlockPos>();for(var module:(List<?>)result.orElseThrow()){var offset=(BlockPos)call(module,"offset");var template=(NativeTemplate)call(call(module,"entry"),"template");for(var cell:template.blocks())require(occupied.add(offset.offset(cell.pos())),"Constrained cells overlap");require(offset.getX()>=0&&offset.getZ()>=0&&offset.getX()+template.size().getX()<=96&&offset.getZ()+template.size().getZ()<=96,"Constrained bounds failed");}
            require(occupied.size()<=65536,"Constrained occupied-cell budget exceeded");complete++;signatures.add(LegacyPoolComparison.signature(result.orElseThrow()));
        }
        require(complete>=96&&signatures.size()>4,"Required-room layouts are not sufficiently usable or varied");
        var impossible=old.withConstraints(new ProceduralPool.Constraints(1,List.of(new ProceduralPool.ModuleCount("hall",1,2))));var invalid=call(make("AssemblyDraft",POOL,0,Identifier.withDefaultNamespace("plains"),0,4,true,parts,Optional.of(impossible)),"structure",server);var refusal=call(invalid,"previewReport",0L);
        require(((Optional<?>)call(refusal,"modules")).isEmpty()&&call(refusal,"reasons").toString().contains("Required module hall"),"Unmet room requirements emitted pieces or lacked an explanation");
        var workers=java.util.concurrent.Executors.newFixedThreadPool(4);try{var jobs=new ArrayList<java.util.concurrent.Future<String>>();for(long i=0;i<32;i++){long seed=i;jobs.add(workers.submit(()->LegacyPoolComparison.signature(((Optional<?>)call(structure,"previewLayout",seed)).orElseThrow())));}for(int i=0;i<32;i++)require(jobs.get(i).get().equals(LegacyPoolComparison.signature(((Optional<?>)call(structure,"previewLayout",(long)i)).orElseThrow())),"Concurrent constrained layouts changed");}finally{workers.shutdownNow();}
        var saved=stat("AssemblyDraft","save",server,draft,-1);var dims=new ArrayList<>(List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end"));if(server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse("wa_api_fixture:lab")))!=null)dims.add("wa_api_fixture:lab");
        for(String dim:dims)System.out.println("WA_CONSTRAINED_POOL_BUNDLE "+dim+" "+stat("AssemblyExports","bundle",server,saved,Identifier.parse("validation:constrained_"+dim.replace(':','_')),Identifier.parse(dim),"1.4.0",18L,"Validation"));
        System.out.println("WA_CONSTRAINED_LAYOUTS_PASS seeds=128 complete="+complete+" varied="+signatures.size());
    }
    private static void regionsExport(MinecraftServer server)throws Exception{
        var root=server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.OVERWORLD).value();var plains=server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);var land=new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0);
        var regions=new RegionalTerrain(List.of(new RegionalTerrain.Zone("meadow",RegionalTerrain.Mask.RECTANGLE,-256,-256,256,256,0,0,64,0,land,Optional.empty()),new RegionalTerrain.Zone("circle",RegionalTerrain.Mask.CIRCLE,-64,-64,64,64,10,0,64,0,land,Optional.empty())));
        var generator=new SingleBlockGenerator(plains,new SingleBlockProfile(Blocks.STONE.defaultBlockState(),new TerrainShape(64,0,Optional.of(land),Optional.of(regions))),Optional.of(new DimensionProfile(root.type().value().minY(),root.type().value().height(),Optional.empty())));
        var dimensions=new LinkedHashMap<>(server.getWorldGenSettings().dimensions().dimensions());dimensions.put(LevelStem.OVERWORLD,new LevelStem(root.type(),generator));var source=PresetStudio.capture(PresetStudio.defaults("shared_regions","1.4.0",new WorldOptions(194723,true,false)),new WorldDimensions(dimensions),server.registryAccess());var preset=source.preset();
        var rules=JsonParser.parseString("""
            {"version":2,"dimension":"minecraft:overworld","rules":[
            {"id":"diamond_circle","when":{"min_y":60,"max_y":64,"named_region":{"name":"circle"}},"action":{"type":"material","material":"minecraft:diamond_block","target":"minecraft:stone"}},
            {"id":"meadow_ore","when":{"named_region":{"name":"meadow","mode":"winner"}},"action":{"type":"ore","ore":{"material":"minecraft:gold_ore","target":"minecraft:stone","attempts":32,"size":16,"min_y":20,"max_y":24}}},
            {"id":"meadow_crystals","when":{"named_region":{"name":"meadow","mode":"winner"}},"action":{"type":"decoration","decoration":{"material":"minecraft:amethyst_block","attempts":8,"chance":100}}},
            {"id":"circle_building","when":{"named_region":{"name":"circle"}},"action":{"type":"template","template":{"structure":"validation:regional_marker","spacing":4,"separation":3}}},
            {"id":"outside_spawn","priority":20,"when":{"named_region":{"name":"circle"}},"action":{"type":"spawn","spawn":{"kind":"fixed","dimension":"minecraft:overworld","position":[160,65,160]}}},
            {"id":"inside_spawn","priority":10,"when":{"named_region":{"name":"circle"}},"action":{"type":"spawn","spawn":{"kind":"fixed","dimension":"minecraft:overworld","position":[16,65,16]}}}
            ]}
            """).getAsJsonObject();
        preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").getAsJsonObject("dimension_profile").add("rule_engine",rules);
        var building=call(make("AssemblyDraft",Identifier.parse("validation:regional_marker"),0,Identifier.withDefaultNamespace("plains"),0,4,true,List.of(part("start",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry(Blocks.BRICKS,"Regional marker",true)))),"structure",server);
        var resources=new TreeMap<>(source.templates());resources.put("data/validation/worldgen/structure/regional_marker.json",Structure.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),(Structure)building).getOrThrow().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var draft=new PresetStudio.Draft(source.manifest(),preset,resources);PresetStudio.validate(draft,server.registryAccess());PresetStudio.save(ModpackRuntime.directory(),"shared_regions",draft,server.registryAccess());
        System.out.println("WA_NAMED_REGION_BUNDLE "+PresetStudio.export(ModpackRuntime.directory(),"shared_regions","1.4.0",server.registryAccess()));
    }
    static void restart(MinecraftServer server)throws Exception{var saved=stat("AssemblyDraft","load",server,POOL);var pool=(Optional<?>)call(saved,"pool");require(((ProceduralPool)pool.orElseThrow()).format()==2&&((ProceduralPool)pool.orElseThrow()).constraints().maxDepth()==2,"Constrained definition changed on restart");Cycle14AuthoringAcceptance.restart(server);System.out.println("WA_CYCLE14_RESTART_PASS");}
    static void regions(MinecraftServer server,int stage)throws Exception{
        var level=server.overworld();require(level.getChunkSource().getGenerator() instanceof SolidColumnGenerator,"Shared-region fixture needs solid terrain");var generator=(SolidColumnGenerator)level.getChunkSource().getGenerator();require(generator.rules().version()==2,"Expected rule version 2");
        long ores=0,decorations=0,diamond=0,starts=0;var signature=new StringBuilder();
        var registry=server.registryAccess().lookupOrThrow(Registries.STRUCTURE);var fixtureKey=ResourceKey.create(Registries.STRUCTURE,Identifier.parse("validation:regional_marker"));var structure=registry.get(fixtureKey).or(()->registry.get(ResourceKey.create(Registries.STRUCTURE,Identifier.parse("worldsunwritten_examples:regional_marker")))).orElseGet(()->registry.getOrThrow(ResourceKey.create(Registries.STRUCTURE,Identifier.parse("worldarchitect_examples:regional_marker")))).value();
        for(var point:CHUNKS){var chunk=level.getChunk(point[0],point[1]);for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++){
            boolean circle=(long)x*x+(long)z*z<=64L*64,meadow=x>=-256&&x<=256&&z>=-256&&z<=256&&!circle;
            for(int y=level.getMinY();y<=70;y++){var pos=new BlockPos(x,y,z);var state=chunk.getBlockState(pos);signature.append(state);if(state.is(Blocks.GOLD_ORE)){require(meadow&&y>=20&&y<=24,"Ore leaked outside its named winning region");ores++;}if(state.is(Blocks.AMETHYST_BLOCK)){require(meadow&&y==65,"Decoration leaked outside its named winning region");decorations++;}if(y>=60&&y<=64){require(state.is(circle?Blocks.DIAMOND_BLOCK:Blocks.STONE),"Material mask differs at "+pos);if(circle)diamond++;}}
            signature.append(level.getBiome(new BlockPos(x,64,z)).unwrapKey().orElseThrow().identifier());
        }
            var start=chunk.getStartForStructure(structure);if(start!=null&&start.isValid()){starts++;for(var piece:start.getPieces()){var origin=(BlockPos)call(piece,"origin");var entry=call(piece,"entry");var template=(NativeTemplate)call(entry,"template");for(var cell:template.blocks()){var pos=origin.offset(cell.pos());require((long)pos.getX()*pos.getX()+(long)pos.getZ()*pos.getZ()<=64L*64,"Template cell leaked outside named circle");}}}
        }
        require(ores>0&&decorations>0&&diamond>0&&starts>0,"Shared-region fixture did not exercise all generation actions");var spawn=RuleSpawns.select(level,RuleSet.SpawnEvent.INITIAL).orElseThrow();require(spawn.position().equals(new BlockPos(16,65,16)),"Named spawn condition selected the outside policy");
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(signature.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));Path receipt=server.getWorldPath(LevelResource.DATA).resolve("worldsunwritten-named-region-audit.txt");
        if(stage==1)Files.writeString(receipt,hash,StandardOpenOption.CREATE_NEW);else require(Files.readString(receipt).equals(hash),"Shared-region blocks or biomes changed after restart");
        System.out.println("WA_NAMED_REGIONS_GENERATION_PASS stage="+stage+" ores="+ores+" decorations="+decorations+" diamonds="+diamond+" starts="+starts);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

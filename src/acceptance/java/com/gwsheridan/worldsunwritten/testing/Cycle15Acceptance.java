package com.gwsheridan.worldsunwritten.testing;

import static com.gwsheridan.worldsunwritten.testing.AssemblyAcceptance.*;
import com.google.gson.*;
import com.gwsheridan.worldsunwritten.config.*;
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
import net.minecraft.world.level.dimension.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;

/** Real-registry checks and inert exports; separate from the gameplay JAR. */
final class Cycle15Acceptance {
    private static final Identifier ID=Identifier.parse("validation:vertical_tower");
    static void fresh(MinecraftServer server)throws Exception{
        var data=server.getWorldPath(LevelResource.DATA);byte[] snapshot=Files.readAllBytes(data.resolve("worldsunwritten-generation-v1.dat")),settings=Files.readAllBytes(data.resolve("minecraft/world_gen_settings.dat"));int loaded=server.overworld().getChunkSource().getLoadedChunksCount();
        require(PlacementAnchor.MIN_Y==DimensionType.MIN_Y&&PlacementAnchor.MAX_Y==DimensionType.MAX_Y,"Anchor bounds differ from the game");
        collision();var draft=vertical(server);var structure=(Structure)call(draft,"structure",server);
        var source=source(server,structure,ID,Optional.empty(),"vertical_pool");PresetStudio.save(ModpackRuntime.directory(),"vertical_pool",source,server.registryAccess());
        var checked=PresetStudio.validate(source,server.registryAccess());var input=SiteInspector.prepare(checked,Identifier.withDefaultNamespace("overworld"),ID,18,32,32,server.registryAccess(),server.getStructureTemplateManager());
        var report=complete(input);require(report.getStringOr("status","").equals("eligible"),"Vertical site rejected: "+report.getListOrEmpty("rows"));
        Cycle15AuthoringAcceptance.check(server);
        require(report.getIntArray("origin").orElseThrow()[1]+report.getIntArray("root").orElseThrow()[1]==100,"Lower modules moved the anchored root");
        require(report.getListOrEmpty("ports").size()==6&&report.getListOrEmpty("markers").size()==4,"Site preview lost connectors or markers");
        water(server);polygonExport(server);stressExport(server,draft);
        var registeredDimensions=new WorldDimensions(server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM)).dimensions();
        for(var dimension:registeredDimensions.keySet()){
            if(!com.gwsheridan.worldsunwritten.dimension.DimensionCatalog.inspect(server.registryAccess(),dimension,registeredDimensions.get(dimension)).modes().contains(com.gwsheridan.worldsunwritten.api.DimensionAdapter.TerrainMode.SINGLE_BLOCK))continue;
            var target=Identifier.parse("validation:vertical_"+dimension.identifier().toString().replace(':','_'));var path=stat("AssemblyExports","bundle",server,draft,target,dimension.identifier(),"1.5.0",18L,"Validation");
            System.out.println("WA15_BUNDLE vertical "+dimension.identifier()+" "+target+" "+path);
        }
        var old=stat("AssemblyDraft","load",server,Identifier.parse("validation:constrained_pool"));var oldStructure=call(old,"structure",server);
        LegacyPoolComparison.check14(server,oldStructure,Structure.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),(Structure)oldStructure).getOrThrow());
        require(loaded==server.overworld().getChunkSource().getLoadedChunksCount(),"Detached site authoring created chunks");
        require(Arrays.equals(snapshot,Files.readAllBytes(data.resolve("worldsunwritten-generation-v1.dat")))&&Arrays.equals(settings,Files.readAllBytes(data.resolve("minecraft/world_gen_settings.dat"))),"Site authoring changed generation files");
        System.out.println("WA_CYCLE15_POLYGON_ANCHORS_VERTICAL_SITES_PASS");
    }
    private static Object entry(String name,boolean down,boolean up,boolean chest)throws Exception{
        var cells=new ArrayList<NativeTemplate.Cell>();for(int x=0;x<3;x++)for(int z=0;z<3;z++){
            if(!down||x!=1||z!=1)cells.add(new NativeTemplate.Cell(new BlockPos(x,0,z),Blocks.ANDESITE.defaultBlockState(),null));
            if(!up||x!=1||z!=1)cells.add(new NativeTemplate.Cell(new BlockPos(x,3,z),Blocks.STONE_BRICKS.defaultBlockState(),null));
        }
        if(chest){var metadata=new CompoundTag();metadata.putString("CustomName","Tower supplies");cells.add(new NativeTemplate.Cell(new BlockPos(2,1,0),Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.EAST),metadata));}
        var e=stat("LibraryEntry","read",new NativeTemplate(new BlockPos(3,4,3),cells),new CompoundTag(),name,"Validation");
        if(name.equals("Middle")){var r=call(e,"rules");e=call(e,"withRules",make("TemplateRules",call(r,"dimension"),call(r,"biome"),call(r,"minY"),call(r,"maxY"),enumValue("Terrain","SUPPORTED"),call(r,"collision")));}
        return call(e,"withMarker","entry",make("LibraryEntry$Marker",new BlockPos(0,1,1),Direction.NORTH));
    }
    private static Object vertical(MinecraftServer server)throws Exception{
        var parts=List.of(part("junction",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry("Root",true,true,true)),part("middle",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry("Middle",true,true,false)),part("top",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry("Top",true,false,false)),part("bottom",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,entry("Bottom",false,true,false)));
        var ports=List.of(port("junction","up",Direction.UP,"up"),port("junction","down",Direction.DOWN,"down"),port("middle","up",Direction.UP,"up"),port("middle","down",Direction.DOWN,"up"),port("top","down",Direction.DOWN,"up"),port("bottom","up",Direction.UP,"down"));
        var pool=new ProceduralPool("junction",4,128,37,ports,List.of(new ProceduralPool.Choice("up","middle",1,false),new ProceduralPool.Choice("up","top",1,true),new ProceduralPool.Choice("up","bottom",1,true),new ProceduralPool.Choice("down","bottom",1,true)),3,new ProceduralPool.Constraints(2,List.of(new ProceduralPool.ModuleCount("middle",1,1),new ProceduralPool.ModuleCount("top",1,1),new ProceduralPool.ModuleCount("bottom",1,1))));
        var draft=make("AssemblyDraft",ID,0,Identifier.withDefaultNamespace("plains"),0,4,true,parts,Optional.of(pool),Optional.of(new PlacementAnchor(PlacementAnchor.Mode.FIXED,100)));var structure=call(draft,"structure",server);
        for(long seed=0;seed<128;seed++){
            var report=call(structure,"previewReport",seed);var modules=(Optional<?>)call(report,"modules");require(modules.isPresent()&&((List<?>)modules.orElseThrow()).size()==4,"Vertical layout incomplete at seed "+seed);
            require((int)call(report,"depth")<=2&&(int)call(report,"attempts")<=128,"Vertical planning exceeded bounds");
            var next=(Optional<?>)call(structure,"previewLayout",seed);require(LegacyPoolComparison.signature(modules.orElseThrow()).equals(LegacyPoolComparison.signature(next.orElseThrow())),"Vertical layout is not repeatable");
            Object root=((List<?>)modules.orElseThrow()).stream().filter(m->{try{return call(m,"name").equals("root");}catch(Exception e){throw new IllegalStateException(e);}}).findFirst().orElseThrow();require(((BlockPos)call(root,"offset")).getY()==4,"Vertical normalization lost root offset");
        }
        stat("AssemblyDraft","save",server,draft,-1);return draft;
    }
    private static ProceduralPool.Port port(String module,String name,Direction direction,String pool){return new ProceduralPool.Port(module,name,new BlockPos(1,direction==Direction.UP?3:0,1),direction,pool,"shaft");}
    private static void collision()throws Exception{
        var water=enumValue("Collision","AIR_OR_WATER");var air=enumValue("Collision","AIR");
        for(int level:new int[]{0,7}){var state=Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,level);require((boolean)call(water,"allows",state,Blocks.STONE.defaultBlockState()),"Water policy rejected water");require(!(boolean)call(air,"allows",state,Blocks.STONE.defaultBlockState()),"Default policy allowed water");}
        for(var state:List.of(Blocks.LAVA.defaultBlockState(),Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED,true),Blocks.CHEST.defaultBlockState()))require(!(boolean)call(water,"allows",state,Blocks.STONE.defaultBlockState()),"Water policy allowed lava, a waterlogged solid or a container");
    }
    private static PresetStudio.Draft source(MinecraftServer server,Structure structure,Identifier id,Optional<ContentProfile.Sea> sea,String name)throws Exception{
        var root=server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.OVERWORLD).value();var type=root.type().value();var plains=server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var content=ContentProfile.EMPTY.withSea(sea);var profile=new DimensionProfile(type.minY(),type.height(),Optional.empty()).withContent(content);
        TerrainShape shape=new TerrainShape(64,0);
        if(sea.isPresent()){var land=new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0);shape=new TerrainShape(64,0,Optional.of(land),Optional.of(new RegionalTerrain(List.of(new RegionalTerrain.Zone("dry_coast",RegionalTerrain.Mask.CIRCLE,-128,-128,128,128,0,16,96,0,land,Optional.empty())))));}
        var generator=new SingleBlockGenerator(plains,new SingleBlockProfile(Blocks.STONE.defaultBlockState(),shape),Optional.of(profile));var dimensions=new LinkedHashMap<>(server.getWorldGenSettings().dimensions().dimensions());dimensions.put(LevelStem.OVERWORLD,new LevelStem(root.type(),generator));
        var draft=PresetStudio.capture(PresetStudio.defaults(name,"1.5.0",new WorldOptions(18,true,false)),new WorldDimensions(dimensions),server.registryAccess());var json=draft.preset();var selected=new JsonObject();selected.addProperty("structure",id.toString());selected.addProperty("spacing",16);selected.addProperty("separation",6);var selections=new JsonArray();selections.add(selected);var dp=json.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").getAsJsonObject("dimension_profile");if(!dp.has("content"))dp.add("content",new JsonObject());dp.getAsJsonObject("content").add("structures",selections);
        var resources=new TreeMap<>(draft.templates());resources.put("data/"+id.getNamespace()+"/worldgen/structure/"+id.getPath()+".json",Structure.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),structure).getOrThrow().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return new PresetStudio.Draft(draft.manifest(),json,resources);
    }
    private static CompoundTag complete(SiteInspector.Input input){for(int i=0;i<3;i++){var report=SiteInspector.inspect(input);if(!report.getStringOr("status","").equals("incomplete"))return report;}throw new AssertionError("Small site never completed within the preview budget");}
    private static void water(MinecraftServer server)throws Exception{
        var e=stat("LibraryEntry","read",new NativeTemplate(new BlockPos(2,3,2),List.of(new NativeTemplate.Cell(BlockPos.ZERO,Blocks.BRICKS.defaultBlockState(),null))),new CompoundTag(),"Coastal remains","Validation");
        var collision=enumValue("Collision","AIR_OR_WATER");var r=call(e,"rules");e=call(e,"withRules",make("TemplateRules",call(r,"dimension"),call(r,"biome"),call(r,"minY"),call(r,"maxY"),call(r,"terrain"),collision));
        var id=Identifier.parse("validation:coastal_ruin");var draft=make("AssemblyDraft",id,0,Identifier.withDefaultNamespace("plains"),0,4,false,List.of(part("ruin",BlockPos.ZERO,Rotation.NONE,Mirror.NONE,e)),Optional.empty(),Optional.of(new PlacementAnchor(PlacementAnchor.Mode.SEABED,0)));
        var source=source(server,(Structure)call(draft,"structure",server),id,Optional.of(new ContentProfile.Sea(ContentProfile.Fluid.WATER,80,false)),"coastal");var checked=PresetStudio.validate(source,server.registryAccess());
        var report=complete(SiteInspector.prepare(checked,Identifier.withDefaultNamespace("overworld"),id,18,32,32,server.registryAccess(),server.getStructureTemplateManager()));require(report.getStringOr("status","").equals("eligible"),"Seabed water opt-in was rejected: "+report.getListOrEmpty("rows"));
        var denied=source.preset();var resources=new TreeMap<>(source.templates());var definition=JsonParser.parseString(new String(resources.get("data/validation/worldgen/structure/coastal_ruin.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();definition.getAsJsonArray("modules").get(0).getAsJsonObject().getAsJsonObject("template").getAsJsonObject("worldsunwritten").getAsJsonObject("rules").addProperty("collision","air");resources.put("data/validation/worldgen/structure/coastal_ruin.json",definition.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));var rejected=PresetStudio.validate(new PresetStudio.Draft(source.manifest(),denied,resources),server.registryAccess());
        require(complete(SiteInspector.prepare(rejected,Identifier.withDefaultNamespace("overworld"),id,18,32,32,server.registryAccess(),server.getStructureTemplateManager())).getStringOr("status","").equals("rejected"),"Air-only seabed placement was accepted");
        PresetStudio.save(ModpackRuntime.directory(),"coastal",source,server.registryAccess());System.out.println("WA15_BUNDLE coastal minecraft:overworld "+id+" "+PresetStudio.export(ModpackRuntime.directory(),"coastal","1.5.0",server.registryAccess()));
    }
    private static void polygonExport(MinecraftServer server)throws Exception{
        var original=PresetStudio.load(ModpackRuntime.directory(),"shared_regions","1.4.0");var preset=original.preset();var profile=preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").getAsJsonObject("profile");
        var region=profile.getAsJsonArray("terrain_regions").get(1).getAsJsonObject();region.addProperty("mask","polygon");var points=new JsonArray();for(int[] p:new int[][]{{-64,-64},{64,-64},{64,0},{32,0},{32,64},{-64,64}}){var v=new JsonObject();v.addProperty("x",p[0]);v.addProperty("z",p[1]);points.add(v);}region.add("vertices",points);profile.addProperty("generator_version",4);
        var manifest=PresetStudio.defaults("polygon_regions","1.5.0",original.manifest().options());var draft=new PresetStudio.Draft(manifest,preset,original.templates());PresetStudio.save(ModpackRuntime.directory(),"polygon_regions",draft,server.registryAccess());System.out.println("WA15_BUNDLE polygon minecraft:overworld validation:regional_marker "+PresetStudio.export(ModpackRuntime.directory(),"polygon_regions","1.5.0",server.registryAccess()));
    }
    static void generated(MinecraftServer server,String kind,Identifier id,Identifier dimension,int stage)throws Exception{
        if(kind.equals("polygon")){polygonGeneration(server,id,stage);return;}
        boolean pooled=kind.equals("vertical")||kind.equals("stress");long started=System.nanoTime();
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,dimension));require(level!=null,"Missing site dimension");var holder=server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,id));var state=level.getChunkSource().getGeneratorState();
        var receipt=server.getWorldPath(LevelResource.DATA).resolve("worldsunwritten-site15-"+UUID.nameUUIDFromBytes((id+"/"+dimension).getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json");
        var definition=Structure.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),holder.value()).getOrThrow().getAsJsonObject();
        JsonObject previous=Files.exists(receipt)?JsonParser.parseString(Files.readString(receipt)).getAsJsonObject():null;
        for(var placement:state.getPlacementsForStructure(holder))if(placement instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread){
            for(int attempt=0;attempt<4;attempt++){
                var candidate=previous==null?spread.getPotentialStructureChunk(state.getLevelSeed(),(32+attempt)*spread.spacing(),32*spread.spacing()):new ChunkPos(previous.get("x").getAsInt(),previous.get("z").getAsInt());
                var start=level.getChunk(candidate.x(),candidate.z()).getStartForStructure(holder.value());if(start==null||!start.isValid()){require(previous==null,"Saved site structure disappeared");continue;}
                var bounds=start.getBoundingBox();for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++)level.getChunk(x,z);
                if(pooled){var pool=definition.getAsJsonObject("assembly_pool");int minimum=1;if(pool.has("constraints")&&pool.getAsJsonObject("constraints").has("modules"))for(var count:pool.getAsJsonObject("constraints").getAsJsonArray("modules")){var c=count.getAsJsonObject();if(!c.get("module").getAsString().equals(pool.get("root").getAsString()))minimum+=c.has("min")?c.get("min").getAsInt():0;}require(start.getPieces().size()>=minimum&&start.getPieces().size()<=(pool.has("max_modules")?pool.get("max_modules").getAsInt():8),"Wrong site piece count");}else require(start.getPieces().size()==1,"Wrong coastal piece count");int cells=0,markers=0;boolean root=false;var signature=new StringBuilder();
                for(var piece:start.getPieces()){
                    var entry=call(piece,"entry");var origin=(BlockPos)call(piece,"origin");if(call(piece,"name").equals("root")){
                        var anchor=PlacementAnchor.CODEC.parse(JsonOps.INSTANCE,definition.get("placement")).getOrThrow();var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
                        int expected=anchor.resolve(generator.getBaseHeight(candidate.getMinBlockX(),candidate.getMinBlockZ(),Heightmap.Types.WORLD_SURFACE_WG,level,random),generator.getBaseHeight(candidate.getMinBlockX(),candidate.getMinBlockZ(),Heightmap.Types.OCEAN_FLOOR_WG,level,random),definition.has("y_offset")?definition.get("y_offset").getAsInt():0);require(origin.getY()==expected,"A lower module moved the entry anchor");root=true;
                    }
                    var template=(NativeTemplate)call(entry,"template");for(var cell:template.blocks()){
                        var p=origin.offset(cell.pos());require(level.getBlockState(p)==cell.state(),"Site generation lost state at "+p);
                        if(cell.metadata()!=null&&cell.metadata().contains("CustomName")){var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(p);require(chest.getName().getString().equals("Tower supplies")&&chest.isEmpty(),"Tower metadata or empty inventory changed");}cells++;
                    }
                    for(var marker:((Map<?,?>)call(entry,"markers")).values()){var p=origin.offset((BlockPos)call(marker,"pos"));require(level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir()&&!level.getBlockState(p.below()).isAir(),"Site marker unsafe");markers++;}
                    if(kind.equals("coastal"))require(level.getBlockState(origin.east().south()).is(Blocks.WATER),"Water placement drained an unfilled interior");
                    signature.append(call(piece,"name")).append(origin).append(call(entry,"toTag"));
                }
                require(!pooled||root&&markers==start.getPieces().size(),"Vertical entry or marker missing");
                int chunks=level.getChunkSource().getLoadedChunksCount();var source=PresetStudio.validate(PresetStudio.installedDraft().orElseThrow(),server.registryAccess());var input=SiteInspector.prepare(source,dimension,id,level.getSeed(),candidate.x(),candidate.z(),server.registryAccess(),server.getStructureTemplateManager());var report=kind.equals("stress")?SiteInspector.inspect(input):complete(input);
                require(report.getStringOr("status","").equals("eligible")||kind.equals("stress")&&report.getStringOr("status","").equals("incomplete"),"Site preview disagrees with native generation: "+report.getListOrEmpty("rows"));require(chunks==level.getChunkSource().getLoadedChunksCount(),"Site inspector loaded chunks");
                if(kind.equals("stress")){require(start.getPieces().size()==16&&((SolidColumnGenerator)level.getChunkSource().getGenerator()).terrainRegions().orElseThrow().zones().stream().mapToInt(z->z.vertices().size()).sum()==512,"Stress fixture did not reach its declared limits");System.out.println("WA15_STRESS_LIMITS_PASS vertices=512 modules=16 elapsed_ms="+(System.nanoTime()-started)/1_000_000+" heap_at_completion_mib="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576+" preview="+report.getStringOr("status",""));}
                String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(signature.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if(previous!=null)require(previous.get("sha256").getAsString().equals(hash),"Stored site templates, metadata or markers changed");else{var record=new JsonObject();record.addProperty("x",candidate.x());record.addProperty("z",candidate.z());record.addProperty("sha256",hash);Files.writeString(receipt,record+"\n",StandardOpenOption.CREATE_NEW);}
                System.out.println("WA15_GENERATION_PASS kind="+kind+" dimension="+dimension+" stage="+stage+" cells="+cells+" markers="+markers);return;
            }
        }throw new AssertionError("No complete site structure generated");
    }
    private static void polygonGeneration(MinecraftServer server,Identifier id,int stage)throws Exception{
        var level=server.overworld();var generator=(SolidColumnGenerator)level.getChunkSource().getGenerator();var regions=generator.terrainRegions().orElseThrow();require(regions.version()==4,"Polygon profile did not use version 4");var polygon=regions.zones().stream().filter(z->z.mask()==RegionalTerrain.Mask.POLYGON).findFirst().orElseThrow();
        var structure=server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,id)).value();
        var digest=java.security.MessageDigest.getInstance("SHA-256");long ores=0,decorations=0,diamonds=0,starts=0;
        for(int[] point:new int[][]{{-17,-1},{-16,-1},{-5,-1},{-4,-1},{3,-1},{4,-1},{15,-1},{16,-1},{17,-1},{-4,-4},{-3,-3},{0,0},{1,0},{0,1},{1,1},{2,1},{3,1},{3,3}}){
            var chunk=level.getChunk(point[0],point[1]);for(int x=point[0]*16;x<point[0]*16+16;x++)for(int z=point[1]*16;z<point[1]*16+16;z++){
                boolean inside=polygon.edgeDistance(x,z)>=0,meadow=x>=-256&&x<=256&&z>=-256&&z<=256&&!inside;
                for(int y=level.getMinY();y<=70;y++){
                    var p=new BlockPos(x,y,z);var state=chunk.getBlockState(p);digest.update(state.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if(state.is(Blocks.GOLD_ORE)){require(meadow&&y>=20&&y<=24,"Ore escaped polygon winner filtering");ores++;}
                    if(state.is(Blocks.AMETHYST_BLOCK)){require(meadow&&y==65,"Decoration escaped polygon winner filtering");decorations++;}
                    if(y>=60&&y<=64){require(state.is(Blocks.DIAMOND_BLOCK)==inside,"Material write escaped or missed polygon at "+p);if(inside)diamonds++;}
                }
                digest.update(level.getBiome(new BlockPos(x,64,z)).unwrapKey().orElseThrow().identifier().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            var start=chunk.getStartForStructure(structure);if(start!=null&&start.isValid()){starts++;for(var piece:start.getPieces()){var origin=(BlockPos)call(piece,"origin");for(var cell:((NativeTemplate)call(call(piece,"entry"),"template")).blocks()){var p=origin.offset(cell.pos());require(polygon.edgeDistance(p.getX(),p.getZ())>=0,"A structure cell escaped its polygon");}}}
        }
        require(ores>0&&decorations>0&&diamonds>0&&starts>0,"Polygon fixture missed a generation action");require(com.gwsheridan.worldsunwritten.rules.RuleSpawns.select(level,com.gwsheridan.worldsunwritten.rules.RuleSet.SpawnEvent.INITIAL).orElseThrow().position().equals(new BlockPos(16,65,16)),"Polygon spawn selected an outside policy");
        var receipt=server.getWorldPath(LevelResource.DATA).resolve("worldsunwritten-polygon15.txt");String hash=HexFormat.of().formatHex(digest.digest());if(stage==1)Files.writeString(receipt,hash,StandardOpenOption.CREATE_NEW);else require(Files.readString(receipt).equals(hash),"Polygon generation changed across restart");
        System.out.println("WA15_GENERATION_PASS kind=polygon stage="+stage+" ores="+ores+" decorations="+decorations+" diamonds="+diamonds+" starts="+starts);
    }
    private static void stressExport(MinecraftServer server,Object vertical)throws Exception{
        var pool=(ProceduralPool)((Optional<?>)call(vertical,"pool")).orElseThrow();pool=pool.withSettings("junction",16,256,37).withConstraints(new ProceduralPool.Constraints(15,List.of(new ProceduralPool.ModuleCount("middle",13,13),new ProceduralPool.ModuleCount("top",1,1),new ProceduralPool.ModuleCount("bottom",1,1))));
        var draft=call(call(call(vertical,"withPool",Optional.of(pool)),"withAnchor",Optional.of(new PlacementAnchor(PlacementAnchor.Mode.FIXED,220))),"withSettings",Identifier.withDefaultNamespace("plains"),0,32,true);
        var id=Identifier.parse("validation:stress_tower");var source=source(server,(Structure)call(draft,"structure",server),id,Optional.empty(),"stress_bounds");
        var land=new AdvancedTerrain(AdvancedTerrain.Landform.MOUNTAINS,128,0);var points=new ArrayList<RegionalTerrain.Vertex>();for(int i=0;i<64;i++){double a=i*Math.PI*2/64;points.add(new RegionalTerrain.Vertex((int)Math.round(10000*Math.cos(a)),(int)Math.round(10000*Math.sin(a))));}
        var zones=new ArrayList<RegionalTerrain.Zone>();for(int i=0;i<8;i++)zones.add(new RegionalTerrain.Zone("stress_"+i,RegionalTerrain.Mask.POLYGON,-10000,-10000,10000,10000,i,1024,64+i*10,80,land,Optional.empty(),points));
        var shape=new TerrainShape(64,80,Optional.of(land),Optional.of(new RegionalTerrain(zones)));var preset=source.preset();preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").add("profile",mergeProfile(preset,shape,server));
        var output=new PresetStudio.Draft(source.manifest(),preset,source.templates());PresetStudio.save(ModpackRuntime.directory(),"stress_bounds",output,server.registryAccess());System.out.println("WA15_BUNDLE stress minecraft:overworld "+id+" "+PresetStudio.export(ModpackRuntime.directory(),"stress_bounds","1.5.0",server.registryAccess()));
    }
    private static JsonObject mergeProfile(JsonObject preset,TerrainShape shape,MinecraftServer server){var profile=preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").getAsJsonObject("profile").deepCopy();var encoded=TerrainShape.CODEC.codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),shape).getOrThrow().getAsJsonObject();encoded.entrySet().forEach(e->profile.add(e.getKey(),e.getValue()));return profile;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object enumValue(String type,String name)throws Exception{return Arrays.stream(Class.forName("com.gwsheridan.worldsunwritten.structure.TemplateRules$"+type).getEnumConstants()).filter(v->v.toString().equals(name)).findFirst().orElseThrow();}
}

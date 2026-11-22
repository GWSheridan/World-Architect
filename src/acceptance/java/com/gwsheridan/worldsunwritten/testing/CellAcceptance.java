package com.gwsheridan.worldsunwritten.testing;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.gwsheridan.worldsunwritten.worldgen.*;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Dedicated assertions use production codecs, the loaded server and the persistent activation queue. */
final class CellAcceptance {
    static void register(RegisterCommandsEvent event){
        var root=Commands.literal("wa_cells_test").requires(Commands.hasPermission(Commands.LEVEL_OWNERS));
        for(String action:List.of("recipes","fresh","status","ready","conflict","restart","recover","multiplayer","multi_ready","multi_restart","pid","api_cells"))root.then(Commands.literal(action).executes(c->{try{check(c.getSource().getServer(),action);return 1;}catch(Exception|AssertionError error){throw new IllegalStateException("WA19_CELLS_FAILED "+action,error);}}));
        event.getDispatcher().register(root);
    }
    private static void check(MinecraftServer server,String action)throws Exception{
        if(action.equals("pid")){System.out.println("WA19_SERVER_PID "+ProcessHandle.current().pid());return;}
        if(action.equals("api_cells")){
            var id=net.minecraft.resources.Identifier.parse("wa_api_fixture:lab");var key=net.minecraft.resources.ResourceKey.create(Registries.LEVEL_STEM,id);var dimensions=server.getWorldGenSettings().dimensions();var stem=dimensions.dimensions().get(key);require(stem!=null,"External dimension fixture is missing");var material=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("wa_api_fixture:slate")).defaultBlockState();var source=CellRecipes.create("Hex Continents",server.registryAccess());var tiles=source.tiles().stream().map(t->new CellWorldProfile.Tile(t.id(),t.title(),t.weight(),t.biome(),t.kind(),new LayeredProfile(material,t.terrain().layers(),t.terrain().shape(),t.terrain().strata()),t.content(),t.template())).toList();var cells=new CellWorldProfile(source.layout(),source.salt(),source.start(),tiles,source.assignments(),source.border(),source.borderToFloor(),source.growth());var profile=new DimensionProfile(stem.type().value().minY(),stem.type().value().height(),Optional.of(new DimensionProfile.AdapterBinding(id,1)));var draft=PresetStudio.capture(PresetStudio.defaults("api_cells","2.0.0",server.getWorldGenSettings().options()),dimensions,server.registryAccess());draft=ModpackProject.dimension(draft,key,new net.minecraft.world.level.dimension.LevelStem(stem.type(),new CellGenerator(profile,cells)),server.registryAccess());PresetStudio.save(ModpackRuntime.directory(),"api_cells",draft,server.registryAccess());System.out.println("WA19_API_CELL_BUNDLE "+PresetStudio.export(ModpackRuntime.directory(),"api_cells","2.0.0",server.registryAccess()));return;
        }
        if(action.equals("recipes")){
            var unbound=net.minecraft.core.Holder.Reference.createStandAlone(new net.minecraft.core.HolderOwner<net.minecraft.world.level.levelgen.NoiseGeneratorSettings>(){},net.minecraft.world.level.levelgen.NoiseGeneratorSettings.END);
            var arenaConstructor=EndProgression.class.getDeclaredConstructor(net.minecraft.core.HolderGetter.class,net.minecraft.core.Holder.class,net.minecraft.core.Holder.class);arenaConstructor.setAccessible(true);
            var pendingArena=(EndProgression)arenaConstructor.newInstance(server.registryAccess().lookupOrThrow(Registries.NOISE),server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.THE_END),unbound);
            new CellGenerator(new DimensionProfile(0,256,Optional.empty()).withEndProgression(Optional.of(pendingArena)),CellRecipes.create("Hex Continents",server.registryAccess()));
            require(!unbound.isBound(),"Preset construction must not resolve pending End settings");
            var original=CellRecipes.create("Hex Continents",server.registryAccess());var start=original.tiles().stream().filter(t->t.id().equals(original.start())).findFirst().orElseThrow();var hill=new LayeredProfile(start.terrain().base(),start.terrain().layers(),new TerrainShape(68,80),List.of());var types=new ArrayList<>(original.tiles());types.replaceAll(t->t.id().equals(start.id())?new CellWorldProfile.Tile(t.id(),t.title(),t.weight(),t.biome(),t.kind(),hill,t.content(),t.template()):t);var raised=new CellWorldProfile(original.layout(),original.salt(),original.start(),types,original.assignments(),original.border(),original.borderToFloor(),original.growth());var preview=new CellGenerator(new DimensionProfile(-64,384,Optional.empty()),raised);var height=net.minecraft.world.level.LevelHeightAccessor.create(-64,384);var marker=preview.spawn(new Key(0,Cell.ORIGIN),height);for(long seed=0;seed<32;seed++){var column=preview.previewColumn(marker.getX(),marker.getZ(),height,seed);require(column.getBlock(marker.getY()).isAir()&&column.getBlock(marker.getY()+1).isAir(),"Terrain buried an embedded spawn marker");}
            var normal=server.registryAccess().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions();
            for(String name:CellRecipes.NAMES){String slug=CellRecipes.slug(name);var draft=PresetStudio.capture(PresetStudio.defaults(slug,"2.0.0",server.getWorldGenSettings().options()),normal,server.registryAccess());draft=ModpackProject.starter(draft,name,server.registryAccess());PresetStudio.save(ModpackRuntime.directory(),slug,draft,server.registryAccess());System.out.println("WA19_RECIPE "+slug+" "+PresetStudio.export(ModpackRuntime.directory(),slug,"2.0.0",server.registryAccess()));}
            for(var scope:List.of(GrowthRules.Scope.PLAYER,GrowthRules.Scope.TEAM)){
                String name="cell_"+scope.getSerializedName();var draft=PresetStudio.capture(PresetStudio.defaults(name,"2.0.0",server.getWorldGenSettings().options()),normal,server.registryAccess());draft=ModpackProject.starter(draft,"Expanding One Chunk",server.registryAccess());
                var preset=PresetStudio.validate(draft,server.registryAccess()).preset().createWorldDimensions();var stem=preset.dimensions().get(net.minecraft.world.level.dimension.LevelStem.OVERWORLD);var g=(CellGenerator)stem.generator();var p=g.cells();var goal=new GrowthRules.Goal("first","First quest",GrowthRules.Trigger.SIGNAL,net.minecraft.resources.Identifier.parse("validation:first"),List.of(),false,List.of(),2);var growth=new GrowthRules(scope,3,8,16384,true,List.of(goal));var profile=new CellWorldProfile(p.layout(),p.salt(),p.start(),p.tiles(),List.of(new CellWorldProfile.Assignment(new Cell(1,0),p.start())),p.border(),p.borderToFloor(),growth);draft=ModpackProject.dimension(draft,net.minecraft.world.level.dimension.LevelStem.OVERWORLD,new net.minecraft.world.level.dimension.LevelStem(stem.type(),new CellGenerator(g.dimension(),profile)),server.registryAccess());PresetStudio.save(ModpackRuntime.directory(),name,draft,server.registryAccess());System.out.println("WA19_FIXTURE "+name+" "+PresetStudio.export(ModpackRuntime.directory(),name,"2.0.0",server.registryAccess()));
            }
            var endDraft=PresetStudio.load(ModpackRuntime.directory(),"cell_team","2.0.0");endDraft=PresetStudio.duplicate(endDraft,"cell_team_end","2.0.0");var nativeEnd=normal.dimensions().get(net.minecraft.world.level.dimension.LevelStem.END);
            var endDimension=new DimensionProfile(0,256,Optional.empty()).withEndProgression(Optional.of(EndProgression.vanillaArena(server.registryAccess())));
            endDraft=ModpackProject.dimension(endDraft,net.minecraft.world.level.dimension.LevelStem.END,new net.minecraft.world.level.dimension.LevelStem(nativeEnd.type(),new CellGenerator(endDimension,CellRecipes.create("Hex Continents",server.registryAccess()))),server.registryAccess());PresetStudio.save(ModpackRuntime.directory(),"cell_team_end",endDraft,server.registryAccess());System.out.println("WA19_FIXTURE cell_team_end "+PresetStudio.export(ModpackRuntime.directory(),"cell_team_end","2.0.0",server.registryAccess()));
            System.out.println("WA19_RECIPES_PASS");return;
        }
        var level=server.overworld();require(level.getChunkSource().getGenerator() instanceof CellGenerator,"Cell generator missing");var generator=(CellGenerator)level.getChunkSource().getGenerator();var state=CellWorlds.state(level);require(state!=null,"Progress journal missing");
        if(action.startsWith("multi")){
            require(server.getPlayerList().getPlayers().size()==4,"Four real clients are required");var slots=new HashSet<Integer>();
            var checkedSlots=new HashSet<Integer>();for(var player:server.getPlayerList().getPlayers()){
                int slot=CellWorlds.territory(state,player,false);require(slot>0,"Player remained unassigned");slots.add(slot);require(state.ledger.status(new Key(slot,Cell.ORIGIN))==CellLedger.Status.READY,"Personal start is not ready");
                if(action.equals("multiplayer")){String owner=generator.cells().growth().scope()==GrowthRules.Scope.TEAM?"team:"+player.getTeam().getName():"player:"+player.getUUID();String command="wa_cells admin reassign "+slot+" "+owner;require(server.getCommands().getDispatcher().execute(command,server.createCommandSourceStack())==1,"Administrator reassignment syntax failed");try{server.getCommands().getDispatcher().execute(command,player.createCommandSourceStack());throw new AssertionError("A regular player reassigned territory");}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
                    com.gwsheridan.worldsunwritten.api.CellProgressionApi.signal(player,net.minecraft.resources.Identifier.parse("validation:first"));com.gwsheridan.worldsunwritten.api.CellProgressionApi.signal(player,net.minecraft.resources.Identifier.parse("validation:first"));require(state.ledger.credits(slot)==2,"Duplicate quest signal paid twice");}
                else{
                    require(state.ledger.credits(slot)==1,"Frontier reward was spent incorrectly");require(state.ledger.completed(slot).equals(Set.of("first")),"Completed goal changed");require(state.ledger.status(new Key(slot,new Cell(1,0)))==CellLedger.Status.READY,"Chosen cell did not finish");var marker=generator.spawn(new Key(slot,Cell.ORIGIN),level).above(5);
                    if(action.equals("multi_ready")){level.setBlock(marker,Blocks.EMERALD_BLOCK.defaultBlockState(),2);}else require(level.getBlockState(marker).is(Blocks.EMERALD_BLOCK),"Player build changed after restart");
                    if(checkedSlots.add(slot))for(var cell:List.of(Cell.ORIGIN,new Cell(1,0))){var key=new Key(slot,cell);var tile=generator.cells().tile(generator.seed(),slot,cell);var origin=generator.origin(tile,key);for(var block:tile.template().orElseThrow().cells())if(block.data().isPresent()){
                        var entity=level.getBlockEntity(origin.offset(block.pos()));require(entity instanceof net.minecraft.world.Container,"Starter container metadata missing");var container=(net.minecraft.world.Container)entity;for(var value:block.data().orElseThrow().getListOrEmpty("Items")){var item=(net.minecraft.nbt.CompoundTag)value;var expected=net.minecraft.world.item.ItemStack.CODEC.parse(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,server.registryAccess()),item).getOrThrow();int index=item.getIntOr("Slot",-1),count=expected.getCount()-(action.equals("multi_restart")?1:0);require(container.getItem(index).getCount()==count,"Starter inventory changed or refilled");if(action.equals("multi_ready")){container.removeItem(index,1);entity.setChanged();}}
                    }}
                }
            }
            require(slots.size()==(generator.cells().growth().scope()==GrowthRules.Scope.TEAM?2:4),"Independent territory ownership changed");System.out.println("WA19_"+action.toUpperCase(Locale.ROOT)+"_PASS");return;
        }
        if(action.equals("status")){System.out.println("WA19_CELL_STATUS "+state.ledger.cells());return;}
        if(action.equals("fresh")){
            var write=CellWorlds.class.getDeclaredMethod("write",net.minecraft.server.level.ServerLevel.class,Key.class,BlockPos.class,net.minecraft.world.level.block.state.BlockState.class,Runnable.class);write.setAccessible(true);var target=new BlockPos(8,90,8);var old=level.getBlockState(target);var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);
            write.invoke(null,level,new Key(0,Cell.ORIGIN),target,Blocks.STONE.defaultBlockState(),(Runnable)()->{
                require(level.setBlock(target,Blocks.STONE.defaultBlockState(),2),"Expected activation write was refused");
                require(!level.setBlock(target,Blocks.GOLD_BLOCK.defaultBlockState(),2),"A callback replaced the activation block");
                require(!level.setBlock(target.east(),Blocks.GOLD_BLOCK.defaultBlockState(),2),"A callback changed an existing cell");
                require(!nether.setBlock(target,Blocks.GOLD_BLOCK.defaultBlockState(),2),"A callback wrote into another dimension");
            });level.setBlock(target,old,2);
            require(generator.cells().layout().size()==16,"Expected chunk-sized cells");
            long occupied=0;for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=level.getMinY();y<=level.getMaxY();y++)if(!level.getBlockState(new BlockPos(x,y,z)).isAir())occupied++;
            require(occupied>20000,"Starting chunk was not generated");
            for(int[] origin:List.of(new int[]{16,0},new int[]{-16,0},new int[]{0,16},new int[]{0,-16}))for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=level.getMinY();y<=level.getMaxY();y++)require(level.getBlockState(new BlockPos(origin[0]+x,y,origin[1]+z)).isAir(),"Locked cell is not true void");
            require(!level.setBlock(new BlockPos(18,70,2),Blocks.GOLD_BLOCK.defaultBlockState(),2),"Locked write was accepted");state.ledger.queue(new Key(0,new Cell(1,0)));System.out.println("WA19_TRUE_VOID_PASS occupied="+occupied);return;
        }
        if(action.equals("ready")){
            require(state.ledger.status(new Key(0,new Cell(1,0)))==CellLedger.Status.READY,"Activation did not finish");require(!level.getBlockState(new BlockPos(20,50,8)).isAir(),"Saved void chunk did not materialize");
            level.setBlock(new BlockPos(8,75,8),Blocks.EMERALD_BLOCK.defaultBlockState(),2);var conflict=new BlockPos(40,70,8);level.getChunkAt(conflict).setBlockState(conflict,Blocks.GOLD_BLOCK.defaultBlockState(),2);state.ledger.queue(new Key(0,new Cell(2,0)));System.out.println("WA19_ACTIVATION_PASS");return;
        }
        if(action.equals("conflict")){
            require(state.ledger.status(new Key(0,new Cell(2,0)))==CellLedger.Status.CONFLICT,"Unexpected player block did not stop activation");require(level.getBlockState(new BlockPos(40,70,8)).is(Blocks.GOLD_BLOCK),"Conflict block was overwritten");
            state.ledger.queue(new Key(0,new Cell(0,1)));System.out.println("WA19_CONFLICT_PRESERVED_PASS");return;
        }
        if(action.equals("restart")){
            require(level.getBlockState(new BlockPos(8,75,8)).is(Blocks.EMERALD_BLOCK),"Existing build changed");require(level.getBlockState(new BlockPos(40,70,8)).is(Blocks.GOLD_BLOCK),"Conflict block changed");require(state.ledger.status(new Key(0,new Cell(1,0)))==CellLedger.Status.READY,"Ready state changed");System.out.println("WA19_RESTART_PASS");return;
        }
        if(action.equals("recover")){require(state.ledger.status(new Key(0,new Cell(0,1)))==CellLedger.Status.READY,"Interrupted cell did not recover");require(level.getBlockState(new BlockPos(8,50,20)).is(Blocks.STONE)||!level.getBlockState(new BlockPos(8,50,20)).isAir(),"Recovery terrain missing");System.out.println("WA19_RECOVERY_PASS");}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

package com.gwsheridan.worldarchitect.cells;

import com.gwsheridan.worldarchitect.api.CellLayout;
import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.structure.IslandBuild;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.*;

/** Complete, editable starters. Geometry and terrain are implemented independently of other mods. */
public final class CellRecipes {
    public static final List<String> NAMES=List.of("Hex Continents","Hex Skyblock","Expanding One Chunk","Resource Archipelago","Biome Mosaic","Underground Start");
    private CellRecipes(){}
    public static String slug(String name){return name.toLowerCase(Locale.ROOT).replace(' ','_');}
    public static CellWorldProfile create(String name,HolderLookup.Provider registries){
        if(!NAMES.contains(name))throw new IllegalArgumentException("Unknown cell recipe.");
        boolean progressive=name.equals("Expanding One Chunk")||name.equals("Underground Start"),islands=name.equals("Hex Skyblock")||name.equals("Resource Archipelago");
        var layout=new CellLayout(progressive||name.equals("Biome Mosaic")?CellLayout.Shape.SQUARE:CellLayout.Shape.POINTY_HEX,progressive?16:32,progressive?0:islands?7:1,islands||progressive?0:1);
        var biomes=registries.lookupOrThrow(Registries.BIOME);var tiles=new ArrayList<CellWorldProfile.Tile>();
        var ore=new ContentProfile.Ore(Blocks.IRON_ORE.defaultBlockState(),Optional.of(Blocks.STONE.defaultBlockState()),12,5,-48,48,List.of());
        var coal=new ContentProfile.Ore(Blocks.COAL_ORE.defaultBlockState(),Optional.of(Blocks.STONE.defaultBlockState()),16,8,-48,64,List.of());
        var content=ContentProfile.EMPTY.withOres(List.of(coal,ore));
        for(int index=0;index<3;index++){
            String id=(name.equals("Resource Archipelago")?List.of("coal","iron","copper"):List.of("meadow","desert","taiga")).get(index);var biome=biomes.getOrThrow(index==0?Biomes.PLAINS:index==1?Biomes.DESERT:Biomes.SNOWY_TAIGA);
            var ground=index==1?Blocks.SANDSTONE.defaultBlockState():Blocks.GRASS_BLOCK.defaultBlockState();
            var layers=List.of(new LayeredProfile.Layer(ground,1),new LayeredProfile.Layer(index==1?Blocks.SANDSTONE.defaultBlockState():Blocks.DIRT.defaultBlockState(),3));
            var terrain=new LayeredProfile(Blocks.STONE.defaultBlockState(),layers,new TerrainShape(68,progressive||islands?0:12),List.of());
            var template=islands?Optional.of(resourceIsland(index)):Optional.<IslandBuild>empty();
            var kind=islands?CellWorldProfile.Kind.ISLAND:name.equals("Underground Start")?CellWorldProfile.Kind.CAVERN:CellWorldProfile.Kind.TERRAIN;
            var rules=islands?ContentProfile.EMPTY:name.equals("Hex Continents")?content.withSea(Optional.of(new ContentProfile.Sea(ContentProfile.Fluid.WATER,64,false))):content;
            String title=(name.equals("Resource Archipelago")?List.of("Coal island","Iron island","Copper island"):List.of("Meadow","Desert","Taiga")).get(index);
            tiles.add(new CellWorldProfile.Tile(id,title,index==0?3:1,biome,kind,terrain,rules,template));
        }
        var base=tiles.getFirst();var startTerrain=new LayeredProfile(base.terrain().base(),base.terrain().layers(),new TerrainShape(68,0),List.of());
        tiles.add(new CellWorldProfile.Tile("start","Starting cell",0,base.biome(),base.kind(),startTerrain,base.content(),Optional.of(starter(registries))));
        var goals=new ArrayList<GrowthRules.Goal>();String previous="";int number=0;
        for(String advancement:List.of("story/root","story/mine_stone","story/upgrade_tools","story/smelt_iron","story/iron_tools","story/mine_diamond","story/enter_the_nether")){
            String id="stage_"+(++number);goals.add(new GrowthRules.Goal(id,List.of("Begin your world","Gather stone","Make better tools","Smelt iron","Craft iron tools","Find diamonds","Reach the Nether").get(number-1),GrowthRules.Trigger.ADVANCEMENT,Identifier.withDefaultNamespace(advancement),previous.isEmpty()?List.of():List.of(previous),false,List.of(),number<4?1:2));previous=id;
        }
        var growth=progressive?new GrowthRules(GrowthRules.Scope.SHARED,6,64,4096,false,goals):GrowthRules.STATIC;
        return new CellWorldProfile(layout,0,"start",tiles,List.of(),Blocks.STONE_BRICKS.defaultBlockState(),!islands,growth).validate().getOrThrow();
    }
    private static IslandBuild resourceIsland(int index){var build=IslandBuild.classic();var material=index==0?Blocks.COAL_ORE:index==1?Blocks.IRON_ORE:Blocks.COPPER_ORE;return new IslandBuild(build.size(),build.markers(),build.spawnMarker(),build.cells().stream().map(c->c.state().is(Blocks.STONE)?new IslandBuild.Cell(c.pos(),material.defaultBlockState(),Optional.empty()):c).toList());}
    private static IslandBuild starter(HolderLookup.Provider registries){
        var build=IslandBuild.classic();var cells=new ArrayList<>(build.cells());var tag=new CompoundTag();tag.putString("id","minecraft:chest");var items=new ListTag();int slot=0;
        for(var stack:List.of(new ItemStack(Items.OAK_LOG,16),new ItemStack(Items.OAK_SAPLING,2),new ItemStack(Items.DIRT,16),new ItemStack(Items.WATER_BUCKET),new ItemStack(Items.LAVA_BUCKET),new ItemStack(Items.BONE_MEAL,8),new ItemStack(Items.TORCH,8))){var item=(CompoundTag)ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,registries),stack).getOrThrow();item.putByte("Slot",(byte)slot++);items.add(item);}
        tag.put("Items",items);cells.add(new IslandBuild.Cell(new BlockPos(2,4,4),Blocks.CHEST.defaultBlockState(),Optional.of(tag)));return new IslandBuild(build.size(),build.markers(),build.spawnMarker(),cells).validate().getOrThrow();
    }
}

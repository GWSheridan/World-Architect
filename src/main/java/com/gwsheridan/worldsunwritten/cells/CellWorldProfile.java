package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.api.CellLayout;
import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.structure.IslandBuild;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Version-one cellular worlds are opt-in. Palette, geometry and objectives are pinned with the world. */
public record CellWorldProfile(CellLayout layout,long salt,String start,List<Tile> tiles,List<Assignment> assignments,BlockState border,boolean borderToFloor,GrowthRules growth) {
    public static final int TERRITORY_SPACING=262144;
    public CellWorldProfile {tiles=List.copyOf(tiles);assignments=List.copyOf(assignments);}
    public static final Codec<CellWorldProfile> CODEC=RecordCodecBuilder.<CellWorldProfile>create(b->b.group(
            CellLayout.CODEC.fieldOf("layout").forGetter(CellWorldProfile::layout),Codec.LONG.optionalFieldOf("seed_salt",0L).forGetter(CellWorldProfile::salt),
            Codec.STRING.fieldOf("starting_type").forGetter(CellWorldProfile::start),Tile.CODEC.listOf(1,16).fieldOf("types").forGetter(CellWorldProfile::tiles),
            Assignment.CODEC.listOf(0,1024).optionalFieldOf("assignments",List.of()).forGetter(CellWorldProfile::assignments),
            TerrainMaterial.CODEC.optionalFieldOf("border",Blocks.STONE_BRICKS.defaultBlockState()).forGetter(CellWorldProfile::border),
            Codec.BOOL.optionalFieldOf("border_to_floor",false).forGetter(CellWorldProfile::borderToFloor),
            GrowthRules.CODEC.optionalFieldOf("growth",GrowthRules.STATIC).forGetter(CellWorldProfile::growth)
    ).apply(b,CellWorldProfile::new)).validate(CellWorldProfile::validate);
    public DataResult<CellWorldProfile> validate(){
        var issue=layout.validate().error();if(issue.isPresent())return DataResult.error(()->issue.orElseThrow().message());
        var rules=growth.validate().error();if(rules.isPresent())return DataResult.error(()->rules.orElseThrow().message());
        var ids=new HashSet<String>();for(var tile:tiles){if(!ids.add(tile.id)||!tile.id.matches("[a-z][a-z0-9_-]{0,31}")||tile.title.isBlank()||tile.title.length()>80||tile.weight<0||tile.weight>100)return DataResult.error(()->"Use 1–16 distinct cell types with titles and weights.");if(tile.terrain.validate().error().isPresent())return DataResult.error(()->"Invalid layered terrain in "+tile.id);if(tile.kind==Kind.ISLAND&&tile.template.isEmpty())return DataResult.error(()->"An island cell requires its embedded template.");
            if(tile.template.isPresent()){var build=tile.template.orElseThrow();if(build.validate().error().isPresent())return DataResult.error(()->"Invalid cell template "+tile.id);int x=build.size().getX(),z=build.size().getZ();var c=layout.centre(Cell.ORIGIN);for(int dx:new int[]{-x/2,(x-1)/2})for(int dz:new int[]{-z/2,(z-1)/2})if(!layout.contains(Cell.ORIGIN,(int)StrictMath.floor(c.x())+dx,(int)StrictMath.floor(c.z())+dz))return DataResult.error(()->"The complete template must fit inside its cell and gap.");}
        }
        if(tiles.isEmpty()||tiles.size()>16||!ids.contains(start))return DataResult.error(()->"Select an existing starting cell type.");
        if(tiles.stream().mapToInt(Tile::weight).sum()==0)return DataResult.error(()->"Give at least one random cell type a positive weight. Weight zero is reserved for explicit assignments and the starting cell.");
        var assigned=new HashSet<Cell>();for(var a:assignments)if(a.cell.equals(Cell.ORIGIN)||!ids.contains(a.type)||!assigned.add(a.cell))return DataResult.error(()->"Cell assignments need distinct non-origin coordinates and existing types. Select the origin through starting_type.");
        if(growth.scope()!=GrowthRules.Scope.STATIC){if(layout.size()>64)return DataResult.error(()->"Progressive cells support sizes up to 64 blocks.");for(var goal:growth.goals())for(var cell:goal.targets())if(layout.distance(cell)>growth.radius())return DataResult.error(()->"An objective targets a cell outside the saved territory radius.");for(var a:assignments)if(layout.distance(a.cell)>growth.radius())return DataResult.error(()->"Assigned cells must fit the saved territory radius.");}
        return DataResult.success(this);
    }
    public boolean progressive(){return growth.scope()!=GrowthRules.Scope.STATIC;}
    public Tile tile(long seed,int territory,Cell cell){
        String id=assignments.stream().filter(a->a.cell.equals(cell)).map(Assignment::type).findFirst().orElse(cell.equals(Cell.ORIGIN)?start:"");
        if(!id.isEmpty())return tiles.stream().filter(t->t.id.equals(id)).findFirst().orElseThrow();
        long n=mix(seed^salt^((long)territory*0x632be59bd9b4e019L)^((long)cell.q()*0x9e3779b97f4a7c15L)^((long)cell.r()*0xc2b2ae3d27d4eb4fL));int choice=(int)Long.remainderUnsigned(n,tiles.stream().mapToInt(Tile::weight).sum());for(var tile:tiles){choice-=tile.weight;if(choice<0)return tile;}throw new AssertionError();
    }
    public Address address(int x,int z){
        if(!progressive())return new Address(0,layout.at(x,z),x,z);
        int column=Math.floorDiv(x+TERRITORY_SPACING/2,TERRITORY_SPACING),row=Math.floorDiv(z+TERRITORY_SPACING/2,TERRITORY_SPACING);
        if(column<0||column>15||row<0||row>15)return null;int slot=row*16+column;
        if(slot>growth.territories()||growth.scope()==GrowthRules.Scope.SHARED&&slot!=0)return null;
        int localX=x-column*TERRITORY_SPACING,localZ=z-row*TERRITORY_SPACING;var cell=layout.at(localX,localZ);
        return layout.distance(cell)<=growth.radius()?new Address(slot,cell,localX,localZ):null;
    }
    public BlockPos offset(int territory){return new BlockPos(territory%16*TERRITORY_SPACING,0,territory/16*TERRITORY_SPACING);}
    public static long mix(long n){n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    public enum Kind implements StringRepresentable {TERRAIN,ISLAND,CAVERN;public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public record Address(int territory,Cell cell,int localX,int localZ){}
    public record Tile(String id,String title,int weight,Holder<Biome> biome,Kind kind,LayeredProfile terrain,ContentProfile content,Optional<IslandBuild> template){
        public static final Codec<Tile> CODEC=RecordCodecBuilder.create(b->b.group(
                Codec.STRING.fieldOf("id").forGetter(Tile::id),Codec.STRING.fieldOf("title").forGetter(Tile::title),Codec.intRange(0,100).optionalFieldOf("weight",1).forGetter(Tile::weight),
                Biome.CODEC.fieldOf("biome").forGetter(Tile::biome),StringRepresentable.fromEnum(Kind::values).optionalFieldOf("kind",Kind.TERRAIN).forGetter(Tile::kind),
                CellTerrain.CODEC.fieldOf("terrain").forGetter(Tile::terrain),ContentProfile.CODEC.optionalFieldOf("content",ContentProfile.EMPTY).forGetter(Tile::content),IslandBuild.CODEC.optionalFieldOf("template").forGetter(Tile::template)
        ).apply(b,Tile::new));
    }
    public record Assignment(Cell cell,String type){public static final Codec<Assignment> CODEC=RecordCodecBuilder.create(b->b.group(Cell.CODEC.fieldOf("cell").forGetter(Assignment::cell),Codec.STRING.fieldOf("type").forGetter(Assignment::type)).apply(b,Assignment::new));}
}

package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.config.AdvancedTerrain;
import com.gwsheridan.worldsunwritten.config.TerrainShape;
import com.gwsheridan.worldsunwritten.rules.RuleSet;
import com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV2;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.level.LevelHeightAccessor;

/** Audits stored chunks against independent filter boundaries and persisted template cells. */
public final class RuleWorldAudit {
    private RuleWorldAudit() {}
    private record Cell(int x,int y,int z) {}
    public static void main(String[] args)throws Exception {
        Path world=Path.of(args[0]);boolean cityExpected=Boolean.parseBoolean(args[1]);
        var snapshot=NbtIo.readCompressed(world.resolve("data/worldsunwritten-generation-v1.dat"),NbtAccounter.unlimitedHeap());
        var settings=NbtIo.readCompressed(world.resolve("data/minecraft/world_gen_settings.dat"),NbtAccounter.unlimitedHeap());
        require(settings.getCompoundOrEmpty("data").equals(snapshot.getCompoundOrEmpty("data")),"Generation snapshot mismatch");
        var generator=settings.getCompoundOrEmpty("data").getCompoundOrEmpty("dimensions").getCompoundOrEmpty("minecraft:overworld").getCompoundOrEmpty("generator");
        var engine=generator.getCompoundOrEmpty("dimension_profile").getCompoundOrEmpty("rule_engine");
        require(engine.getIntOr("version",0)==1,"Missing engine version");
        require(snapshot.getCompoundOrEmpty("generation_dependencies").getCompoundOrEmpty("rule_mods").getStringOr("wa_missing_dependency","").equals("absent"),"Missing dependency pin");
        var rules=engine.getListOrEmpty("rules");String blue="";int radius=0;boolean lower=false;
        for(var value:rules){var rule=(CompoundTag)value;String id=rule.getStringOr("id","");if(id.equals("blue"))blue=rule.getCompoundOrEmpty("action").getStringOr("material","");if(id.equals("crystals"))radius=rule.getCompoundOrEmpty("when").getCompoundOrEmpty("distance").getIntOr("max",0);if(id.equals("lower_city"))lower=true;}
        require(!blue.isEmpty(),"Missing material rule");
        var cache=new HashMap<String,CompoundTag>();var overlays=new HashMap<Cell,String>();
        var origin=CrossDimensionWorldAudit.chunk(world,"minecraft:overworld",0,0,cache);
        var start=origin.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").getCompoundOrEmpty("wa_example:rule_city");
        long templateCells=0;int markers=0,containers=0;
        if(cityExpected){
            require(start.getStringOr("id","").equals("wa_example:rule_city"),"City did not generate");
            require(start.getListOrEmpty("Children").size()==8,"Expected eight city modules");
            for(var value:start.getListOrEmpty("Children")){
                var piece=(CompoundTag)value;int[] bounds=piece.getIntArray("BB").orElseThrow();require(bounds[1]==97,"Wrong city height");
                var template=piece.getCompoundOrEmpty("template");var palette=template.getListOrEmpty("palette");
                for(var cellValue:template.getListOrEmpty("blocks")){
                    var cell=(CompoundTag)cellValue;var p=cell.getListOrEmpty("pos");int x=bounds[0]+p.getIntOr(0,0),y=bounds[1]+p.getIntOr(1,0),z=bounds[2]+p.getIntOr(2,0);
                    var expected=palette.getCompoundOrEmpty(cell.getInt("state").orElseThrow());var chunk=CrossDimensionWorldAudit.chunk(world,"minecraft:overworld",x,z,cache);
                    require(expected.equals(CrossDimensionWorldAudit.block(chunk,x,y,z)),"Wrong template state at "+x+","+y+","+z);
                    overlays.put(new Cell(x,y,z),expected.getStringOr("id",""));templateCells++;
                    if(cell.contains("nbt")){
                        var data=cell.getCompoundOrEmpty("nbt");var entity=CrossDimensionWorldAudit.entity(chunk,x,y,z);
                        require(data.get("CustomName")!=null&&data.get("CustomName").toString().contains("Sky City Supplies"),"Lost source chest name");containers++;
                        for(String key:data.keySet())require(java.util.Objects.equals(data.get(key),entity.get(key)),"Metadata changed: "+key);
                    }
                }
                var marker=template.getCompoundOrEmpty("worldsunwritten").getCompoundOrEmpty("markers").getCompoundOrEmpty("entry").getListOrEmpty("pos");
                int x=bounds[0]+marker.getIntOr(0,0),y=bounds[1]+marker.getIntOr(1,0),z=bounds[2]+marker.getIntOr(2,0);var chunk=CrossDimensionWorldAudit.chunk(world,"minecraft:overworld",x,z,cache);
                require(CrossDimensionWorldAudit.block(chunk,x,y,z).getStringOr("id","").equals("minecraft:air"),"Occupied marker");
                require(CrossDimensionWorldAudit.block(chunk,x,y+1,z).getStringOr("id","").equals("minecraft:air"),"Blocked marker headroom");markers++;
            }
        } else require(start.isEmpty()||start.getStringOr("id","").equals("INVALID"),"Whole-assembly filter leaked city pieces");
        if(lower){var rejected=origin.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").getCompoundOrEmpty("wa_example:lower_city");require(rejected.isEmpty()||rejected.getStringOr("id","").equals("INVALID"),"Lower priority city generated");}
        var terrain=generator.getCompoundOrEmpty("profile").getCompoundOrEmpty("terrain");
        var advanced=new AdvancedTerrain(AdvancedTerrain.Landform.FLOATING_ISLANDS,terrain.getIntOr("scale",256),terrain.getLongOr("seed_salt",0));
        var shape=new TerrainShape(96,0,Optional.of(advanced));var height=LevelHeightAccessor.create(-64,384);
        long seed=settings.getCompoundOrEmpty("data").getLong("seed").orElseThrow();long positions=0;int ores=0,decorations=0,gold=0,air=0;
        var deterministic=java.security.MessageDigest.getInstance("SHA-256");
        var chunks=new java.util.ArrayList<>(java.util.Arrays.asList(new int[][]{{-4,0},{-1,0},{0,0},{1,0},{2,0},{3,0},{0,1},{1,1},{2,1},{3,1},{0,2},{1,2},{2,2},{3,2},{4,0},{0,-1},{96,96}}));
        if(args.length>2&&!args[2].isBlank())for(String point:args[2].split(";")){String[] xy=point.split(",");if(xy.length!=2)throw new IllegalArgumentException("Extra chunks use x,z pairs separated by semicolons.");chunks.add(new int[]{Integer.parseInt(xy[0]),Integer.parseInt(xy[1])});}
        for(int[] coordinate:chunks){
            int cx=coordinate[0],cz=coordinate[1];var chunk=CrossDimensionWorldAudit.chunk(world,"minecraft:overworld",cx*16,cz*16,cache);
            var sections=FeatureWorldAudit.sections(chunk,-64,384,"block_states",4);
            if((cx!=0||cz!=0)&&cx%4==0&&cz%4==0){var rejected=chunk.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").getCompoundOrEmpty("wa_example:rule_city");require(rejected.isEmpty()||rejected.getStringOr("id","").equals("INVALID"),"Region filter leaked a city outside its candidate region");}
            for(int lz=0;lz<16;lz++)for(int lx=0;lx<16;lx++){
                int x=cx*16+lx,z=cz*16+lz;var column=HeightfieldV2.sample(shape,advanced,seed,x,z,height);
                for(int y=-64;y<320;y++){
                    String actual=FeatureWorldAudit.block(sections,-64,y,lz*16+lx);String expected=y>=column.bottom()&&y<=column.top()?"minecraft:stone":"minecraft:air";
                    if(!expected.equals("minecraft:air")&&y>=92&&y<=96&&x>=-64&&x<=64&&z>=-32&&z<=64)expected=x<0&&(long)x*x+(long)z*z<=10000?"minecraft:gold_block":blue;
                    String overlay=overlays.get(new Cell(x,y,z));if(overlay!=null)expected=overlay;
                    if(actual.equals("minecraft:redstone_block")){
                        require(expected.equals(blue)&&x>=0&&x<=63&&z>=0&&z<=31&&y>=94&&y<=95,"Ore escaped conditions");ores++;
                    } else if(actual.equals("minecraft:amethyst_block")){
                        long dx=x-56L,dz=z-16L;
                        require(expected.equals("minecraft:air")&&y==97&&x>=48&&x<=63&&z>=0&&z<=31&&dx*dx+dz*dz<=(long)radius*radius&&Math.floorMod(RuleSet.hash(seed,x,z,7),4)==0,"Decoration escaped a region, height, distance or seed condition");decorations++;
                    } else require(expected.equals(actual),"Unexpected block at "+x+","+y+","+z+": "+actual+" expected "+expected);
                    if(cx<3&&cz<2)deterministic.update(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if(actual.equals("minecraft:gold_block"))gold++;if(actual.equals("minecraft:air"))air++;positions++;
                }
            }
        }
        require(ores>0&&decorations>0&&gold>0&&air>0,"Expected positive feature, material and void samples");
        require(containers==(cityExpected?6:0),"Wrong number of named city containers");
        System.out.printf("Verified %,d rule-filtered positions, %,d template cells, %d markers, %d ore cells and %d decorations; strict filters and priority passed.%n",positions,templateCells,markers,ores,decorations);
        System.out.println("RULE_SAMPLE_SHA256 "+java.util.HexFormat.of().formatHex(deterministic.digest()));
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

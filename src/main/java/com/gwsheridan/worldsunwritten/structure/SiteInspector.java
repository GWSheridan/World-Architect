package com.gwsheridan.worldsunwritten.structure;

import com.gwsheridan.worldsunwritten.modpack.PresetStudio;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Detached production-planner inspection. It never accesses a level or creates a chunk. */
public final class SiteInspector {
    public record Input(Holder<Structure> structure,ResourceKey<Level> dimension,SolidColumnGenerator generator,RandomState random,
                        RegistryAccess registries,StructureTemplateManager templates,LevelHeightAccessor height,long seed,ChunkPos chunk,
                        boolean automatic,ChunkPos potential) {}
    private SiteInspector(){}
    public static Input prepare(PresetStudio.Checked source,Identifier dimension,Identifier selected,long seed,int chunkX,int chunkZ,
                                RegistryAccess registries,StructureTemplateManager templates)throws IOException{
        if(Math.abs((long)chunkX)*16>29_999_792||Math.abs((long)chunkZ)*16>29_999_792)throw new IOException("Keep candidate chunks inside the world border with room for the assembly.");
        var stem=source.preset().createWorldDimensions().dimensions().get(ResourceKey.create(Registries.LEVEL_STEM,dimension));
        if(stem==null||!(stem.generator() instanceof SolidColumnGenerator generator))throw new IOException("Select a managed solid-terrain dimension.");
        var holder=source.registries().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE,selected)).orElseThrow(()->new IOException("Missing structure "+selected));
        if(!(holder.value() instanceof ModularStructure))throw new IOException("Site inspection supports Worlds Unwritten assemblies.");
        var placements=placements(source,dimension,selected);var placement=placements.getFirst();
        var chunk=new ChunkPos(chunkX,chunkZ);var potential=placement.getPotentialStructureChunk(seed,chunkX,chunkZ);
        var random=RandomState.create(source.registries().lookupOrThrow(Registries.NOISE),seed,source.registries().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value());
        var type=stem.type().value();return new Input(holder,ResourceKey.create(Registries.DIMENSION,dimension),generator,random,registries,templates,
                LevelHeightAccessor.create(type.minY(),type.height()),seed,chunk,source.draft().manifest().options().generateStructures()&&placements.stream().anyMatch(p->p.getPotentialStructureChunk(seed,chunkX,chunkZ).equals(chunk)),potential);
    }
    public static List<RandomSpreadStructurePlacement> placements(PresetStudio.Checked source,Identifier dimension,Identifier selected)throws IOException{
        var stem=source.preset().createWorldDimensions().dimensions().get(ResourceKey.create(Registries.LEVEL_STEM,dimension));if(stem==null||!(stem.generator() instanceof SolidColumnGenerator generator))throw new IOException("Select a managed solid-terrain dimension.");
        var holder=source.registries().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE,selected)).orElseThrow(()->new IOException("Missing assembly."));
        var result=Stream.concat(generator.structureRules().stream(),generator.rules().ordered().stream().flatMap(r->r.action().template().stream())).filter(r->r.structure().value()==holder.value()).map(r->StructureDistricts.placement(r)).toList();
        if(result.isEmpty())throw new IOException("This assembly is not selected in that preset dimension.");return result;
    }
    public static CompoundTag inspect(Input input){
        return inspect(input,System.nanoTime()+500_000_000L);
    }
    static CompoundTag inspect(Input input,long deadline){
        var data=new CompoundTag();data.putString("kind","site");data.putString("dimension",input.dimension.identifier().toString());data.putString("structure",input.structure.unwrapKey().orElseThrow().identifier().toString());
        data.putString("seed",Long.toString(input.seed));data.putInt("chunk_x",input.chunk.x());data.putInt("chunk_z",input.chunk.z());
        var trace=new SiteProbe(deadline);var previous=SiteProbe.CURRENT.get();SiteProbe.CURRENT.set(trace);boolean eligible=false,incomplete=false;
        var rows=new ArrayList<String>();
        try{
            var start=input.structure.value().generate(input.structure,input.dimension,input.registries,input.generator,input.generator.getBiomeSource(),input.random.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED),input.random,
                    input.templates,input.seed,input.chunk,0,input.height,input.structure.value().biomes()::contains);
            eligible=start.isValid();SiteProbe.check();geometry(data,trace,input,rows);
        }catch(SiteProbe.Incomplete e){incomplete=true;rows.add("Sampling is incomplete: the request was cancelled or reached its 500 ms work budget.");}
        catch(RuntimeException e){rows.add("Inspection failed: "+Objects.toString(e.getMessage(),"Invalid generation inputs"));incomplete=true;}
        finally{if(previous==null)SiteProbe.CURRENT.remove();else SiteProbe.CURRENT.set(previous);}
        data.putString("status",incomplete?"incomplete":eligible?"eligible":"rejected");
        rows.addFirst(incomplete?"No placement decision: sampling incomplete.":eligible?"Production planner accepted the geometry.":trace.reason);
        rows.add("Native candidate in this placement region: "+input.potential.x()+", "+input.potential.z()+". Automatic candidate enabled here: "+input.automatic+".");
        rows.add("This is base-terrain planning. Native selection, End reservations, other structures, features and later ticking remain authoritative.");
        rows.add("No chunks were created and no world, draft or generation files were changed.");
        var lines=new ListTag();rows.stream().limit(64).map(s->s.substring(0,Math.min(s.length(),500))).forEach(s->lines.add(StringTag.valueOf(s)));data.put("rows",lines);return data;
    }
    private static void geometry(CompoundTag data,SiteProbe trace,Input input,List<String> rows){
        if(trace.pieces.isEmpty())return;var cells=new ArrayList<Integer>();var cleared=new ArrayList<Integer>();var markers=new ListTag();var ports=new ListTag();int sx=1,sy=1,sz=1,water=0,supports=0;
        var displayOrigin=new BlockPos(Math.min(trace.origin.getX(),trace.pieces.stream().mapToInt(p->p.origin().getX()).min().orElseThrow()),Math.min(trace.origin.getY(),trace.pieces.stream().mapToInt(p->p.origin().getY()).min().orElseThrow()),Math.min(trace.origin.getZ(),trace.pieces.stream().mapToInt(p->p.origin().getZ()).min().orElseThrow()));
        var columnCache=new HashMap<Long,net.minecraft.world.level.NoiseColumn>();
        for(var piece:trace.pieces){
            var offset=piece.origin().subtract(displayOrigin);var size=piece.entry().template().size();sx=Math.max(sx,offset.getX()+size.getX());sy=Math.max(sy,offset.getY()+size.getY());sz=Math.max(sz,offset.getZ()+size.getZ());
            rows.add(piece.name()+" · origin "+piece.origin().toShortString()+" · "+piece.entry().rules().collision().key());
            if(piece.name().equals("root"))data.putIntArray("root",new int[]{offset.getX(),offset.getY(),offset.getZ()});
            for(var cell:piece.entry().template().blocks()){
                SiteProbe.check();var pos=offset.offset(cell.pos());int packed=pos.getX()|pos.getY()<<7|pos.getZ()<<14;if(piece.operation().equals("clear"))cleared.add(packed);else if(!cell.state().isAir()){cells.add(packed);cells.add(cell.state().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).col);}if(piece.operation().equals("support"))supports++;
                var world=piece.origin().offset(cell.pos());long key=BlockPos.asLong(world.getX(),0,world.getZ());
                var column=columnCache.computeIfAbsent(key,k->input.generator.baseColumn(world.getX(),world.getZ(),input.height,input.seed));if(column.getBlock(world.getY()).is(Blocks.WATER))water++;
            }
            piece.entry().markers().forEach((name,marker)->{var pos=offset.offset(marker.pos());var t=new CompoundTag();t.putString("name",piece.name()+"/"+name);t.putString("facing",marker.facing().getSerializedName());t.putIntArray("pos",new int[]{pos.getX(),pos.getY(),pos.getZ()});markers.add(t);});
        }
        for(var connection:trace.connections){var p=trace.transform.position(trace.layoutSize,connection.position()).offset(trace.origin.subtract(displayOrigin));var t=new CompoundTag();t.putString("name",connection.module()+"/"+connection.name());t.putString("facing",trace.transform.facing(connection.facing()).getSerializedName());t.putIntArray("pos",new int[]{p.getX(),p.getY(),p.getZ()});ports.add(t);}
        int minY=Math.clamp(displayOrigin.getY()-8,input.height.getMinY(),input.height.getMaxY()-1),maxY=Math.clamp(displayOrigin.getY()+sy+8,minY+1,input.height.getMaxY()),width=sx,height=maxY-minY;
        var section=new int[width*height];int z=displayOrigin.getZ()+sz/2;
        for(int x=0;x<width;x++){
            SiteProbe.check();int worldX=displayOrigin.getX()+x;var column=input.generator.baseColumn(worldX,z,input.height,input.seed);
            for(int y=0;y<height;y++){var state=column.getBlock(minY+y);section[x+(height-1-y)*width]=state.isAir()?0xFF222831:state.is(Blocks.WATER)?0xFF346EB4:state.is(Blocks.LAVA)?0xFFE26B25:0xFF626B62;}
        }
        data.putIntArray("cells",cells.stream().mapToInt(Integer::intValue).toArray());data.putIntArray("size",new int[]{sx,sy,sz});data.put("markers",markers);data.put("ports",ports);
        data.putIntArray("origin",new int[]{displayOrigin.getX(),displayOrigin.getY(),displayOrigin.getZ()});data.putIntArray("clear_cells",cleared.stream().mapToInt(Integer::intValue).toArray());data.putIntArray("section",section);data.putIntArray("section_size",new int[]{width,height});data.putInt("section_min_y",minY);data.putInt("section_z",z);
        rows.add("Placement: "+((ModularStructure)input.structure.value()).placementDescription());
        var root=data.getIntArray("root").orElse(new int[]{0,0,0});var resolved=((ModularStructure)input.structure.value()).rootAnchored()?displayOrigin.offset(root[0],root[1],root[2]):trace.origin;rows.add("Resolved anchor "+resolved.toShortString()+"; displayed bounds origin "+displayOrigin.toShortString()+".");
        rows.add("Preparation: "+((ModularStructure)input.structure.value()).preparationDescription()+" · "+supports+" support cells · "+cleared.size()+" cleared cells.");data.putInt("support_cells",supports);
        rows.add(water+" occupied template cells intersect base water. Empty interiors are not drained.");
        rows.add(markers.size()+" spawn markers; "+ports.size()+" connection endpoints. Clear connectors do not certify stairs or a traversable route.");
    }
}

package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.api.IslandLayout;
import com.gwsheridan.worldarchitect.config.DimensionProfile;
import com.gwsheridan.worldarchitect.config.IslandCatalog;
import com.gwsheridan.worldarchitect.structure.IslandBuild;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

/** Opt-in rich islands. Legacy void generation and its serialized form stay unchanged. */
public final class SkyblockGenerator extends VoidGenerator {
    public static final MapCodec<SkyblockGenerator> CODEC=RecordCodecBuilder.mapCodec(b->b.group(
            Codec.intRange(1,1).optionalFieldOf("generator_version",1).forGetter(g->1),
            Biome.CODEC.fieldOf("biome").forGetter(SkyblockGenerator::biome),
            DimensionProfile.CODEC.fieldOf("dimension").forGetter(g->g.dimensionProfile().orElseThrow()),
            IslandCatalog.CODEC.fieldOf("catalog").forGetter(SkyblockGenerator::catalog)
    ).apply(b,(version,biome,dimension,catalog)->new SkyblockGenerator(biome,dimension,catalog)));
    private final IslandCatalog catalog;
    private final Map<String,Map<ChunkPos,List<IslandBuild.Cell>>> chunks;
    private volatile long worldSeed;

    public SkyblockGenerator(Holder<Biome> biome,DimensionProfile dimension,IslandCatalog catalog){
        super(biome,dimension,Optional.empty(),false);
        this.catalog=catalog.validate().getOrThrow();
        var parts=new HashMap<String,Map<ChunkPos,List<IslandBuild.Cell>>>();
        for(var entry:catalog.entries())parts.put(entry.id(),entry.build().cells().stream().collect(Collectors.groupingBy(c->ChunkPos.containing(c.pos()))));
        chunks=Map.copyOf(parts);
    }
    public IslandCatalog catalog(){return catalog;}
    @Override public Optional<IslandLayout> layout(){return Optional.of(layout(worldSeed));}
    /** The native End arena owns the central square; its shared island starts outside it. */
    public IslandLayout layout(long seed){return catalog.layout(seed,endProgression().isPresent()?512:0,0);}
    @Override public SkyblockGenerator withDimensionProfile(DimensionProfile profile){return new SkyblockGenerator(biome(),profile,catalog);}
    @Override protected MapCodec<SkyblockGenerator> codec(){return CODEC;}
    @Override public com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode terrainMode(){return com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode.SKYBLOCK;}
    @Override public Optional<String> profileProblem(){
        var parent=super.profileProblem();if(parent.isPresent())return parent;
        return catalog.entries().stream().filter(e->catalog.originY()<getMinY()||catalog.originY()+e.build().size().getY()>getMinY()+getGenDepth()).findFirst().map(e->"The entire island "+e.id()+" must fit inside the dimension's vertical range.");
    }
    public void validateAssets(HolderLookup.Provider registries) throws java.io.IOException {
        for(var entry:catalog.entries()){
            var icon=net.minecraft.resources.Identifier.tryParse(entry.icon());
            if(icon==null||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(icon))throw new java.io.IOException("Unknown island icon "+entry.icon());
            var checked=entry.build().checked(registries);
            if(!IslandBuild.CODEC.encodeStart(JsonOps.INSTANCE,checked).getOrThrow().equals(IslandBuild.CODEC.encodeStart(JsonOps.INSTANCE,entry.build()).getOrThrow()))throw new java.io.IOException("Island "+entry.id()+" contains metadata that must be sanitized in Island Studio before use.");
        }
    }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets,RandomState state,long seed){worldSeed=seed;return super.createState(sets,state,seed);}
    private boolean arena(int x,int z){return endProgression().isPresent()&&com.gwsheridan.worldarchitect.config.EndProgression.contains(x,z);}
    @Override public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk,Blender blender,RandomState state,StructureManager structures,BiomeManager biomes,@Nullable WorldGenRegion region,Set<Holder<Biome>> possibleBiomes){
        int x=chunk.getPos().getMinBlockX(),z=chunk.getPos().getMinBlockZ();
        if(arena(x,z))return super.buildTerrain(chunk,blender,state,structures,biomes,region,possibleBiomes);
        var layout=layout(state.seed());int slot=layout.slotAt(x,z);if(slot<0)return CompletableFuture.completedFuture(chunk);
        var entry=catalog.entry(slot,state.seed());var origin=layout.origin(slot);
        var selected=chunks.get(entry.id()).getOrDefault(new ChunkPos((x-origin.getX())>>4,(z-origin.getZ())>>4),List.of());
        var floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);var surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for(var cell:selected){
            var pos=origin.offset(cell.pos());
            chunk.setBlockState(pos,cell.state(),Block.UPDATE_ALL);floor.update(pos.getX()&15,pos.getY(),pos.getZ()&15,cell.state());surface.update(pos.getX()&15,pos.getY(),pos.getZ()&15,cell.state());
            cell.data().ifPresent(data->{data.putInt("x",pos.getX());data.putInt("y",pos.getY());data.putInt("z",pos.getZ());chunk.setBlockEntityNbt(data);});
            if(!cell.state().getFluidState().isEmpty()||cell.state().getBlock() instanceof FallingBlock)chunk.markPosForPostProcessing(pos);
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public NoiseColumn baseColumn(int x,int z,LevelHeightAccessor height,long seed){
        if(arena(x,z))return super.baseColumn(x,z,height,seed);
        var column=new BlockState[height.getHeight()];Arrays.fill(column,Blocks.AIR.defaultBlockState());
        var layout=layout(seed);int slot=layout.slotAt(x,z);
        if(slot>=0){var origin=layout.origin(slot);var entry=catalog.entry(slot,seed);for(var cell:chunks.get(entry.id()).getOrDefault(new ChunkPos((x-origin.getX())>>4,(z-origin.getZ())>>4),List.of())){
            int y=origin.getY()+cell.pos().getY()-height.getMinY();if(cell.pos().getX()==x-origin.getX()&&cell.pos().getZ()==z-origin.getZ()&&y>=0&&y<column.length)column[y]=cell.state();
        }}
        return new NoiseColumn(height.getMinY(),column);
    }
    public int previewHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor height,long seed){
        if(arena(x,z))return endProgression().orElseThrow().generator().getBaseHeight(x,z,type,height,endProgression().orElseThrow().state(seed));
        var column=baseColumn(x,z,height,seed);for(int y=height.getMaxY();y>=height.getMinY();y--)if(type.isOpaque().test(column.getBlock(y)))return y+1;return height.getMinY();
    }
    @Override int baseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor height){return previewHeight(x,z,type,height,worldSeed);}
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor height,RandomState state){return previewHeight(x,z,type,height,state.seed());}
}

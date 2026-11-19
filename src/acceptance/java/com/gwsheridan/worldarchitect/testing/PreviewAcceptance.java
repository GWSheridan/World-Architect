package com.gwsheridan.worldarchitect.testing;

import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.worldgen.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;

final class PreviewAcceptance {
    private PreviewAcceptance() {}
    static void check(MinecraftServer server) {
        int loaded=server.overworld().getChunkSource().getLoadedChunksCount();
        var stems=server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        var base=stems.getOrThrow(LevelStem.OVERWORLD).value();
        var biome=server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var profile=new DimensionProfile(base.type().value().minY(),base.type().value().height(),Optional.empty());
        var cases=new ArrayList<LevelStem>();
        var shapes=new ArrayList<TerrainShape>();shapes.add(TerrainShape.HILLS);
        for(var landform:AdvancedTerrain.Landform.values())shapes.add(new TerrainShape(108,72,Optional.of(new AdvancedTerrain(landform,192,37))));
        for(var shape:shapes) {
            cases.add(new LevelStem(base.type(),new SingleBlockGenerator(biome,new SingleBlockProfile(Blocks.STONE.defaultBlockState(),shape),Optional.of(profile))));
            cases.add(new LevelStem(base.type(),new LayeredGenerator(biome,new LayeredProfile(Blocks.STONE.defaultBlockState(),List.of(new LayeredProfile.Layer(Blocks.DIRT.defaultBlockState(),1),new LayeredProfile.Layer(Blocks.ANDESITE.defaultBlockState(),3)),shape,List.of()),Optional.of(profile))));
        }
        var flooded=profile.withContent(ContentProfile.EMPTY.withCaves(Optional.of(new ContentProfile.Caves(-32,48,8))).withSea(Optional.of(new ContentProfile.Sea(ContentProfile.Fluid.WATER,90,true))));
        cases.add(new LevelStem(base.type(),new SingleBlockGenerator(biome,new SingleBlockProfile(Blocks.STONE.defaultBlockState(),TerrainShape.HILLS),Optional.of(flooded))));
        cases.add(new LevelStem(base.type(),new VoidGenerator(biome,profile,Optional.empty(),true)));
        cases.add(new LevelStem(base.type(),new VoidGenerator(biome,profile,Optional.of(new IslandProfile(IslandTemplate.classic(),64,128,64)),false)));
        var end=stems.getOrThrow(LevelStem.END).value();
        var arenaProfile=new DimensionProfile(0,256,Optional.empty()).withEndProgression(Optional.of(EndProgression.vanillaArena(server.registryAccess())));
        cases.add(new LevelStem(end.type(),new SingleBlockGenerator(biome,new SingleBlockProfile(Blocks.END_STONE.defaultBlockState(),TerrainShape.HILLS),Optional.of(arenaProfile))));
        cases.add(new LevelStem(end.type(),new VoidGenerator(biome,arenaProfile,Optional.empty(),false)));
        long columns=0,blocks=0;
        for(var stem:cases)for(long seed:new long[]{0,194723,Long.MIN_VALUE}) {
            var preview=GenerationPreview.forStem(stem,seed).orElseThrow();
            var height=net.minecraft.world.level.LevelHeightAccessor.create(stem.type().value().minY(),stem.type().value().height());
            for(int x:new int[]{-1024,-257,-256,-1,0,4,255,256,2048})for(int z:new int[]{-256,0,4,256}) {
                var sample=preview.sample(x,z);var column=preview.column(x,z);
                var expected=stem.generator() instanceof SolidColumnGenerator solid?solid.baseColumn(x,z,height,seed):((VoidGenerator)stem.generator()).baseColumn(x,z,height,seed);
                int top=height.getMinY()-1;
                for(int y=height.getMinY();y<=height.getMaxY();y++) {
                    require(preview.biomeAt(x,y,z).isPresent(),"Missing sampled biome registry ID");
                    require(column.getBlock(y)==expected.getBlock(y),"Cross-section differs from generator column");
                    if(!column.getBlock(y).isAir())top=y;blocks++;
                }
                require(sample.surfaceY()==top&&sample.occupied()==(top>=height.getMinY()),"Height preview differs from column");columns++;
            }
        }
        require(loaded==server.overworld().getChunkSource().getLoadedChunksCount(),"Preview sampling loaded chunks");
        System.out.println("WA_PREVIEW_BASE_COLUMNS_PASS cases="+cases.size()+" columns="+columns+" blocks="+blocks);
    }
    private static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
}

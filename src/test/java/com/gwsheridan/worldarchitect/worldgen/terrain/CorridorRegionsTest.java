package com.gwsheridan.worldarchitect.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.*;
import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.config.RegionalTerrain.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;

class CorridorRegionsTest {
    private static final AdvancedTerrain FLAT=new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0);
    private Zone path(List<Vertex> points,int radius){return new Zone("path",Mask.RECTANGLE,-64,-64,64,64,1,4,80,0,FLAT,Optional.empty()).withCorridor(new Corridor(points,radius));}
    @Test void straightCorridorMatchesAnIndependentCapsuleAndIncludesItsBoundary(){
        var zone=path(List.of(new Vertex(-32,0),new Vertex(32,0)),4);
        for(int x=-40;x<=40;x++)for(int z=-8;z<=8;z++){int dx=x<-32?-32-x:x>32?x-32:0;assertEquals(dx*dx+z*z<=16,zone.edgeDistance(x,z)>=0,"at "+x+","+z);}
        assertEquals(-36,zone.minX());assertEquals(36,zone.maxX());assertEquals(-4,zone.minZ());assertEquals(4,zone.maxZ());
    }
    @Test void bendsCrossingsAndClosedPathsAreUnionsOfRoundSegments(){
        var corner=path(List.of(new Vertex(-32,0),new Vertex(0,0),new Vertex(0,32)),4);
        assertTrue(corner.edgeDistance(-3,-3)>=0);assertTrue(corner.edgeDistance(4,4)>=0);assertTrue(corner.edgeDistance(3,-3)<0);
        var loop=path(List.of(new Vertex(0,0),new Vertex(32,0),new Vertex(32,32),new Vertex(0,32),new Vertex(0,0)),4);
        assertTrue(loop.validate().isSuccess());assertTrue(loop.edgeDistance(0,0)>=0);assertTrue(loop.edgeDistance(16,16)<0);
    }
    @Test void corridorHolesAndFeatheringShareTheCompositeGeometry(){
        var line=path(List.of(new Vertex(-32,0),new Vertex(32,0)),4);var land=new Zone("land",Mask.RECTANGLE,-64,-64,64,64,0,0,64,0,FLAT,Optional.empty());
        var subtraction=new Zone("outside",Mask.COMPOSITE,-64,-64,64,64,2,0,90,0,FLAT,Optional.empty(),List.of(),Optional.of(new Composition(Operation.SUBTRACT,List.of("land","path"))));
        var regions=new RegionalTerrain(List.of(land,line,subtraction)).normalized();assertTrue(regions.validate().isSuccess());assertTrue(regions.distances(0,4).get("outside")<0);assertTrue(regions.distances(0,5).get("outside")>=0);
        var shape=new TerrainShape(64,0,Optional.of(FLAT),Optional.of(new RegionalTerrain(List.of(line))));var height=LevelHeightAccessor.create(-64,384);
        assertEquals(80,HeightfieldV6.sample(shape,17,0,0,height).top());assertEquals(72,HeightfieldV6.sample(shape,17,0,2,height).top());assertEquals(64,HeightfieldV6.sample(shape,17,0,4,height).top());
    }
    @Test void invalidPathsBoundsAndSharedPointBudgetsAreRefused(){
        assertTrue(new Corridor(List.of(new Vertex(0,0),new Vertex(0,0)),4).validate().error().isPresent());
        assertTrue(new Corridor(List.of(new Vertex(0,0),new Vertex(RegionalTerrain.WORLD_BOUND,0)),4).validate().error().isPresent());
        assertTrue(new Corridor(List.of(new Vertex(0,0),new Vertex(16,0)),0).validate().error().isPresent());
        var points=java.util.stream.IntStream.range(0,64).mapToObj(x->new Vertex(x*16,0)).toList();var zones=new ArrayList<Zone>();
        for(int i=0;i<9;i++){var z=path(points,4);zones.add(new Zone("path_"+i,z.mask(),z.minX(),z.minZ(),z.maxX(),z.maxZ(),z.priority(),z.transition(),z.elevation(),z.relief(),z.terrain(),z.biome(),z.vertices(),z.composition(),z.corridor()));}
        assertTrue(new RegionalTerrain(zones).validate().error().isPresent());
    }
    @Test void versionSixIsExplicitAndLegacyEncodingDoesNotGainNewFields(){
        var z=path(List.of(new Vertex(-32,0),new Vertex(32,0)),4);var shape=new TerrainShape(64,0,Optional.of(FLAT),Optional.of(new RegionalTerrain(List.of(z))));
        var json=TerrainShape.CODEC.codec().encodeStart(JsonOps.INSTANCE,shape).getOrThrow().getAsJsonObject();assertEquals(6,json.get("generator_version").getAsInt());assertEquals(shape,TerrainShape.CODEC.codec().parse(JsonOps.INSTANCE,json).getOrThrow());json.addProperty("generator_version",5);assertTrue(TerrainShape.CODEC.codec().parse(JsonOps.INSTANCE,json).error().isPresent());
        var old=new Zone("old",Mask.RECTANGLE,-64,-64,64,64,0,0,64,0,FLAT,Optional.empty());assertFalse(Zone.CODEC.encodeStart(JsonOps.INSTANCE,old).getOrThrow().getAsJsonObject().has("corridor"));
    }
    @Test void translatingAndConcurrentSamplingAreDeterministic(){
        var z=path(List.of(new Vertex(-32,0),new Vertex(32,16)),4);var moved=z.translated(100,-100);assertEquals(z.edgeDistance(0,4),moved.edgeDistance(100,-96));assertEquals(z.corridor().orElseThrow().points().getFirst(),new Vertex(-32,0));
        var shape=new TerrainShape(64,0,Optional.of(FLAT),Optional.of(new RegionalTerrain(List.of(z))));var height=LevelHeightAccessor.create(-64,384);
        var serial=java.util.stream.IntStream.range(-128,128).map(x->HeightfieldV6.sample(shape,42,x,4,height).top()).toArray();var parallel=java.util.stream.IntStream.range(-128,128).parallel().map(x->HeightfieldV6.sample(shape,42,x,4,height).top()).toArray();assertArrayEquals(serial,parallel);
    }
}

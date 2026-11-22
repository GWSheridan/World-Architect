package com.gwsheridan.worldsunwritten.worldgen.terrain;

import com.gwsheridan.worldsunwritten.config.*;
import com.gwsheridan.worldsunwritten.config.RegionalTerrain.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolygonRegionsTest {
    private static final AdvancedTerrain FLAT=new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0);
    private Zone polygon(List<Vertex> points){return new Zone("outline",Mask.POLYGON,0,0,100,100,0,0,120,0,FLAT,Optional.empty()).withVertices(points);}
    private List<Vertex> outline(){return List.of(new Vertex(-100,-100),new Vertex(100,-100),new Vertex(100,-20),new Vertex(-20,-20),new Vertex(-20,100),new Vertex(-100,100));}
    @Test void concaveMaskIncludesEdgesAndVerticesInEitherWinding(){
        var p=polygon(outline());assertTrue(p.validate().isSuccess());
        for(var v:outline())assertEquals(0,p.edgeDistance(v.x(),v.z()));
        assertTrue(p.edgeDistance(-60,60)>0);assertTrue(p.edgeDistance(60,-60)>0);assertTrue(p.edgeDistance(0,0)<0);
        var reverse=new ArrayList<>(outline());Collections.reverse(reverse);var other=polygon(reverse);
        for(int x=-112;x<=112;x+=7)for(int z=-112;z<=112;z+=7)assertEquals(p.edgeDistance(x,z),other.edgeDistance(x,z));
    }
    @Test void crossingsDuplicatesDegeneracyAndBacktrackingAreRefused(){
        for(var points:List.of(List.of(new Vertex(0,0),new Vertex(10,10),new Vertex(0,10),new Vertex(10,0)),
                List.of(new Vertex(0,0),new Vertex(10,0),new Vertex(5,0)),
                List.of(new Vertex(0,0),new Vertex(10,0),new Vertex(0,10),new Vertex(0,0)),
                List.of(new Vertex(0,0),new Vertex(10,0),new Vertex(5,0),new Vertex(10,10),new Vertex(0,10))))assertTrue(polygon(points).validate().error().isPresent());
        assertTrue(polygon(List.of(new Vertex(-30_000_000,0),new Vertex(0,0),new Vertex(0,100))).validate().error().isPresent());
    }
    @Test void vertexLimitsAreSharedAcrossTheProfile(){
        var points=new ArrayList<Vertex>();for(int i=0;i<64;i++){double a=i*Math.PI*2/64;points.add(new Vertex((int)Math.round(10000*Math.cos(a)),(int)Math.round(10000*Math.sin(a))));}
        var p=polygon(points);assertTrue(p.validate().isSuccess());var zones=new ArrayList<Zone>();
        for(int i=0;i<9;i++)zones.add(new Zone("p"+i,p.mask(),p.minX(),p.minZ(),p.maxX(),p.maxZ(),0,0,120,0,FLAT,Optional.empty(),p.vertices()));
        assertTrue(new RegionalTerrain(zones.subList(0,8)).validate().isSuccess());assertTrue(new RegionalTerrain(zones).validate().error().isPresent());
        points.add(new Vertex(0,0));assertTrue(polygon(points).validate().error().isPresent());
    }
    @Test void onlyPolygonProfilesOptIntoVersionFour(){
        var p=polygon(outline());var shape=new TerrainShape(64,0,Optional.of(FLAT),Optional.of(new RegionalTerrain(List.of(p))));
        var encoded=TerrainShape.CODEC.codec().encodeStart(JsonOps.INSTANCE,shape).getOrThrow().getAsJsonObject();
        assertEquals(4,encoded.get("generator_version").getAsInt());assertEquals(shape,TerrainShape.CODEC.codec().parse(JsonOps.INSTANCE,encoded).getOrThrow());
        encoded.addProperty("generator_version",3);assertTrue(TerrainShape.CODEC.codec().parse(JsonOps.INSTANCE,encoded).error().isPresent());
        var old=new Zone("old",Mask.RECTANGLE,-100,-100,100,100,0,0,120,0,FLAT,Optional.empty());
        assertFalse(Zone.CODEC.encodeStart(JsonOps.INSTANCE,old).getOrThrow().getAsJsonObject().has("vertices"));assertEquals(3,new RegionalTerrain(List.of(old)).version());
    }
    @Test void featheringAndSamplingRemainCoordinateDeterministic(){
        var p=polygon(outline());p=new Zone(p.name(),p.mask(),p.minX(),p.minZ(),p.maxX(),p.maxZ(),0,20,120,0,FLAT,Optional.empty(),p.vertices());
        assertEquals(0,p.weight(-100,0));assertEquals(.5,p.weight(-90,0));assertEquals(1,p.weight(-80,0));
        var shape=new TerrainShape(64,0,Optional.of(FLAT),Optional.of(new RegionalTerrain(List.of(p))));var height=LevelHeightAccessor.create(-64,384);
        assertEquals(64,HeightfieldV4.sample(shape,18,0,0,height).top());assertEquals(120,HeightfieldV4.sample(shape,18,-60,0,height).top());
        var serial=java.util.stream.IntStream.range(-128,128).map(x->HeightfieldV4.sample(shape,18,x,-60,height).top()).toArray();
        var parallel=java.util.stream.IntStream.range(-128,128).parallel().map(x->HeightfieldV4.sample(shape,18,x,-60,height).top()).toArray();assertArrayEquals(serial,parallel);
    }
    @Test void translationMovesVerticesAndDerivedBoundsTogether(){
        var p=polygon(outline()).translated(-1000,500);assertTrue(p.validate().isSuccess());assertEquals(-1100,p.minX());assertEquals(400,p.minZ());assertEquals(0,p.edgeDistance(-1100,500));
    }
    @Test void coordinatesRefuseFractionsAndNumbersThatWouldWrapIntoBounds(){
        for(String x:List.of("0.5","1.0000000000000000001","4294967296","-4294967296","30000000"))assertTrue(Vertex.CODEC.parse(JsonOps.INSTANCE,com.google.gson.JsonParser.parseString("{\"x\":"+x+",\"z\":0}")).error().isPresent());
    }
}

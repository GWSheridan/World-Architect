package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;

/** Versioned coordinate masks; evaluation order is cached and independent of chunk visitation. */
public final class RegionalTerrain {
    public static final int VERSION = 3, MAX_REGIONS = 32, WORLD_BOUND = 29_999_984;
    public static final int POLYGON_VERSION = 4, MAX_VERTICES = 64, MAX_PROFILE_VERTICES = 512;
    public static final Codec<RegionalTerrain> CODEC = Zone.CODEC.listOf(1, MAX_REGIONS).xmap(RegionalTerrain::new, RegionalTerrain::zones).validate(RegionalTerrain::validate);
    private final List<Zone> zones, ordered;
    public RegionalTerrain(List<Zone> zones) {
        this.zones = List.copyOf(zones);
        this.ordered = zones.stream().sorted(Comparator.comparingInt(Zone::priority).reversed()).toList();
    }
    public List<Zone> zones() { return zones; }
    public int version() { return zones.stream().anyMatch(z -> z.mask() == Mask.POLYGON) ? POLYGON_VERSION : VERSION; }
    /** Higher priority wins; ties retain the earlier editor entry. */
    public List<Zone> ordered() { return ordered; }
    public DataResult<RegionalTerrain> validate() {
        if (zones.isEmpty() || zones.size() > MAX_REGIONS || zones.stream().map(Zone::name).distinct().count() != zones.size()) return DataResult.error(() -> "Use 1–32 regions with unique names.");
        if (zones.stream().mapToLong(z -> z.vertices().size()).sum() > MAX_PROFILE_VERTICES) return DataResult.error(() -> "A dimension supports at most 512 polygon vertices.");
        for (var zone : zones) { var error = zone.validate().error(); if (error.isPresent()) return DataResult.error(() -> error.orElseThrow().message()); }
        return DataResult.success(this);
    }
    public Optional<Holder<Biome>> biomeAt(int x, int z) {
        return ordered.stream().filter(zone -> zone.biome().isPresent() && zone.weight(x,z) >= 0.5).map(zone -> zone.biome().orElseThrow()).findFirst();
    }
    @Override public boolean equals(Object o) { return o instanceof RegionalTerrain other && zones.equals(other.zones); }
    @Override public int hashCode() { return zones.hashCode(); }
    public enum Mask implements StringRepresentable {
        RECTANGLE, CIRCLE, POLYGON;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public record Vertex(int x, int z) {
        public static final Codec<Vertex> CODEC = RecordCodecBuilder.create(b -> b.group(
                ExactInt.range(-WORLD_BOUND, WORLD_BOUND).fieldOf("x").forGetter(Vertex::x),
                ExactInt.range(-WORLD_BOUND, WORLD_BOUND).fieldOf("z").forGetter(Vertex::z)).apply(b, Vertex::new));
    }
    public record Zone(String name, Mask mask, int minX, int minZ, int maxX, int maxZ, int priority, int transition,
                       int elevation, int relief, AdvancedTerrain terrain, Optional<Holder<Biome>> biome, List<Vertex> vertices) {
        /** Retained API 1 constructor; legacy masks keep their exact encoding. */
        public Zone(String name, Mask mask, int minX, int minZ, int maxX, int maxZ, int priority, int transition,
                    int elevation, int relief, AdvancedTerrain terrain, Optional<Holder<Biome>> biome) {
            this(name,mask,minX,minZ,maxX,maxZ,priority,transition,elevation,relief,terrain,biome,List.of());
        }
        public Zone { vertices = List.copyOf(vertices); }
        private static final Codec<Integer> COORD = Codec.intRange(-WORLD_BOUND, WORLD_BOUND);
        public static final Codec<Zone> CODEC = RecordCodecBuilder.<Zone>create(b -> b.group(
                Codec.STRING.fieldOf("name").forGetter(Zone::name), StringRepresentable.fromEnum(Mask::values).fieldOf("mask").forGetter(Zone::mask),
                COORD.fieldOf("min_x").forGetter(Zone::minX), COORD.fieldOf("min_z").forGetter(Zone::minZ),
                COORD.fieldOf("max_x").forGetter(Zone::maxX), COORD.fieldOf("max_z").forGetter(Zone::maxZ),
                Codec.intRange(-1024,1024).optionalFieldOf("priority",0).forGetter(Zone::priority), Codec.intRange(0,4096).optionalFieldOf("transition",32).forGetter(Zone::transition),
                Codec.intRange(TerrainShape.MIN_ELEVATION,TerrainShape.MAX_ELEVATION).fieldOf("elevation").forGetter(Zone::elevation),
                Codec.intRange(0,TerrainShape.MAX_RELIEF).fieldOf("relief").forGetter(Zone::relief),
                AdvancedTerrain.CODEC.fieldOf("terrain").forGetter(Zone::terrain), Codec.lazyInitialized(() -> Biome.CODEC).optionalFieldOf("biome").forGetter(Zone::biome),
                Vertex.CODEC.listOf(0,MAX_VERTICES).optionalFieldOf("vertices",List.of()).forGetter(Zone::vertices)
        ).apply(b,Zone::new)).validate(Zone::validate);
        public DataResult<Zone> validate() {
            long width=(long)maxX-minX, depth=(long)maxZ-minZ;
            if (!name.matches("[a-z0-9_-]{1,32}") || minX < -WORLD_BOUND || minZ < -WORLD_BOUND || maxX > WORLD_BOUND || maxZ > WORLD_BOUND
                    || width < 2 || depth < 2 || mask==Mask.CIRCLE && width!=depth || transition<0 || transition>4096 || transition*2L>Math.min(width,depth)
                    || priority < -1024 || priority > 1024 || elevation < TerrainShape.MIN_ELEVATION || elevation > TerrainShape.MAX_ELEVATION || relief<0 || relief>TerrainShape.MAX_RELIEF
                    || terrain.landform()==AdvancedTerrain.Landform.FLOATING_ISLANDS || terrain.scale()<AdvancedTerrain.MIN_SCALE || terrain.scale()>AdvancedTerrain.MAX_SCALE
                    || biome.filter(holder -> holder.unwrapKey().isEmpty()).isPresent())
                return DataResult.error(() -> "Region bounds, name, transition or terrain are invalid. Circles need square bounds; regional terrain needs solid landforms.");
            if (mask != Mask.POLYGON && !vertices.isEmpty()) return DataResult.error(() -> "Only polygon masks accept vertices.");
            if (mask == Mask.POLYGON && !validPolygon()) return DataResult.error(() -> "Use 3–64 unique vertices in a simple polygon with nonzero area and matching derived bounds. Holes and crossings are not supported.");
            return DataResult.success(this);
        }
        public Zone withVertices(List<Vertex> points) {
            return new Zone(name,Mask.POLYGON,points.stream().mapToInt(Vertex::x).min().orElse(0),points.stream().mapToInt(Vertex::z).min().orElse(0),
                    points.stream().mapToInt(Vertex::x).max().orElse(0),points.stream().mapToInt(Vertex::z).max().orElse(0),priority,transition,elevation,relief,terrain,biome,points);
        }
        public Zone translated(int dx,int dz) {
            return new Zone(name,mask,Math.addExact(minX,dx),Math.addExact(minZ,dz),Math.addExact(maxX,dx),Math.addExact(maxZ,dz),priority,transition,elevation,relief,terrain,biome,
                    vertices.stream().map(p -> new Vertex(Math.addExact(p.x,dx),Math.addExact(p.z,dz))).toList());
        }
        private boolean validPolygon() {
            int n=vertices.size();
            if(n<3||n>MAX_VERTICES||new HashSet<>(vertices).size()!=n||vertices.stream().anyMatch(v->Math.abs((long)v.x)>WORLD_BOUND||Math.abs((long)v.z)>WORLD_BOUND)
                    ||vertices.stream().mapToInt(Vertex::x).min().orElse(0)!=minX||vertices.stream().mapToInt(Vertex::x).max().orElse(0)!=maxX
                    ||vertices.stream().mapToInt(Vertex::z).min().orElse(0)!=minZ||vertices.stream().mapToInt(Vertex::z).max().orElse(0)!=maxZ)return false;
            long area=0;
            for(int i=0;i<n;i++){
                var a=vertices.get(i);var b=vertices.get((i+1)%n);area+=(long)a.x*b.z-(long)b.x*a.z;
                for(int j=i+1;j<n;j++)if(j!=i+1&&!(i==0&&j==n-1)&&intersects(a,b,vertices.get(j),vertices.get((j+1)%n)))return false;
                var c=vertices.get((i+2)%n);
                if(cross(a,b,c)==0&&((long)(a.x-b.x)*(c.x-b.x)+(long)(a.z-b.z)*(c.z-b.z))>0)return false;
            }
            return area!=0;
        }
        private static long cross(Vertex a,Vertex b,Vertex p){return ((long)b.x-a.x)*((long)p.z-a.z)-((long)b.z-a.z)*((long)p.x-a.x);}
        private static boolean on(Vertex a,Vertex b,Vertex p){return cross(a,b,p)==0&&p.x>=Math.min(a.x,b.x)&&p.x<=Math.max(a.x,b.x)&&p.z>=Math.min(a.z,b.z)&&p.z<=Math.max(a.z,b.z);}
        private static boolean intersects(Vertex a,Vertex b,Vertex c,Vertex d){
            long abC=cross(a,b,c),abD=cross(a,b,d),cdA=cross(c,d,a),cdB=cross(c,d,b);
            return on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b)||(Long.signum(abC)!=Long.signum(abD)&&Long.signum(cdA)!=Long.signum(cdB));
        }
        public double edgeDistance(int x,int z) {
            if(mask==Mask.RECTANGLE)return Math.min(Math.min((double)x-minX,(double)maxX-x),Math.min((double)z-minZ,(double)maxZ-z));
            if(mask==Mask.POLYGON){
                if(x<minX||x>maxX||z<minZ||z>maxZ)return -1;
                boolean inside=false;double distance=Double.POSITIVE_INFINITY;var point=new Vertex(x,z);
                for(int i=0;i<vertices.size();i++){
                    var a=vertices.get(i);var b=vertices.get((i+1)%vertices.size());
                    if(on(a,b,point))return 0;
                    if((a.z>z)!=(b.z>z)&&(cross(a,b,point)>0)==(b.z>a.z))inside=!inside;
                    double dx=(double)b.x-a.x,dz=(double)b.z-a.z,t=Math.clamp(((x-(double)a.x)*dx+(z-(double)a.z)*dz)/(dx*dx+dz*dz),0,1);
                    double px=x-(a.x+t*dx),pz=z-(a.z+t*dz);distance=Math.min(distance,px*px+pz*pz);
                }
                return (inside?1:-1)*StrictMath.sqrt(distance);
            }
            double dx=x-((long)minX+maxX)/2.0,dz=z-((long)minZ+maxZ)/2.0;
            return ((long)maxX-minX)/2.0-StrictMath.sqrt(dx*dx+dz*dz);
        }
        public double weight(int x,int z) {
            double edge=edgeDistance(x,z); if(edge<0)return 0; if(transition==0)return 1;
            double progress=Math.clamp(edge/transition,0.0,1.0);return progress*progress*(3-2*progress);
        }
    }
}

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
    public static final int COMPOSITE_VERSION = 5, MAX_COMPOSITE_DEPTH = 8;
    public static final int CORRIDOR_VERSION = 6;
    public static final Codec<RegionalTerrain> CODEC = Zone.CODEC.listOf(1, MAX_REGIONS).xmap(RegionalTerrain::new, RegionalTerrain::zones).validate(RegionalTerrain::validate);
    private final List<Zone> zones, ordered;
    private final Map<String,Zone> named;
    private final int version;
    private final String graphError;
    public RegionalTerrain(List<Zone> zones) {
        this.zones = List.copyOf(zones);
        this.ordered = zones.stream().sorted(Comparator.comparingInt(Zone::priority).reversed()).toList();
        var names=new LinkedHashMap<String,Zone>();zones.forEach(z->names.put(z.name(),z));named=Map.copyOf(names);
        version=zones.stream().anyMatch(z->z.mask()==Mask.CORRIDOR)?CORRIDOR_VERSION:zones.stream().anyMatch(z->z.mask()==Mask.COMPOSITE)?COMPOSITE_VERSION:zones.stream().anyMatch(z->z.mask()==Mask.POLYGON)?POLYGON_VERSION:VERSION;
        String failure=null;try{if(zones.size()>MAX_REGIONS)throw new IllegalArgumentException("At most 32 terrain regions are supported.");var seen=new HashMap<String,Integer>();for(var z:zones)graph(z.name(),new HashSet<>(),0,seen);}catch(IllegalArgumentException e){failure=e.getMessage();}graphError=failure;
    }
    public List<Zone> zones() { return zones; }
    public int version() { return version; }
    private void graph(String name,Set<String> path,int depth,Map<String,Integer> seen){var z=named.get(name);if(z==null||depth>=MAX_COMPOSITE_DEPTH||!path.add(name))throw new IllegalArgumentException("Composite regions need existing, acyclic references within depth 8.");if(seen.getOrDefault(name,-1)>=depth){path.remove(name);return;}seen.put(name,depth);if(z.mask()==Mask.COMPOSITE){var c=z.composition().orElseThrow(()->new IllegalArgumentException("A composite needs its operation and operands."));if(c.regions().size()<2||c.regions().size()>8)throw new IllegalArgumentException("Use 2–8 distinct existing region names.");for(var ref:c.regions())graph(ref,path,depth+1,seen);}path.remove(name);}
    /** Higher priority wins; ties retain the earlier editor entry. */
    public List<Zone> ordered() { return ordered; }
    public DataResult<RegionalTerrain> validate() {
        if (zones.isEmpty() || zones.size() > MAX_REGIONS || zones.stream().map(Zone::name).distinct().count() != zones.size()) return DataResult.error(() -> "Use 1–32 regions with unique names.");
        if (zones.stream().mapToLong(z -> z.vertices().size()+z.corridor().map(c->c.points().size()).orElse(0)).sum() > MAX_PROFILE_VERTICES) return DataResult.error(() -> "A dimension supports at most 512 polygon and corridor points.");
        for (var zone : zones) { var error = zone.validate().error(); if (error.isPresent()) return DataResult.error(() -> error.orElseThrow().message()); }
        if(graphError!=null)return DataResult.error(()->graphError);
        try { var memo=new HashMap<String,int[]>();for(var zone:zones) bounds(zone.name(),new HashSet<>(),0,memo); }
        catch(IllegalArgumentException e){return DataResult.error(e::getMessage);}
        return DataResult.success(this);
    }
    public Optional<Holder<Biome>> biomeAt(int x, int z) {
        if(version()>=COMPOSITE_VERSION){var sample=distances(x,z);return ordered.stream().filter(zone->zone.biome().isPresent()&&weight(zone,sample.get(zone.name()))>=.5).map(zone->zone.biome().orElseThrow()).findFirst();}
        return ordered.stream().filter(zone -> zone.biome().isPresent() && zone.weight(x,z) >= 0.5).map(zone -> zone.biome().orElseThrow()).findFirst();
    }
    /** One bounded, memoized evaluation shared by terrain, rule masks and previews. */
    public Map<String,Double> distances(int x,int z){if(graphError!=null)throw new IllegalArgumentException(graphError);var result=new HashMap<String,Double>();for(var zone:zones)distance(zone.name(),x,z,result,new HashSet<>(),0);return Map.copyOf(result);}
    public double edgeDistance(Zone zone,int x,int z){return version()>=COMPOSITE_VERSION?distance(zone.name(),x,z,new HashMap<>(),new HashSet<>(),0):zone.edgeDistance(x,z);}
    public static double weight(Zone zone,double edge){if(edge<0)return 0;if(zone.transition()==0)return 1;double t=Math.clamp(edge/zone.transition(),0,1);return t*t*(3-2*t);}
    private double distance(String name,int x,int z,Map<String,Double> memo,Set<String> path,int depth){
        if(memo.containsKey(name))return memo.get(name);var zone=named.get(name);
        if(zone==null||depth>=MAX_COMPOSITE_DEPTH||!path.add(name))throw new IllegalArgumentException("Composite regions need existing, acyclic references within depth 8.");
        double value;
        if(zone.mask()!=Mask.COMPOSITE)value=zone.geometricDistance(x,z);
        else {var c=zone.composition().orElseThrow();value=distance(c.regions().getFirst(),x,z,memo,path,depth+1);for(int i=1;i<c.regions().size();i++){double other=distance(c.regions().get(i),x,z,memo,path,depth+1);value=switch(c.operation()){case UNION->Math.max(value,other);case INTERSECTION->Math.min(value,other);case SUBTRACT->Math.min(value,other==0?-Math.ulp(1.0):-other);};}}
        path.remove(name);memo.put(name,value);return value;
    }
    private int[] bounds(String name,Set<String> path,int depth,Map<String,int[]> memo){
        if(memo.containsKey(name))return memo.get(name).clone();
        var zone=named.get(name);if(zone==null||depth>=MAX_COMPOSITE_DEPTH||!path.add(name))throw new IllegalArgumentException("Composite regions need existing, acyclic references within depth 8.");
        int[] box={zone.minX(),zone.minZ(),zone.maxX(),zone.maxZ()};
        if(zone.mask()==Mask.COMPOSITE){var c=zone.composition().orElseThrow();box=bounds(c.regions().getFirst(),path,depth+1,memo);for(int i=1;i<c.regions().size();i++){var b=bounds(c.regions().get(i),path,depth+1,memo);if(c.operation()==Operation.UNION){box[0]=Math.min(box[0],b[0]);box[1]=Math.min(box[1],b[1]);box[2]=Math.max(box[2],b[2]);box[3]=Math.max(box[3],b[3]);}else if(c.operation()==Operation.INTERSECTION){box[0]=Math.max(box[0],b[0]);box[1]=Math.max(box[1],b[1]);box[2]=Math.min(box[2],b[2]);box[3]=Math.min(box[3],b[3]);}}
            if((long)box[2]-box[0]<2||(long)box[3]-box[1]<2)throw new IllegalArgumentException("Composite intersection bounds must overlap by at least two blocks.");}
        path.remove(name);memo.put(name,box.clone());return box;
    }
    /** Editor convenience: bounds follow the referenced geometry rather than clipping it. */
    public RegionalTerrain normalized(){if(graphError!=null)throw new IllegalArgumentException(graphError);var result=new ArrayList<Zone>();var memo=new HashMap<String,int[]>();for(var zone:zones){var b=bounds(zone.name(),new HashSet<>(),0,memo);result.add(zone.mask()==Mask.COMPOSITE?new Zone(zone.name(),zone.mask(),b[0],b[1],b[2],b[3],zone.priority(),Math.min(zone.transition(),Math.min(b[2]-b[0],b[3]-b[1])/2),zone.elevation(),zone.relief(),zone.terrain(),zone.biome(),zone.vertices(),zone.composition()):zone);}return new RegionalTerrain(result);}
    @Override public boolean equals(Object o) { return o instanceof RegionalTerrain other && zones.equals(other.zones); }
    @Override public int hashCode() { return zones.hashCode(); }
    public enum Mask implements StringRepresentable {
        RECTANGLE, CIRCLE, POLYGON, COMPOSITE, CORRIDOR;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Operation implements StringRepresentable {UNION,INTERSECTION,SUBTRACT;@Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public record Composition(Operation operation,List<String> regions){
        public Composition {regions=List.copyOf(regions);}
        public static final Codec<Composition> CODEC=RecordCodecBuilder.<Composition>create(b->b.group(StringRepresentable.fromEnum(Operation::values).fieldOf("operation").forGetter(Composition::operation),Codec.STRING.listOf(2,8).fieldOf("regions").forGetter(Composition::regions)).apply(b,Composition::new)).validate(c->c.regions().stream().allMatch(n->n.matches("[a-z0-9_-]{1,32}"))&&new HashSet<>(c.regions()).size()==c.regions().size()?DataResult.success(c):DataResult.error(()->"Use 2–8 distinct existing region names."));
    }
    public record Vertex(int x, int z) {
        public static final Codec<Vertex> CODEC = RecordCodecBuilder.create(b -> b.group(
                ExactInt.range(-WORLD_BOUND, WORLD_BOUND).fieldOf("x").forGetter(Vertex::x),
                ExactInt.range(-WORLD_BOUND, WORLD_BOUND).fieldOf("z").forGetter(Vertex::z)).apply(b, Vertex::new));
    }
    /** Union of round-ended segments. Crossings and closed paths need no chunk state. */
    public record Corridor(List<Vertex> points, int radius) {
        public Corridor { points=List.copyOf(points); }
        public static final Codec<Corridor> CODEC=RecordCodecBuilder.<Corridor>create(b->b.group(
                Vertex.CODEC.listOf(2,MAX_VERTICES).fieldOf("points").forGetter(Corridor::points),
                Codec.intRange(1,4096).fieldOf("radius").forGetter(Corridor::radius)).apply(b,Corridor::new)).validate(Corridor::validate);
        public DataResult<Corridor> validate(){
            if(radius<1||radius>4096||points.size()<2||points.size()>MAX_VERTICES)return DataResult.error(()->"Use 2–64 corridor points and a radius of 1–4096 blocks.");
            for(int i=0;i<points.size();i++){var p=points.get(i);if(Math.abs((long)p.x())+radius>WORLD_BOUND||Math.abs((long)p.z())+radius>WORLD_BOUND||i>0&&p.equals(points.get(i-1)))return DataResult.error(()->"Corridor points must fit inside world bounds with their radius and consecutive points must differ.");}
            return DataResult.success(this);
        }
        public int minX(){return points.stream().mapToInt(Vertex::x).min().orElse(0)-radius;}
        public int minZ(){return points.stream().mapToInt(Vertex::z).min().orElse(0)-radius;}
        public int maxX(){return points.stream().mapToInt(Vertex::x).max().orElse(0)+radius;}
        public int maxZ(){return points.stream().mapToInt(Vertex::z).max().orElse(0)+radius;}
        public double edgeDistance(int x,int z){
            double best=Double.POSITIVE_INFINITY;
            for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);double dx=(double)b.x()-a.x(),dz=(double)b.z()-a.z(),ox=(double)x-a.x(),oz=(double)z-a.z(),t=Math.clamp((ox*dx+oz*dz)/(dx*dx+dz*dz),0,1),px=ox-t*dx,pz=oz-t*dz;best=Math.min(best,px*px+pz*pz);}
            return radius-StrictMath.sqrt(best);
        }
    }
    public record Zone(String name, Mask mask, int minX, int minZ, int maxX, int maxZ, int priority, int transition,
                       int elevation, int relief, AdvancedTerrain terrain, Optional<Holder<Biome>> biome, List<Vertex> vertices, Optional<Composition> composition, Optional<Corridor> corridor) {
        /** Retained API 1 composite constructor. */
        public Zone(String name,Mask mask,int minX,int minZ,int maxX,int maxZ,int priority,int transition,int elevation,int relief,AdvancedTerrain terrain,Optional<Holder<Biome>> biome,List<Vertex> vertices,Optional<Composition> composition){this(name,mask,minX,minZ,maxX,maxZ,priority,transition,elevation,relief,terrain,biome,vertices,composition,Optional.empty());}
        public Zone(String name,Mask mask,int minX,int minZ,int maxX,int maxZ,int priority,int transition,int elevation,int relief,AdvancedTerrain terrain,Optional<Holder<Biome>> biome,List<Vertex> vertices){this(name,mask,minX,minZ,maxX,maxZ,priority,transition,elevation,relief,terrain,biome,vertices,Optional.empty());}
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
                Vertex.CODEC.listOf(0,MAX_VERTICES).optionalFieldOf("vertices",List.of()).forGetter(Zone::vertices),
                Composition.CODEC.optionalFieldOf("composition").forGetter(Zone::composition),
                Corridor.CODEC.optionalFieldOf("corridor").forGetter(Zone::corridor)
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
            if((mask==Mask.COMPOSITE)!=composition.isPresent())return DataResult.error(()->"Only composite masks require a composition.");
            if((mask==Mask.CORRIDOR)!=corridor.isPresent())return DataResult.error(()->"Only corridor masks require a corridor path.");
            if(corridor.isPresent()){var c=corridor.orElseThrow();if(c.validate().error().isPresent()||minX!=c.minX()||minZ!=c.minZ()||maxX!=c.maxX()||maxZ!=c.maxZ()||transition>c.radius())return DataResult.error(()->"Corridor bounds must follow the path and radius; feathering cannot exceed its radius.");}
            if(composition.isPresent()&&Composition.CODEC.encodeStart(JsonOps.INSTANCE,composition.orElseThrow()).error().isPresent())return DataResult.error(()->"Use 2–8 distinct valid operands.");
            if (mask == Mask.POLYGON && !validPolygon()) return DataResult.error(() -> "Use 3–64 unique vertices in a simple polygon with nonzero area and matching derived bounds. Holes and crossings are not supported.");
            return DataResult.success(this);
        }
        public Zone withVertices(List<Vertex> points) {
            if(mask==Mask.CORRIDOR)return withCorridor(new Corridor(points,corridor.orElseThrow().radius()));
            return new Zone(name,Mask.POLYGON,points.stream().mapToInt(Vertex::x).min().orElse(0),points.stream().mapToInt(Vertex::z).min().orElse(0),
                    points.stream().mapToInt(Vertex::x).max().orElse(0),points.stream().mapToInt(Vertex::z).max().orElse(0),priority,transition,elevation,relief,terrain,biome,points);
        }
        public List<Vertex> editablePoints(){return corridor.map(Corridor::points).orElse(vertices);}
        public Zone withCorridor(Corridor path){path.validate().getOrThrow();return new Zone(name,Mask.CORRIDOR,path.minX(),path.minZ(),path.maxX(),path.maxZ(),priority,Math.min(transition,path.radius()),elevation,relief,terrain,biome,List.of(),Optional.empty(),Optional.of(path));}
        public Zone translated(int dx,int dz) {
            if(mask==Mask.COMPOSITE)throw new IllegalArgumentException("Move a composite region's operands instead.");
            if(mask==Mask.CORRIDOR)return withCorridor(new Corridor(corridor.orElseThrow().points().stream().map(p->new Vertex(Math.addExact(p.x(),dx),Math.addExact(p.z(),dz))).toList(),corridor.orElseThrow().radius()));
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
            if(mask==Mask.COMPOSITE)throw new IllegalArgumentException("Resolve composite geometry through its RegionalTerrain profile.");
            return distance(x,z,false);
        }
        public double geometricDistance(int x,int z){return distance(x,z,true);}
        private double distance(int x,int z,boolean full) {
            if(mask==Mask.CORRIDOR)return corridor.orElseThrow().edgeDistance(x,z);
            if(mask==Mask.RECTANGLE)return Math.min(Math.min((double)x-minX,(double)maxX-x),Math.min((double)z-minZ,(double)maxZ-z));
            if(mask==Mask.POLYGON){
                if(!full&&(x<minX||x>maxX||z<minZ||z>maxZ))return -1;
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

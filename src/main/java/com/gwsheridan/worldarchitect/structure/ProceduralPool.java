package com.gwsheridan.worldarchitect.structure;

import com.gwsheridan.worldarchitect.config.IslandTemplate;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;

/** Pool format 1. All working state belongs to one bounded candidate, never the world or a shared RNG. */
public record ProceduralPool(String root, int maxModules, int attempts, long salt, List<Port> ports, List<Choice> choices) {
    public static final Codec<ProceduralPool> CODEC=RecordCodecBuilder.<ProceduralPool>create(b->b.group(
            Codec.intRange(1,1).fieldOf("format").forGetter(p->1),Codec.STRING.fieldOf("root").forGetter(ProceduralPool::root),
            Codec.intRange(2,16).optionalFieldOf("max_modules",8).forGetter(ProceduralPool::maxModules),
            Codec.intRange(1,256).optionalFieldOf("attempts",128).forGetter(ProceduralPool::attempts),Codec.LONG.optionalFieldOf("seed_salt",0L).forGetter(ProceduralPool::salt),
            Port.CODEC.listOf(0,128).fieldOf("connections").forGetter(ProceduralPool::ports),Choice.CODEC.listOf(0,64).fieldOf("choices").forGetter(ProceduralPool::choices)
    ).apply(b,(format,root,max,attempts,salt,ports,choices)->new ProceduralPool(root,max,attempts,salt,ports,choices))).validate(ProceduralPool::validate);
    public ProceduralPool { ports=List.copyOf(ports);choices=List.copyOf(choices); }
    public record Port(String module,String name,BlockPos position,Direction facing,String pool,String channel) {
        static final Codec<Port> CODEC=RecordCodecBuilder.create(b->b.group(
                Codec.STRING.fieldOf("module").forGetter(Port::module),Codec.STRING.fieldOf("name").forGetter(Port::name),BlockPos.CODEC.fieldOf("pos").forGetter(Port::position),
                Direction.CODEC.fieldOf("facing").forGetter(Port::facing),Codec.STRING.fieldOf("pool").forGetter(Port::pool),Codec.STRING.optionalFieldOf("channel","default").forGetter(Port::channel)
        ).apply(b,Port::new));
    }
    public record Choice(String pool,String module,int weight,boolean endcap) {
        static final Codec<Choice> CODEC=RecordCodecBuilder.create(b->b.group(
                Codec.STRING.fieldOf("pool").forGetter(Choice::pool),Codec.STRING.fieldOf("module").forGetter(Choice::module),Codec.intRange(1,100).optionalFieldOf("weight",1).forGetter(Choice::weight),Codec.BOOL.optionalFieldOf("endcap",false).forGetter(Choice::endcap)
        ).apply(b,Choice::new));
    }
    private static boolean name(String n){return n.matches("[a-z0-9_-]{1,32}");}
    public DataResult<ProceduralPool> validate(){
        if(!name(root)||maxModules<2||maxModules>16||attempts<1||attempts>256||ports.size()>128||choices.size()>64)return DataResult.error(()->"Pool settings exceed their bounds.");
        var names=new HashSet<String>();for(var p:ports)if(!name(p.module)||!name(p.name)||!name(p.pool)||!name(p.channel)||p.facing.getAxis()==Direction.Axis.Y||!names.add(p.module+":"+p.name))return DataResult.error(()->"Use unique named horizontal connections and valid pool/channel names.");
        names.clear();for(var c:choices)if(!name(c.pool)||!name(c.module)||c.weight<1||c.weight>100||!names.add(c.pool+":"+c.module+":"+c.endcap))return DataResult.error(()->"Pool choices need unique module/role pairs and weights 1–100.");
        return DataResult.success(this);
    }
    DataResult<ProceduralPool> validateCatalog(List<ModularStructure.Module> catalog){
        var basic=validate();if(basic.error().isPresent())return basic;
        var modules=new HashMap<String,LibraryEntry>();catalog.forEach(m->modules.put(m.name(),m.entry()));
        if(!modules.containsKey(root)||ports.stream().noneMatch(p->p.module.equals(root)))return DataResult.error(()->"Select an existing root module with at least one connection.");
        if(ports.stream().filter(p->p.module.equals(root)).count()+1>maxModules)return DataResult.error(()->"The module budget must leave room for root end caps.");
        for(var p:ports){
            var entry=modules.get(p.module);if(entry==null)return DataResult.error(()->"A connection references a missing module.");var size=entry.template().size();var pos=p.position;
            boolean face=switch(p.facing){case WEST->pos.getX()==0;case EAST->pos.getX()==size.getX()-1;case NORTH->pos.getZ()==0;case SOUTH->pos.getZ()==size.getZ()-1;default->false;};
            var cells=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();entry.template().blocks().forEach(c->cells.put(c.pos(),c.state()));
            if(!face||pos.getX()<0||pos.getZ()<0||pos.getX()>=size.getX()||pos.getZ()>=size.getZ()||pos.getY()<1||pos.getY()+1>=size.getY()||cells.containsKey(pos)||cells.containsKey(pos.above())
                    ||!cells.containsKey(pos.below())||!IslandTemplate.safeFloor(cells.get(pos.below()),EmptyBlockGetter.INSTANCE,pos.below()))return DataResult.error(()->"Connections need two clear cells on a named module edge over a safe floor.");
            if(ports.stream().filter(other->other.module.equals(p.module)).count()>8)return DataResult.error(()->"A module supports at most eight connections.");
            if((p.module.equals(root)||choices.stream().anyMatch(c->c.module.equals(p.module)&&!c.endcap)) && choices.stream().noneMatch(c->c.pool.equals(p.pool)&&c.endcap&&ports.stream().anyMatch(cap->cap.module.equals(c.module)&&cap.channel.equals(p.channel))))return DataResult.error(()->"Every connection pool/channel needs a compatible end cap.");
        }
        for(var c:choices){long count=ports.stream().filter(p->p.module.equals(c.module)).count();if(!modules.containsKey(c.module)||count<1||c.endcap&&count!=1)return DataResult.error(()->"Choices need existing modules with connections; end caps must have exactly one.");}
        return DataResult.success(this);
    }
    static long seed(long worldSeed,int chunkX,int chunkZ){long value=worldSeed^(long)chunkX*0x632BE59BD9B4E019L^(long)chunkZ*0x9E3779B97F4A7C15L;value=(value^(value>>>30))*0xBF58476D1CE4E5B9L;return value^(value>>>27);}
    private record Variant(LibraryEntry entry,List<Port> ports) {}
    private record Open(BlockPos position,Direction facing,String pool,String channel) {}
    private record Placed(String name,BlockPos offset,LibraryEntry entry) {}
    Optional<List<ModularStructure.Module>> layout(List<ModularStructure.Module> catalog,long seed)throws IOException{
        if(validateCatalog(catalog).error().isPresent())return Optional.empty();
        var source=new HashMap<String,LibraryEntry>();catalog.forEach(m->source.put(m.name(),m.entry()));
        var variants=new HashMap<String,Variant>();var random=new Random(seed^salt);var placed=new ArrayList<Placed>();var open=new ArrayDeque<Open>();
        var occupied=new HashSet<BlockPos>();var clearance=new HashSet<BlockPos>();int[] budget={0,0};
        var first=variant(root,Rotation.NONE,source,variants);add("root",BlockPos.ZERO,first,null,placed,open,occupied,clearance,budget);
        while(!open.isEmpty()){
            var socket=open.removeFirst();boolean attached=false;
            for(boolean endcap:new boolean[]{false,true}){
                var candidates=new ArrayList<>(choices.stream().filter(c->c.pool.equals(socket.pool)&&c.endcap==endcap).toList());
                while(!candidates.isEmpty()&&!attached){int sum=candidates.stream().mapToInt(Choice::weight).sum(),pick=random.nextInt(sum),index=0;while(pick>=candidates.get(index).weight){pick-=candidates.get(index).weight;index++;}var choice=candidates.remove(index);
                    var rotations=new ArrayList<>(List.of(Rotation.values()));Collections.shuffle(rotations,random);
                    for(var rotation:rotations){var candidate=variant(choice.module,rotation,source,variants);
                        if(placed.size()+1+open.size()+candidate.ports.size()-1>maxModules)continue;
                        for(var port:candidate.ports){if(!port.channel.equals(socket.channel)||port.facing!=socket.facing.getOpposite())continue;
                            if(++budget[0]>attempts)return Optional.empty();
                            var offset=socket.position.relative(socket.facing).subtract(port.position);
                            if(fits(candidate,offset,placed,occupied,clearance,budget[1])){
                                add("module_"+placed.size(),offset,candidate,port,placed,open,occupied,clearance,budget);attached=true;break;
                            }
                        }if(attached)break;
                    }
                }if(attached)break;
            }
            if(!attached)return Optional.empty();
        }
        int minX=placed.stream().mapToInt(p->p.offset.getX()).min().orElse(0),minY=placed.stream().mapToInt(p->p.offset.getY()).min().orElse(0),minZ=placed.stream().mapToInt(p->p.offset.getZ()).min().orElse(0);
        var shift=new BlockPos(minX,minY,minZ);return Optional.of(placed.stream().map(p->new ModularStructure.Module(p.name,p.offset.subtract(shift),p.entry)).toList());
    }
    private Variant variant(String module,Rotation rotation,Map<String,LibraryEntry> source,Map<String,Variant> cache)throws IOException{
        String key=module+":"+rotation;var result=cache.get(key);if(result!=null)return result;var entry=source.get(module);var transform=new TemplatePlacement(BlockPos.ZERO,rotation,Mirror.NONE);
        var transformed=entry.transformed(transform);var connections=ports.stream().filter(p->p.module.equals(module)).map(p->new Port(p.module,p.name,transform.position(entry.template().size(),p.position),transform.facing(p.facing),p.pool,p.channel)).toList();
        result=new Variant(transformed,connections);cache.put(key,result);return result;
    }
    private static boolean fits(Variant variant,BlockPos offset,List<Placed> placed,Set<BlockPos> occupied,Set<BlockPos> clearance,int blocks){
        if(blocks+variant.entry.template().blocks().size()>ModularStructure.MAX_BLOCKS)return false;
        int minX=offset.getX(),minY=offset.getY(),minZ=offset.getZ(),maxX=minX+variant.entry.template().size().getX(),maxY=minY+variant.entry.template().size().getY(),maxZ=minZ+variant.entry.template().size().getZ();
        for(var p:placed){minX=Math.min(minX,p.offset.getX());minY=Math.min(minY,p.offset.getY());minZ=Math.min(minZ,p.offset.getZ());maxX=Math.max(maxX,p.offset.getX()+p.entry.template().size().getX());maxY=Math.max(maxY,p.offset.getY()+p.entry.template().size().getY());maxZ=Math.max(maxZ,p.offset.getZ()+p.entry.template().size().getZ());}
        if(maxX-minX>96||maxY-minY>96||maxZ-minZ>96)return false;
        for(var cell:variant.entry.template().blocks()){var pos=offset.offset(cell.pos());if(occupied.contains(pos)||clearance.contains(pos))return false;}
        for(var marker:variant.entry.markers().values()){var pos=offset.offset(marker.pos());if(occupied.contains(pos)||occupied.contains(pos.above()))return false;}
        for(var port:variant.ports){var pos=offset.offset(port.position);if(occupied.contains(pos)||occupied.contains(pos.above()))return false;}
        return true;
    }
    private static void add(String name,BlockPos offset,Variant variant,Port joined,List<Placed> placed,Deque<Open> open,Set<BlockPos> occupied,Set<BlockPos> clearance,int[] budget){
        placed.add(new Placed(name,offset,variant.entry));for(var cell:variant.entry.template().blocks())occupied.add(offset.offset(cell.pos()));budget[1]+=variant.entry.template().blocks().size();
        for(var marker:variant.entry.markers().values()){clearance.add(offset.offset(marker.pos()));clearance.add(offset.offset(marker.pos()).above());}
        for(var port:variant.ports){var pos=offset.offset(port.position);clearance.add(pos);clearance.add(pos.above());if(port!=joined)open.addLast(new Open(pos,port.facing,port.pool,port.channel));}
    }
}

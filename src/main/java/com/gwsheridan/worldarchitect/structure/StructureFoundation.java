package com.gwsheridan.worldarchitect.structure;

import com.gwsheridan.worldarchitect.config.ExactInt;
import com.gwsheridan.worldarchitect.config.IslandTemplate;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in, bounded base-terrain preparation. The complete plan is validated before native pieces are emitted. */
public record StructureFoundation(Mode mode,BlockState material,int maxDepth,int clearHeight,boolean replaceWater) {
    public static final int MAX_CELLS=4096,MAX_PIECES=32;
    public enum Mode implements StringRepresentable {POSTS,FOOTPRINT;public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public static final Codec<StructureFoundation> CODEC=RecordCodecBuilder.<StructureFoundation>create(b->b.group(
            ExactInt.range(1,1).fieldOf("version").forGetter(v->1),StringRepresentable.fromEnum(Mode::values).fieldOf("mode").forGetter(StructureFoundation::mode),
            Codec.lazyInitialized(()->BlockState.CODEC).fieldOf("material").forGetter(StructureFoundation::material),ExactInt.range(1,16).fieldOf("max_depth").forGetter(StructureFoundation::maxDepth),
            ExactInt.range(0,8).optionalFieldOf("clear_height",0).forGetter(StructureFoundation::clearHeight),Codec.BOOL.optionalFieldOf("replace_water",false).forGetter(StructureFoundation::replaceWater)
    ).apply(b,(version,mode,material,depth,clear,water)->new StructureFoundation(mode,material,depth,clear,water))).validate(StructureFoundation::validate);
    public DataResult<StructureFoundation> validate(){return maxDepth>=1&&maxDepth<=16&&clearHeight>=0&&clearHeight<=8&&!material.hasBlockEntity()&&material.getFluidState().isEmpty()&&!(material.getBlock() instanceof FallingBlock)&&!TemplateMetadata.restricted(material)&&IslandTemplate.safeFloor(material,EmptyBlockGetter.INSTANCE,BlockPos.ZERO)?DataResult.success(this):DataResult.error(()->"Use a stable full support block without metadata or fluid, depth 1–16 and clearance 0–8.");}
    public record Plan(List<ModularPiece> pieces,Map<BlockPos,BlockState> writes){public Plan{pieces=List.copyOf(pieces);writes=Map.copyOf(writes);}}
    Optional<Plan> plan(List<ModularPiece> assembly,Function<BlockPos,NoiseColumn> column,LevelHeightAccessor height)throws IOException{
        var bottom=new TreeMap<Long,BlockPos>();var occupied=new HashSet<BlockPos>();
        for(var piece:assembly)for(var cell:piece.entry().template().blocks()){var p=piece.origin().offset(cell.pos());occupied.add(p);if(!cell.state().isAir())bottom.merge(BlockPos.asLong(p.getX(),0,p.getZ()),p,(a,b)->a.getY()<b.getY()?a:b);}
        var supports=new LinkedHashMap<BlockPos,BlockState>();var clears=new LinkedHashMap<BlockPos,BlockState>();
        int minX=bottom.values().stream().mapToInt(BlockPos::getX).min().orElse(0),maxX=bottom.values().stream().mapToInt(BlockPos::getX).max().orElse(0),minZ=bottom.values().stream().mapToInt(BlockPos::getZ).min().orElse(0),maxZ=bottom.values().stream().mapToInt(BlockPos::getZ).max().orElse(0);
        var posts=new HashSet<Long>();for(int x:new int[]{minX,maxX})for(int z:new int[]{minZ,maxZ})bottom.values().stream().min(Comparator.comparingLong(p->(long)(p.getX()-x)*(p.getX()-x)+(long)(p.getZ()-z)*(p.getZ()-z))).ifPresent(p->posts.add(BlockPos.asLong(p.getX(),0,p.getZ())));
        for(var p:bottom.values()){
            SiteProbe.check();
            if(mode==Mode.FOOTPRINT||posts.contains(BlockPos.asLong(p.getX(),0,p.getZ()))){
                boolean grounded=false;var base=column.apply(p);
                for(int depth=1;depth<=maxDepth+1;depth++){var q=p.below(depth);if(!height.isInsideBuildHeight(q))return reject("Foundation exceeds dimension height.");var state=base.getBlock(q.getY());
                    if(!state.hasBlockEntity()&&IslandTemplate.safeFloor(state,EmptyBlockGetter.INSTANCE,q)){grounded=true;break;}
                    if(depth>maxDepth||occupied.contains(q)||!replaceable(state,replaceWater))return reject("Foundation cannot reach safe ground within its depth and collision limits at "+q.toShortString());supports.put(q,material);
                    if(supports.size()+clears.size()>MAX_CELLS)return reject("Foundation preparation exceeds 4,096 cells.");
                }if(!grounded)return reject("Foundation has no stable ground.");
            }
            for(int dy=0;dy<clearHeight;dy++){var q=p.above(dy);if(!height.isInsideBuildHeight(q))return reject("Clearance exceeds dimension height.");var state=column.apply(q).getBlock(q.getY());if(state.isAir())continue;if(!clearable(state,replaceWater))return reject("Clearance cannot replace protected terrain, containers, lava or waterlogged solids at "+q.toShortString());clears.put(q,Blocks.AIR.defaultBlockState());if(supports.size()+clears.size()>MAX_CELLS)return reject("Foundation preparation exceeds 4,096 cells.");}
        }
        var pieces=new ArrayList<ModularPiece>();pieces.addAll(pieces(supports,"support"));pieces.addAll(pieces(clears,"clear"));if(pieces.size()>MAX_PIECES)return reject("Foundation preparation exceeds 32 pieces.");
        var writes=new HashMap<>(supports);writes.putAll(clears);return Optional.of(new Plan(pieces,writes));
    }
    private static Optional<Plan> reject(String text){SiteProbe.reject(text);return Optional.empty();}
    static boolean replaceable(BlockState state,boolean water){return state.isAir()||water&&state.is(Blocks.WATER);}
    static boolean clearable(BlockState state,boolean water){return !state.hasBlockEntity()&&!TemplateMetadata.restricted(state)&&(state.isAir()||state.is(Blocks.WATER)?replaceable(state,water):state.getFluidState().isEmpty()&&state.getDestroySpeed(EmptyBlockGetter.INSTANCE,BlockPos.ZERO)>=0);}
    private List<ModularPiece> pieces(Map<BlockPos,BlockState> writes,String operation)throws IOException{
        var tiles=new TreeMap<String,List<Map.Entry<BlockPos,BlockState>>>();for(var cell:writes.entrySet()){var p=cell.getKey();String key=Math.floorDiv(p.getX(),32)+":"+Math.floorDiv(p.getY(),32)+":"+Math.floorDiv(p.getZ(),32);tiles.computeIfAbsent(key,k->new ArrayList<>()).add(cell);}
        var pieces=new ArrayList<ModularPiece>();for(var cells:tiles.values()){int x=cells.stream().mapToInt(c->c.getKey().getX()).min().orElseThrow(),y=cells.stream().mapToInt(c->c.getKey().getY()).min().orElseThrow(),z=cells.stream().mapToInt(c->c.getKey().getZ()).min().orElseThrow();var origin=new BlockPos(x,y,z);var size=new BlockPos(cells.stream().mapToInt(c->c.getKey().getX()).max().orElseThrow()-x+1,cells.stream().mapToInt(c->c.getKey().getY()).max().orElseThrow()-y+1,cells.stream().mapToInt(c->c.getKey().getZ()).max().orElseThrow()-z+1);
            var template=new NativeTemplate(size,cells.stream().sorted(Comparator.comparingInt((Map.Entry<BlockPos,BlockState> c)->c.getKey().getY()).thenComparingInt(c->c.getKey().getX()).thenComparingInt(c->c.getKey().getZ())).map(c->new NativeTemplate.Cell(c.getKey().subtract(origin),c.getValue(),null)).toList());var entry=LibraryEntry.read(template,new net.minecraft.nbt.CompoundTag(),"Structure "+operation,"");pieces.add(new ModularPiece("prepare_"+operation+"_"+pieces.size(),origin,entry,operation,replaceWater));}
        return pieces;
    }
}

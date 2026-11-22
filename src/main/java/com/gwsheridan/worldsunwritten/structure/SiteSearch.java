package com.gwsheridan.worldsunwritten.structure;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Candidate enumeration is bounded as well as sampling. No search step accesses a live level. */
public final class SiteSearch {
    public static final int MAX_CANDIDATES=256,MAX_RESULTS=32,MAX_RADIUS=128;
    public record Bounds(int minX,int minZ,int maxX,int maxZ,int candidates,int results){
        public Bounds{if(minX>maxX||minZ>maxZ||(long)maxX-minX>256||(long)maxZ-minZ>256||candidates<1||candidates>MAX_CANDIDATES||results<1||results>MAX_RESULTS)throw new IllegalArgumentException("Use coordinate spans up to 256 chunks, 256 native regions and 32 results.");for(int c:new int[]{minX,minZ,maxX,maxZ})if(Math.abs((long)c)*16>29_999_792)throw new IllegalArgumentException("Keep the search inside world bounds.");}
        boolean contains(ChunkPos p){return p.x()>=minX&&p.x()<=maxX&&p.z()>=minZ&&p.z()<=maxZ;}
    }
    private record Region(int binding,int x,int z,ChunkPos chunk,long distance){}
    private SiteSearch(){}
    public static CompoundTag search(SiteInspector.Input input,List<RandomSpreadStructurePlacement> placements,Bounds bounds,boolean generationEnabled){return search(input,placements,bounds,generationEnabled,5000);}
    public static CompoundTag search(SiteInspector.Input input,List<RandomSpreadStructurePlacement> placements,Bounds bounds,boolean generationEnabled,int milliseconds){
        if(milliseconds<0||milliseconds>5000||placements.isEmpty()||placements.size()>128)throw new IllegalArgumentException("Use a bounded search with 1–128 native placement bindings.");
        long deadline=System.nanoTime()+milliseconds*1_000_000L;int centerX=(int)(((long)bounds.minX()+bounds.maxX())/2),centerZ=(int)(((long)bounds.minZ()+bounds.maxZ())/2),visited=0,sampled=0,incomplete=0;boolean stopped=false;
        var results=new ListTag();var queue=new PriorityQueue<Region>(Comparator.comparingLong(Region::distance).thenComparingInt(Region::binding).thenComparingInt(Region::x).thenComparingInt(Region::z));var seen=new HashSet<String>();var sampledChunks=new HashSet<Long>();
        if(generationEnabled)for(int i=0;i<placements.size();i++)offer(queue,seen,placements,input.seed(),bounds,centerX,centerZ,i,Math.floorDiv(centerX,placements.get(i).spacing()),Math.floorDiv(centerZ,placements.get(i).spacing()));
        while(!queue.isEmpty()&&visited<bounds.candidates()&&results.size()<bounds.results()){
            if(Thread.currentThread().isInterrupted()||System.nanoTime()>=deadline){stopped=true;break;}var region=queue.remove();visited++;
            for(int[] delta:new int[][]{{-1,0},{1,0},{0,-1},{0,1}})offer(queue,seen,placements,input.seed(),bounds,centerX,centerZ,region.binding(),region.x()+delta[0],region.z()+delta[1]);
            if(!bounds.contains(region.chunk())||!sampledChunks.add(region.chunk().pack()))continue;sampled++;
            var candidate=new SiteInspector.Input(input.structure(),input.dimension(),input.generator(),input.random(),input.registries(),input.templates(),input.height(),input.seed(),region.chunk(),true,region.chunk());var report=SiteInspector.inspect(candidate,deadline);String status=report.getStringOr("status","");if(status.equals("incomplete")){incomplete++;continue;}if(!status.equals("eligible"))continue;
            var row=new CompoundTag();row.putInt("chunk_x",region.chunk().x());row.putInt("chunk_z",region.chunk().z());row.putString("status","eligible");row.putIntArray("origin",report.getIntArray("origin").orElse(new int[0]));row.putIntArray("root",report.getIntArray("root").orElse(new int[0]));row.putInt("markers",report.getListOrEmpty("markers").size());row.putInt("supports",report.getIntOr("support_cells",0));row.putInt("clearance",report.getIntArray("clear_cells").orElse(new int[0]).length);results.add(row);
        }
        stopped|=!queue.isEmpty();var report=new CompoundTag();report.putString("kind","site");report.putString("status",!generationEnabled?"rejected":stopped||incomplete>0?"incomplete":"complete");report.putString("dimension",input.dimension().identifier().toString());report.putString("structure",input.structure().unwrapKey().orElseThrow().identifier().toString());report.putString("seed",Long.toString(input.seed()));report.putInt("chunk_x",centerX);report.putInt("chunk_z",centerZ);report.putIntArray("search_bounds",new int[]{bounds.minX(),bounds.minZ(),bounds.maxX(),bounds.maxZ()});report.putInt("candidate_limit",bounds.candidates());report.putInt("result_limit",bounds.results());report.putInt("visited_regions",visited);report.putInt("sampled_candidates",sampled);report.putInt("incomplete_candidates",incomplete);report.put("results",results);
        var rows=new ListTag();rows.add(StringTag.valueOf(!generationEnabled?"Structure generation is disabled in this preset.":results.size()+" eligible native candidates · "+sampled+" sampled · "+visited+" placement regions visited."));if(stopped||incomplete>0)rows.add(StringTag.valueOf("Search incomplete: a time, candidate or result limit was reached. No conclusion is available for unvisited candidates."));rows.add(StringTag.valueOf("Results use the production base-terrain planner. Later features and ticking remain authoritative."));rows.add(StringTag.valueOf("No chunks were created and no generation files were changed."));report.put("rows",rows);return report;
    }
    private static void offer(PriorityQueue<Region> queue,Set<String> seen,List<RandomSpreadStructurePlacement> placements,long seed,Bounds bounds,int centerX,int centerZ,int binding,int x,int z){
        var placement=placements.get(binding);int spacing=placement.spacing();if(x<Math.floorDiv(bounds.minX(),spacing)||x>Math.floorDiv(bounds.maxX(),spacing)||z<Math.floorDiv(bounds.minZ(),spacing)||z>Math.floorDiv(bounds.maxZ(),spacing))return;String key=binding+":"+x+":"+z;if(!seen.add(key))return;var chunk=placement.getPotentialStructureChunk(seed,x*spacing,z*spacing);long dx=(long)chunk.x()-centerX,dz=(long)chunk.z()-centerZ;queue.add(new Region(binding,x,z,chunk,dx*dx+dz*dz));
    }
}

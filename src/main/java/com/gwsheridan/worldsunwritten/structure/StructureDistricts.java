package com.gwsheridan.worldsunwritten.structure;

import com.gwsheridan.worldsunwritten.config.ContentProfile.StructureRule;
import com.gwsheridan.worldsunwritten.config.RegionalTerrain;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

/** Pure, bounded ownership of potential sites. It never reads generated chunks or starts. */
public final class StructureDistricts {
    public static final int MAX_GROUPS=8,MAX_DISTANCE=32;
    private StructureDistricts() {}
    public record Member(String group,String exclusion,int weight,int minDistance,Optional<String> region) {
        public static final Codec<Member> CODEC=RecordCodecBuilder.<Member>create(b->b.group(
                Codec.intRange(1,1).optionalFieldOf("version",1).forGetter(m->1),
                Codec.STRING.fieldOf("group").forGetter(Member::group),
                Codec.STRING.optionalFieldOf("exclusion","settlements").forGetter(Member::exclusion),
                Codec.intRange(1,100).optionalFieldOf("weight",1).forGetter(Member::weight),
                Codec.intRange(0,MAX_DISTANCE).optionalFieldOf("min_distance",0).forGetter(Member::minDistance),
                Codec.STRING.optionalFieldOf("region").forGetter(Member::region)
        ).apply(b,(v,g,e,w,d,r)->new Member(g,e,w,d,r))).validate(Member::validate);
        public DataResult<Member> validate(){return group.matches("[a-z0-9_-]{1,32}")&&exclusion.matches("[a-z0-9_-]{1,32}")&&weight>=1&&weight<=100&&minDistance>=0&&minDistance<=MAX_DISTANCE&&region.stream().allMatch(s->s.matches("[a-z0-9_-]{1,32}"))?DataResult.success(this):DataResult.error(()->"Use simple district/group names, weights 1–100 and a distance of 0–32 chunks.");}
        boolean sameSettings(Member other){return group.equals(other.group)&&exclusion.equals(other.exclusion)&&minDistance==other.minDistance&&region.equals(other.region);}
    }
    public static Optional<String> problem(List<StructureRule> rules,Optional<RegionalTerrain> regions,boolean bound){
        var groups=groups(rules);if(groups.size()>MAX_GROUPS)return Optional.of("A dimension supports at most eight structure districts.");
        for(var members:groups.values()){
            var first=members.getFirst();var settings=first.district().orElseThrow();
            if(settings.validate().error().isPresent())return Optional.of("Invalid district settings.");
            if(members.stream().anyMatch(r->r.spacing()!=first.spacing()||r.separation()!=first.separation()||!settings.sameSettings(r.district().orElseThrow())))return Optional.of("Members of a district must share spacing, separation, region, exclusion group and minimum distance.");
            if(settings.region().isPresent()&&bound&&regions.stream().flatMap(r->r.zones().stream()).noneMatch(z->z.name().equals(settings.region().orElseThrow())))return Optional.of("District region is missing: "+settings.region().orElseThrow());
            if(bound&&members.stream().anyMatch(r->!r.structure().isBound()||!(r.structure().value() instanceof ModularStructure)))return Optional.of("Districts support Worlds Unwritten modular structures only.");
        }
        return Optional.empty();
    }
    private static Map<String,List<StructureRule>> groups(List<StructureRule> rules){
        var result=new TreeMap<String,List<StructureRule>>();
        for(var rule:rules)rule.district().ifPresent(d->result.computeIfAbsent(d.group(),k->new ArrayList<>()).add(rule));
        result.values().forEach(list->list.sort(Comparator.comparing(r->r.structure().unwrapKey().orElseThrow().identifier().toString())));
        return result;
    }
    public static RandomSpreadStructurePlacement placement(StructureRule rule){
        int salt=rule.district().map(d->(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds.NAMESPACE+":district/"+d.group()).hashCode()).orElseGet(()->rule.structure().unwrapKey().orElseThrow().identifier().toString().hashCode())&Integer.MAX_VALUE;
        return new RandomSpreadStructurePlacement(rule.spacing(),rule.separation(),RandomSpreadType.LINEAR,salt);
    }
    public static Stream<Holder<StructureSet>> placements(List<StructureRule> rules){
        var result=new ArrayList<Holder<StructureSet>>();
        for(var rule:rules)if(rule.district().isEmpty())result.add(Holder.direct(new StructureSet(rule.structure(),placement(rule))));
        for(var members:groups(rules).values())result.add(Holder.direct(new StructureSet(members.stream().map(r->new StructureSet.StructureSelectionEntry(r.structure(),r.district().orElseThrow().weight())).toList(),placement(members.getFirst()))));
        return result.stream();
    }
    private static long mix(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
    private static long rank(long seed,String group,ChunkPos chunk){return mix(seed^((long)group.hashCode()*0x9e3779b97f4a7c15L)^((long)chunk.x()*0x632be59bd9b4e019L)^((long)chunk.z()*0x9e3779b97f4a7c15L));}
    private static StructureRule selected(List<StructureRule> members,long seed,ChunkPos chunk){
        return members.get(weightedIndex(members.stream().map(r->r.district().orElseThrow().weight()).toList(),seed,members.getFirst().district().orElseThrow().group(),chunk));
    }
    static int weightedIndex(List<Integer> weights,long seed,String group,ChunkPos chunk){
        if(weights.isEmpty()||weights.size()>16||weights.stream().anyMatch(w->w<1||w>100))throw new IllegalArgumentException("Invalid district weights");
        int total=weights.stream().mapToInt(Integer::intValue).sum(),choice=(int)Long.remainderUnsigned(mix(rank(seed,group,chunk)^0x243f6a8885a308d3L),total);
        for(int i=0;i<weights.size();i++){choice-=weights.get(i);if(choice<0)return i;}throw new IllegalStateException("Invalid weighted selection");
    }
    static boolean excludedBy(Member own,ChunkPos chunk,Member other,ChunkPos candidate,long seed){
        if(!own.exclusion().equals(other.exclusion())||own.group().equals(other.group())&&chunk.equals(candidate))return false;
        int distance=Math.max(own.minDistance(),other.minDistance());long dx=(long)candidate.x()-chunk.x(),dz=(long)candidate.z()-chunk.z();if(dx*dx+dz*dz>=(long)distance*distance)return false;
        int order=Long.compareUnsigned(rank(seed,other.group(),candidate),rank(seed,own.group(),chunk));if(order==0)order=other.group().compareTo(own.group());if(order==0)order=Integer.compare(candidate.x(),chunk.x());if(order==0)order=Integer.compare(candidate.z(),chunk.z());return order<0;
    }
    private static boolean inside(Member member,Optional<RegionalTerrain> regions,ChunkPos chunk){
        if(member.region().isEmpty())return true;
        return regions.stream().anyMatch(r->r.zones().stream().filter(z->z.name().equals(member.region().orElseThrow())).anyMatch(z->r.edgeDistance(z,chunk.getMinBlockX(),chunk.getMinBlockZ())>=0));
    }
    /** Rejections reserve potential sites conservatively, before terrain eligibility is known. */
    public static Optional<String> rejection(SolidColumnGenerator generator,Holder<Structure> structure,long seed,ChunkPos chunk){
        if(member(generator,structure.value()).isEmpty())return Optional.empty();
        if(generator.endProgression().isPresent()&&com.gwsheridan.worldsunwritten.config.EndProgression.contains(chunk.getMinBlockX(),chunk.getMinBlockZ()))return Optional.of("District placement is disabled inside the native End arena.");
        return rejection(generator.structureRules(),generator.terrainRegions(),structure,seed,chunk);
    }
    public static Optional<String> rejection(List<StructureRule> rules,Optional<RegionalTerrain> regions,Holder<Structure> structure,long seed,ChunkPos chunk){
        if(Math.abs((long)chunk.x())*16>RegionalTerrain.WORLD_BOUND||Math.abs((long)chunk.z())*16>RegionalTerrain.WORLD_BOUND)return Optional.of("District site is outside world bounds.");
        var own=rules.stream().filter(r->r.district().isPresent()&&r.structure().value()==structure.value()).findFirst();if(own.isEmpty())return Optional.empty();
        var member=own.orElseThrow().district().orElseThrow();var groups=groups(rules);var members=groups.get(member.group());
        if(!placement(own.orElseThrow()).getPotentialStructureChunk(seed,chunk.x(),chunk.z()).equals(chunk))return Optional.of("District grid does not select this chunk.");
        if(!inside(member,regions,chunk))return Optional.of("District "+member.group()+" excludes this region.");
        if(selected(members,seed,chunk).structure().value()!=structure.value())return Optional.of("District "+member.group()+" selected another weighted assembly.");
        int work=0;
        for(var other:groups.values()){
            var first=other.getFirst();var settings=first.district().orElseThrow();if(!settings.exclusion().equals(member.exclusion()))continue;
            int distance=Math.max(member.minDistance(),settings.minDistance());if(distance==0)continue;
            int spacing=first.spacing(),loX=Math.floorDiv(chunk.x()-distance,spacing),hiX=Math.floorDiv(chunk.x()+distance,spacing),loZ=Math.floorDiv(chunk.z()-distance,spacing),hiZ=Math.floorDiv(chunk.z()+distance,spacing);var placement=placement(first);
            for(int z=loZ;z<=hiZ;z++)for(int x=loX;x<=hiX;x++){
                SiteProbe.check();if(++work>4096)throw new IllegalStateException("District candidate budget exceeded");
                var candidate=placement.getPotentialStructureChunk(seed,x*spacing,z*spacing);
                if(settings.group().equals(member.group())&&candidate.equals(chunk))continue;
                if(inside(settings,regions,candidate)&&excludedBy(member,chunk,settings,candidate,seed))return Optional.of("District exclusion "+member.exclusion()+" reserves a nearby higher-ranked site for "+settings.group()+".");
            }
        }
        return Optional.empty();
    }
    public static Optional<Member> member(SolidColumnGenerator generator,Structure structure){return generator.structureRules().stream().filter(r->r.structure().value()==structure).flatMap(r->r.district().stream()).findFirst();}
    public static boolean cellAllowed(SolidColumnGenerator generator,Structure structure,int x,int z){
        var member=member(generator,structure);if(member.isEmpty()||member.orElseThrow().region().isEmpty())return true;
        String name=member.orElseThrow().region().orElseThrow();return generator.terrainRegions().stream().anyMatch(r->r.zones().stream().filter(zone->zone.name().equals(name)).anyMatch(zone->r.edgeDistance(zone,x,z)>=0));
    }
}

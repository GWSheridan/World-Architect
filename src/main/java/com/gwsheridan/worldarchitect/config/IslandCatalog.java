package com.gwsheridan.worldarchitect.config;

import com.gwsheridan.worldarchitect.api.IslandLayout;
import com.gwsheridan.worldarchitect.structure.IslandBuild;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Versioned, seed-addressed slot map. Allocation never changes generated geometry. */
public record IslandCatalog(List<Entry> entries, String lobby, String mode, boolean teams, int originY, int spacing, int personalSlots) {
    public static final Codec<IslandCatalog> CODEC=RecordCodecBuilder.<IslandCatalog>create(b->b.group(
            Entry.CODEC.listOf(1,16).fieldOf("types").forGetter(IslandCatalog::entries),
            Codec.STRING.fieldOf("lobby").forGetter(IslandCatalog::lobby),
            Codec.STRING.fieldOf("mode").forGetter(IslandCatalog::mode),
            Codec.BOOL.optionalFieldOf("teams",false).forGetter(IslandCatalog::teams),
            Codec.intRange(-2032,2031).fieldOf("origin_y").forGetter(IslandCatalog::originY),
            Codec.intRange(64,4096).optionalFieldOf("spacing",128).forGetter(IslandCatalog::spacing),
            Codec.intRange(0,4095).optionalFieldOf("personal_slots",0).forGetter(IslandCatalog::personalSlots)
    ).apply(b,IslandCatalog::new)).validate(IslandCatalog::validate);
    public IslandCatalog {entries=entries.stream().sorted(Comparator.comparing(Entry::id)).toList();}
    public DataResult<IslandCatalog> validate(){
        if(entries.isEmpty()||entries.size()>16||entries.stream().map(Entry::id).distinct().count()!=entries.size()||entries.stream().noneMatch(e->e.id().equals(lobby))||!Set.of("fixed","seeded","choice").contains(mode)||spacing%16!=0||spacing<64||spacing>4096||personalSlots<0||personalSlots>4095||originY< -2032||originY>2031||teams&&personalSlots==0||mode.equals("choice")&&personalSlots==0)return DataResult.error(()->"Invalid island catalog, layout, lobby or selection mode.");
        for(var e:entries){if(!e.id().matches("[a-z][a-z0-9_-]{0,31}")||e.title().isBlank()||e.title().length()>80||e.description().length()>512||e.weight()<1||e.weight()>100)return DataResult.error(()->"Invalid island type metadata or weight.");var issue=e.build().validate().error();if(issue.isPresent())return DataResult.error(()->e.id()+": "+issue.orElseThrow().message());}
        return DataResult.success(this);
    }
    public Entry entry(int slot,long seed){
        if(slot<0||slot>personalSlots)throw new IllegalArgumentException("Invalid island slot.");
        if(slot==0||mode.equals("fixed"))return entries.stream().filter(e->e.id().equals(lobby)).findFirst().orElseThrow();
        return entries.get(weightedIndex(entries.stream().map(Entry::weight).toList(),seed,slot));
    }
    public static int weightedIndex(List<Integer> weights,long seed,int slot){
        if(weights.isEmpty()||weights.size()>16||weights.stream().anyMatch(w->w==null||w<1||w>100)||slot<1||slot>4095)throw new IllegalArgumentException("Invalid island weights or slot.");
        int total=weights.stream().mapToInt(Integer::intValue).sum();long mixed=seed+0x9e3779b97f4a7c15L*slot;mixed=(mixed^(mixed>>>30))*0xbf58476d1ce4e5b9L;mixed=(mixed^(mixed>>>27))*0x94d049bb133111ebL;mixed^=mixed>>>31;int selected=(int)Long.remainderUnsigned(mixed,total);
        for(int i=0;i<weights.size();i++){selected-=weights.get(i);if(selected<0)return i;}throw new AssertionError();
    }
    public IslandLayout layout(long seed){return layout(seed,0,0);}
    public IslandLayout layout(long seed,int offsetX,int offsetZ){return new Layout(this,seed,offsetX,offsetZ);}
    public record Entry(String id,String title,String description,String icon,int weight,IslandBuild build){
        public static final Codec<Entry> CODEC=RecordCodecBuilder.create(b->b.group(Codec.STRING.fieldOf("id").forGetter(Entry::id),Codec.STRING.fieldOf("title").forGetter(Entry::title),Codec.STRING.optionalFieldOf("description","").forGetter(Entry::description),Codec.STRING.optionalFieldOf("icon","minecraft:grass_block").forGetter(Entry::icon),Codec.intRange(1,100).optionalFieldOf("weight",1).forGetter(Entry::weight),IslandBuild.CODEC.fieldOf("template").forGetter(Entry::build)).apply(b,Entry::new));
    }
    private record Layout(IslandCatalog catalog,long seed,int offsetX,int offsetZ) implements IslandLayout {
        public int originY(){return catalog.originY;}public int spacing(){return catalog.spacing;}public int personalSlots(){return catalog.personalSlots;}
        public BlockPos origin(int slot){return new BlockPos(offsetX+slot%16*spacing(),originY(),offsetZ+slot/16*spacing());}
        public int slotAt(int worldX,int worldZ){long x=(long)worldX-offsetX,z=(long)worldZ-offsetZ;if(x<0||z<0||x/spacing()>=16)return -1;long s=(z/spacing())*16+x/spacing();if(s>personalSlots())return -1;var size=catalog.entry((int)s,seed).build().size();return x%spacing()<size.getX()&&z%spacing()<size.getZ()?(int)s:-1;}
        public BlockPos spawn(int slot){return origin(slot).offset(catalog.entry(slot,seed).build().spawn());}
        public List<BlockPos> floors(int slot){return catalog.entry(slot,seed).build().cells().stream().map(c->origin(slot).offset(c.pos())).toList();}
        public String type(int slot){return catalog.entry(slot,seed).id();}
    }
}

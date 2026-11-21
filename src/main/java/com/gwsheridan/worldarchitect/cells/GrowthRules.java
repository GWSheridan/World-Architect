package com.gwsheridan.worldarchitect.cells;

import com.gwsheridan.worldarchitect.api.CellLayout.Cell;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/** Immutable objective definitions; completion and activation belong in a separate saved ledger. */
public record GrowthRules(Scope scope,int radius,int territories,int blocksPerTick,boolean chooseFrontier,List<Goal> goals) {
    public static final GrowthRules STATIC=new GrowthRules(Scope.STATIC,4,64,4096,false,List.of());
    public GrowthRules {goals=List.copyOf(goals);}
    public static final Codec<GrowthRules> CODEC=RecordCodecBuilder.<GrowthRules>create(b->b.group(
            StringRepresentable.fromEnum(Scope::values).fieldOf("scope").forGetter(GrowthRules::scope),
            Codec.intRange(1,15).optionalFieldOf("radius",4).forGetter(GrowthRules::radius),
            Codec.intRange(1,255).optionalFieldOf("territories",64).forGetter(GrowthRules::territories),
            Codec.intRange(256,16384).optionalFieldOf("blocks_per_tick",4096).forGetter(GrowthRules::blocksPerTick),
            Codec.BOOL.optionalFieldOf("choose_frontier",false).forGetter(GrowthRules::chooseFrontier),
            Goal.CODEC.listOf(0,128).optionalFieldOf("goals",List.of()).forGetter(GrowthRules::goals)
    ).apply(b,GrowthRules::new)).validate(GrowthRules::validate);
    public DataResult<GrowthRules> validate(){
        if(radius<1||radius>15||territories<1||territories>255||blocksPerTick<256||blocksPerTick>16384||goals.size()>128)return DataResult.error(()->"Invalid growth limits.");
        if(scope==Scope.STATIC&&!goals.isEmpty())return DataResult.error(()->"Static worlds cannot award expansion objectives.");
        var names=new HashMap<String,Goal>();for(var goal:goals){if(!goal.id.matches("[a-z][a-z0-9_-]{0,39}")||goal.title.isBlank()||goal.title.length()>100||goal.source.toString().length()>200||goal.credits<0||goal.credits>64||goal.targets.size()>64||new HashSet<>(goal.targets).size()!=goal.targets.size()||goal.targets.contains(Cell.ORIGIN)||goal.requires.size()>16||new HashSet<>(goal.requires).size()!=goal.requires.size()||goal.credits==0&&goal.targets.isEmpty()||names.put(goal.id,goal)!=null)return DataResult.error(()->"Use unique objective IDs, prerequisites and non-origin cell rewards within the saved limits.");}
        try{for(var goal:goals)visit(goal.id,names,new HashSet<>(),new HashSet<>());}catch(IllegalArgumentException e){return DataResult.error(e::getMessage);}
        return DataResult.success(this);
    }
    private static void visit(String name,Map<String,Goal> goals,Set<String> path,Set<String> done){if(done.contains(name))return;var goal=goals.get(name);if(goal==null)throw new IllegalArgumentException("Unknown prerequisite "+name);if(!path.add(name))throw new IllegalArgumentException("Progression objectives contain a dependency cycle.");for(var required:goal.requires)visit(required,goals,path,done);path.remove(name);done.add(name);}
    public enum Scope implements StringRepresentable {STATIC,SHARED,PLAYER,TEAM;public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public enum Trigger implements StringRepresentable {ADVANCEMENT,SIGNAL;public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public record Goal(String id,String title,Trigger trigger,Identifier source,List<String> requires,boolean any,List<Cell> targets,int credits){
        public Goal {requires=List.copyOf(requires);targets=List.copyOf(targets);}
        public static final Codec<Goal> CODEC=RecordCodecBuilder.create(b->b.group(
                Codec.STRING.fieldOf("id").forGetter(Goal::id),Codec.STRING.fieldOf("title").forGetter(Goal::title),
                StringRepresentable.fromEnum(Trigger::values).fieldOf("trigger").forGetter(Goal::trigger),Identifier.CODEC.fieldOf("source").forGetter(Goal::source),
                Codec.STRING.listOf(0,16).optionalFieldOf("requires",List.of()).forGetter(Goal::requires),Codec.BOOL.optionalFieldOf("any",false).forGetter(Goal::any),
                Cell.CODEC.listOf(0,64).optionalFieldOf("cells",List.of()).forGetter(Goal::targets),Codec.intRange(0,64).optionalFieldOf("credits",0).forGetter(Goal::credits)
        ).apply(b,Goal::new));
        public boolean prerequisites(Set<String> completed){return requires.isEmpty()||(any?requires.stream().anyMatch(completed::contains):completed.containsAll(requires));}
    }
}

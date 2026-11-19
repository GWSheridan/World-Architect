package com.gwsheridan.worldarchitect.rules;

import java.util.*;

/** Read-only analysis delegates eligibility to the versioned evaluator. */
public final class RuleInspection {
    private RuleInspection() {}
    public enum Overlay { ALL, BIOME, HEIGHT, REGION, DISTANCE }
    public record Trace(String id, RuleSet.Phase phase, int priority, List<String> requires, Map<String,Boolean> checks, boolean eligible) {
        public Trace { requires=List.copyOf(requires);checks=Collections.unmodifiableMap(new LinkedHashMap<>(checks)); }
    }
    public static boolean region(RuleSet.Condition condition,RuleSet.Context context){return condition.region().map(r->context.x()>=r.minX()&&context.x()<=r.maxX()&&context.z()>=r.minZ()&&context.z()<=r.maxZ()).orElse(true);}
    public static boolean distance(RuleSet.Condition condition,RuleSet.Context context){return condition.distance().map(d->{long x=(long)context.x()-d.centerX(),z=(long)context.z()-d.centerZ(),s=x*x+z*z;return s>=(long)d.min()*d.min()&&s<=(long)d.max()*d.max();}).orElse(true);}
    public static boolean overlay(Overlay overlay,RuleSet rules,int index,RuleSet.Context context){
        var when=rules.ordered().get(index).when();
        return switch(overlay){case ALL->(rules.matches(context)&1L<<index)!=0;case BIOME->when.biomes().isEmpty()||when.biomes().contains(context.biome());case HEIGHT->when.height(context.y());case REGION->region(when,context);case DISTANCE->distance(when,context);};
    }
    public static List<Trace> trace(RuleSet rules,RuleSet.Context context){
        rules.validate().getOrThrow(IllegalArgumentException::new);var result=new ArrayList<Trace>();var matched=new HashSet<String>();long bits=rules.matches(context);
        for(int i=0;i<rules.ordered().size();i++){
            var rule=rules.ordered().get(i);var c=rule.when();var checks=new LinkedHashMap<String,Boolean>();
            checks.put("dimension",c.dimensions().isEmpty()||c.dimensions().contains(context.dimension()));
            checks.put("biome",c.biomes().isEmpty()||c.biomes().contains(context.biome()));checks.put("height",c.height(context.y()));
            checks.put("region",region(c,context));checks.put("distance",distance(c,context));
            checks.put("exact seed",c.seed().map(s->s==context.seed()).orElse(true));checks.put("required mods",context.mods().containsAll(c.mods()));
            checks.put("seeded coordinate",Math.floorMod(RuleSet.hash(context.seed(),context.x(),context.z(),c.seedSalt()),c.seedModulus())==c.seedRemainder());
            checks.put("dependencies",matched.containsAll(rule.requires()));
            boolean eligible=(bits&1L<<i)!=0;if(eligible)matched.add(rule.id());
            result.add(new Trace(rule.id(),rule.action().type().phase(),rule.priority(),rule.requires(),checks,eligible));
        }
        return List.copyOf(result);
    }
    public static List<String> report(RuleSet rules,int minY,int maxY){
        var rows=new ArrayList<String>();
        rules.validate().error().ifPresent(error->rows.add("Validation: "+error.message()));
        long featureWork=0;int templates=0;
        for(var rule:rules.ordered()){
            if(rule.action().type()==RuleSet.Kind.ORE){var ore=rule.action().ore().orElseThrow();featureWork+=(long)ore.attempts()*ore.size();}
            if(rule.action().type()==RuleSet.Kind.DECORATION)featureWork+=rule.action().decoration().orElseThrow().attempts();
            if(rule.action().type()==RuleSet.Kind.TEMPLATE)templates++;
        }
        rows.add("Feature work: "+featureWork+" / "+RuleSet.MAX_FEATURE_WORK+" per chunk. Template candidates: "+templates+" / 8.");
        rows.add("Phases run material → feature → template → spawn. Higher priority precedes lower priority; ties use editor order.");
        RuleSet.Phase phase=null;int index=0;
        for(var rule:rules.ordered()){
            if(rule.action().type().phase()!=phase){phase=rule.action().type().phase();rows.add("Phase: "+phase.name().toLowerCase(Locale.ROOT));}
            rows.add((++index)+". "+rule.id()+" · "+rule.action().type().getSerializedName()+" · priority "+rule.priority()+" · requires "+(rule.requires().isEmpty()?"none":String.join(" → ",rule.requires())));
            if(rule.action().type()!=RuleSet.Kind.SPAWN){
                if(!rule.when().dimensions().isEmpty()&&!rule.when().dimensions().contains(rules.dimension()))rows.add("Warning: "+rule.id()+" excludes its generation dimension.");
                if(rule.when().maxY()<minY||rule.when().minY()>maxY)rows.add("Warning: "+rule.id()+" has no height inside this dimension.");
            }
            if(rule.action().type()==RuleSet.Kind.MATERIAL){
                for(var earlier:rules.ordered().subList(0,index-1)){
                    if(earlier.action().type()==RuleSet.Kind.MATERIAL&&earlier.when().equals(rule.when())&&rule.requires().containsAll(earlier.requires())
                            &&(earlier.action().target().isEmpty()||earlier.action().target().equals(rule.action().target()))){
                        rows.add("Warning: "+rule.id()+" is shadowed by "+earlier.id()+" for the same conditions and terrain targets.");break;
                    }
                }
            }
        }
        rows.add("Coverage describes condition eligibility. Placement also requires valid targets, support, collisions, markers, spacing and the action's own filters. The native End arena excludes custom generation actions.");
        rows.add("Warnings are conservative. No warning is a guarantee that a rule will place blocks.");
        return List.copyOf(rows);
    }
    public static List<String> explain(RuleSet rules,RuleSet.Context context){
        var rows=new ArrayList<String>();rows.add(context.dimension()+" · "+context.x()+", "+context.y()+", "+context.z()+" · biome "+context.biome());
        for(var trace:trace(rules,context)){
            rows.add(trace.id()+" · "+(trace.eligible()?"conditions eligible":"conditions rejected")+" · "+trace.phase().name().toLowerCase(Locale.ROOT)+" · priority "+trace.priority());
            rows.add(trace.checks().entrySet().stream().map(e->e.getKey()+": "+(e.getValue()?"pass":"fail")).collect(java.util.stream.Collectors.joining(" · ")));
        }
        rows.add("This inspection does not place blocks or create chunks. Actual placement additionally applies the action's filters and reserved-area rules.");return List.copyOf(rows);
    }
}

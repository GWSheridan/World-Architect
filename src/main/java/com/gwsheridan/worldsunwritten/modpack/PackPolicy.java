package com.gwsheridan.worldsunwritten.modpack;

import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/** New-world administration defaults and independent, explicit runtime edit scopes. */
public record PackPolicy(WorldAdministration.Settings defaults,List<String> editable) {
    public static final List<String> GROUPS=List.of("initial","respawn","portals","protection");
    public static final PackPolicy DEFAULT=new PackPolicy(WorldAdministration.Settings.DEFAULT,List.of());
    public static final Codec<PackPolicy> CODEC=RecordCodecBuilder.<PackPolicy>create(b->b.group(
            Codec.intRange(1,1).fieldOf("version").forGetter(v->1),
            WorldAdministration.Settings.CODEC.fieldOf("defaults").forGetter(PackPolicy::defaults),
            Codec.STRING.listOf(0,4).optionalFieldOf("editable",List.of()).forGetter(PackPolicy::editable)
    ).apply(b,(v,defaults,edits)->new PackPolicy(defaults,edits))).validate(p->GROUPS.containsAll(p.editable)&&p.editable.stream().distinct().count()==p.editable.size()&&p.defaults.environments().isEmpty()?DataResult.success(p):DataResult.error(()->"Invalid pack runtime edit scopes or administration defaults."));
    public PackPolicy {editable=List.copyOf(editable);}
    public void validateDimensions(net.minecraft.world.level.levelgen.WorldDimensions dimensions) throws java.io.IOException {
        for(var id:defaults.protection().containerBlocks())if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id))throw new java.io.IOException("Unknown protected container block "+id);
        var policies=new ArrayList<com.gwsheridan.worldsunwritten.spawn.SpawnPolicy>();defaults.initial().ifPresent(policies::add);defaults.respawn().ifPresent(policies::add);policies.addAll(defaults.portals().values());
        for(var policy:policies){
            var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LEVEL_STEM,policy.dimension());var stem=dimensions.dimensions().get(key);
            if(stem==null)throw new java.io.IOException("Unknown runtime spawn dimension "+policy.dimension());
            if(policy.kind()==com.gwsheridan.worldsunwritten.spawn.SpawnPolicy.Kind.MARKER)throw new java.io.IOException("A new-world policy cannot refer to a library structure that has not been placed. Use an island or generated marker.");
            if(policy.kind()==com.gwsheridan.worldsunwritten.spawn.SpawnPolicy.Kind.ISLAND||policy.kind()==com.gwsheridan.worldsunwritten.spawn.SpawnPolicy.Kind.TEAM){
                if(!(stem.generator() instanceof com.gwsheridan.worldsunwritten.worldgen.VoidGenerator sky)||sky.layout().isEmpty())throw new java.io.IOException("The runtime policy needs a Skyblock dimension.");
                if(policy.kind()==com.gwsheridan.worldsunwritten.spawn.SpawnPolicy.Kind.TEAM&&(!key.equals(net.minecraft.world.level.dimension.LevelStem.OVERWORLD)||sky.layout().orElseThrow().personalSlots()==0))throw new java.io.IOException("Team policies require personal Overworld island slots.");
                if(policy.kind()==com.gwsheridan.worldsunwritten.spawn.SpawnPolicy.Kind.TEAM&&sky instanceof com.gwsheridan.worldsunwritten.worldgen.SkyblockGenerator rich&&!rich.catalog().teams())throw new java.io.IOException("Enable team allocation in the island catalog before choosing a team spawn policy.");
            }
            var type=stem.type().value();if(policy.kind()==com.gwsheridan.worldsunwritten.spawn.SpawnPolicy.Kind.FIXED&&(policy.position().getY()<type.minY()||policy.position().getY()+1>=type.minY()+type.height()||Math.abs((long)policy.position().getX())>29999984||Math.abs((long)policy.position().getZ())>29999984))throw new java.io.IOException("The fixed spawn falls outside the dimension or world limits.");
        }
    }
    public boolean allows(BundleManifest.Lock lock,String group){return lock==BundleManifest.Lock.UNLOCKED||lock==BundleManifest.Lock.PARTIAL&&editable.contains(group);}
    private Object value(String group){return switch(group){case "initial"->defaults.initial();case "respawn"->defaults.respawn();case "portals"->defaults.portals();case "protection"->defaults.protection();default->throw new IllegalArgumentException("Unknown runtime policy group.");};}
    public boolean compatibleChild(BundleManifest.Lock parentLock,Optional<PackPolicy> child){
        if(child.isEmpty())return parentLock==BundleManifest.Lock.UNLOCKED;
        for(String group:GROUPS)if(!allows(parentLock,group)&&(child.orElseThrow().editable.contains(group)||!Objects.equals(value(group),child.orElseThrow().value(group))))return false;
        return true;
    }
}

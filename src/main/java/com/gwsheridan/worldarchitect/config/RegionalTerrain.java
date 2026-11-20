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
    public static final Codec<RegionalTerrain> CODEC = Zone.CODEC.listOf(1, MAX_REGIONS).xmap(RegionalTerrain::new, RegionalTerrain::zones).validate(RegionalTerrain::validate);
    private final List<Zone> zones, ordered;
    public RegionalTerrain(List<Zone> zones) {
        this.zones = List.copyOf(zones);
        this.ordered = zones.stream().sorted(Comparator.comparingInt(Zone::priority).reversed()).toList();
    }
    public List<Zone> zones() { return zones; }
    /** Higher priority wins; ties retain the earlier editor entry. */
    public List<Zone> ordered() { return ordered; }
    public DataResult<RegionalTerrain> validate() {
        if (zones.isEmpty() || zones.size() > MAX_REGIONS || zones.stream().map(Zone::name).distinct().count() != zones.size()) return DataResult.error(() -> "Use 1–32 regions with unique names.");
        for (var zone : zones) { var error = zone.validate().error(); if (error.isPresent()) return DataResult.error(() -> error.orElseThrow().message()); }
        return DataResult.success(this);
    }
    public Optional<Holder<Biome>> biomeAt(int x, int z) {
        return ordered.stream().filter(zone -> zone.biome().isPresent() && zone.weight(x,z) >= 0.5).map(zone -> zone.biome().orElseThrow()).findFirst();
    }
    @Override public boolean equals(Object o) { return o instanceof RegionalTerrain other && zones.equals(other.zones); }
    @Override public int hashCode() { return zones.hashCode(); }
    public enum Mask implements StringRepresentable {
        RECTANGLE, CIRCLE;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public record Zone(String name, Mask mask, int minX, int minZ, int maxX, int maxZ, int priority, int transition,
                       int elevation, int relief, AdvancedTerrain terrain, Optional<Holder<Biome>> biome) {
        private static final Codec<Integer> COORD = Codec.intRange(-WORLD_BOUND, WORLD_BOUND);
        public static final Codec<Zone> CODEC = RecordCodecBuilder.<Zone>create(b -> b.group(
                Codec.STRING.fieldOf("name").forGetter(Zone::name), StringRepresentable.fromEnum(Mask::values).fieldOf("mask").forGetter(Zone::mask),
                COORD.fieldOf("min_x").forGetter(Zone::minX), COORD.fieldOf("min_z").forGetter(Zone::minZ),
                COORD.fieldOf("max_x").forGetter(Zone::maxX), COORD.fieldOf("max_z").forGetter(Zone::maxZ),
                Codec.intRange(-1024,1024).optionalFieldOf("priority",0).forGetter(Zone::priority), Codec.intRange(0,4096).optionalFieldOf("transition",32).forGetter(Zone::transition),
                Codec.intRange(TerrainShape.MIN_ELEVATION,TerrainShape.MAX_ELEVATION).fieldOf("elevation").forGetter(Zone::elevation),
                Codec.intRange(0,TerrainShape.MAX_RELIEF).fieldOf("relief").forGetter(Zone::relief),
                AdvancedTerrain.CODEC.fieldOf("terrain").forGetter(Zone::terrain), Codec.lazyInitialized(() -> Biome.CODEC).optionalFieldOf("biome").forGetter(Zone::biome)
        ).apply(b,Zone::new)).validate(Zone::validate);
        public DataResult<Zone> validate() {
            long width=(long)maxX-minX, depth=(long)maxZ-minZ;
            if (!name.matches("[a-z0-9_-]{1,32}") || minX < -WORLD_BOUND || minZ < -WORLD_BOUND || maxX > WORLD_BOUND || maxZ > WORLD_BOUND
                    || width < 2 || depth < 2 || mask==Mask.CIRCLE && width!=depth || transition<0 || transition>4096 || transition*2L>Math.min(width,depth)
                    || priority < -1024 || priority > 1024 || elevation < TerrainShape.MIN_ELEVATION || elevation > TerrainShape.MAX_ELEVATION || relief<0 || relief>TerrainShape.MAX_RELIEF
                    || terrain.landform()==AdvancedTerrain.Landform.FLOATING_ISLANDS || terrain.scale()<AdvancedTerrain.MIN_SCALE || terrain.scale()>AdvancedTerrain.MAX_SCALE
                    || biome.filter(holder -> holder.unwrapKey().isEmpty()).isPresent())
                return DataResult.error(() -> "Region bounds, name, transition or terrain are invalid. Circles need square bounds; regional terrain needs solid landforms.");
            return DataResult.success(this);
        }
        public double edgeDistance(int x,int z) {
            if(mask==Mask.RECTANGLE)return Math.min(Math.min((double)x-minX,(double)maxX-x),Math.min((double)z-minZ,(double)maxZ-z));
            double dx=x-((long)minX+maxX)/2.0,dz=z-((long)minZ+maxZ)/2.0;
            return ((long)maxX-minX)/2.0-StrictMath.sqrt(dx*dx+dz*dz);
        }
        public double weight(int x,int z) {
            double edge=edgeDistance(x,z); if(edge<0)return 0; if(transition==0)return 1;
            double progress=Math.clamp(edge/transition,0.0,1.0);return progress*progress*(3-2*progress);
        }
    }
}

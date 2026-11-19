package com.gwsheridan.worldarchitect.rules;

import com.gwsheridan.worldarchitect.config.ContentProfile;
import com.gwsheridan.worldarchitect.config.TerrainMaterial;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

/** Version 1 decisions are pure functions of the candidate and the saved, ordered rules. */
public final class RuleSet {
    public static final int MAX_RULES = 32;
    public static final int MAX_FEATURE_WORK = 32768;
    public static final int MAX_TEMPLATE_CELLS = 65536;
    public static final Identifier UNBOUND = Identifier.fromNamespaceAndPath("worldarchitect", "unbound");
    public static final RuleSet EMPTY = new RuleSet(UNBOUND, List.of());
    public static final Codec<RuleSet> CODEC = RecordCodecBuilder.<RuleSet>create(b -> b.group(
            Codec.intRange(1, 1).fieldOf("version").forGetter(r -> 1),
            Identifier.CODEC.fieldOf("dimension").forGetter(RuleSet::dimension),
            Rule.CODEC.listOf(0, MAX_RULES).fieldOf("rules").forGetter(RuleSet::rules)
    ).apply(b, (version, dimension, rules) -> new RuleSet(dimension, rules))).validate(RuleSet::validate);

    private final Identifier dimension;
    private final List<Rule> rules;
    private final List<Rule> ordered;
    private final long[] dependencies;

    public RuleSet(Identifier dimension, List<Rule> rules) {
        this.dimension = rules.isEmpty() ? UNBOUND : dimension;
        this.rules = List.copyOf(rules);
        var sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparing((Rule rule) -> rule.action().type().phase()).thenComparing(Comparator.comparingInt(Rule::priority).reversed()));
        ordered = List.copyOf(sorted); // List.sort is stable: editor order breaks equal-priority ties.
        dependencies = new long[ordered.size()];
        var indices = new HashMap<String, Integer>();
        for (int index = 0; index < ordered.size(); index++) indices.put(ordered.get(index).id(), index);
        for (int index = 0; index < ordered.size(); index++) {
            for (String name : ordered.get(index).requires()) {
                Integer dependency = indices.get(name);
                if (dependency != null) dependencies[index] |= 1L << dependency;
            }
        }
    }

    public Identifier dimension() { return dimension; }
    public List<Rule> rules() { return rules; }
    public List<Rule> ordered() { return ordered; }
    public boolean isEmpty() { return rules.isEmpty(); }

    public DataResult<RuleSet> validate() {
        if (rules.size() > MAX_RULES || !rules.isEmpty() && dimension.equals(UNBOUND)) return DataResult.error(() -> "Bind non-empty rule sets to a dimension; at most 32 rules are allowed.");
        var seen = new HashSet<String>();
        int featureWork = 0;
        int templates = 0;
        var structures = new HashSet<Identifier>();
        for (Rule rule : ordered) {
            if (!rule.id().matches("[a-z0-9_-]{1,32}") || seen.contains(rule.id())) return DataResult.error(() -> "Rule IDs must be unique names of 1–32 lowercase letters, digits, underscores or hyphens.");
            if (!seen.containsAll(rule.requires())) return DataResult.error(() -> rule.id() + " depends on an unknown or later rule. Dependencies must precede it in phase, priority and editor order.");
            seen.add(rule.id());
            if (rule.action().type() == Kind.ORE) featureWork += rule.action().ore().orElseThrow().attempts() * rule.action().ore().orElseThrow().size();
            if (rule.action().type() == Kind.DECORATION) featureWork += rule.action().decoration().orElseThrow().attempts();
            if (rule.action().type() == Kind.TEMPLATE) {
                templates++;
                Identifier id = rule.action().template().orElseThrow().structure().unwrapKey().orElseThrow().identifier();
                if (!structures.add(id)) return DataResult.error(() -> "Select each template structure only once per rule set.");
            }
        }
        if (featureWork > MAX_FEATURE_WORK || templates > 8) return DataResult.error(() -> "Rules exceed the per-chunk budget: 32,768 feature steps and eight template candidates.");
        return DataResult.success(this);
    }

    public long matches(Context context) {
        long matched = 0;
        for (int i = 0; i < ordered.size(); i++) {
            if ((matched & dependencies[i]) == dependencies[i] && ordered.get(i).when().matches(context)) matched |= 1L << i;
        }
        return matched;
    }

    public boolean matches(Rule rule, Context context) {
        int index = ordered.indexOf(rule);
        return index >= 0 && (matches(context) & 1L << index) != 0;
    }

    public MaterialColumn materials(Context context) {
        int count = 0;
        long eligible = 0;
        while (count < ordered.size() && ordered.get(count).action().type() == Kind.MATERIAL) {
            if (ordered.get(count).when().matchesExceptHeight(context)) eligible |= 1L << count;
            count++;
        }
        return new MaterialColumn(count, eligible);
    }

    public final class MaterialColumn {
        private final int count;
        private final long eligible;
        private MaterialColumn(int count, long eligible) { this.count = count; this.eligible = eligible; }
        public BlockState at(int y, BlockState base) {
            if (eligible == 0) return base;
            long matched = 0;
            BlockState selected = null;
            for (int i = 0; i < count; i++) {
                var rule = ordered.get(i);
                if ((eligible & 1L << i) != 0 && rule.when().height(y) && (matched & dependencies[i]) == dependencies[i]) {
                    matched |= 1L << i;
                    if (selected == null && rule.action().target().filter(target -> target != base).isEmpty()) selected = rule.action().material().orElseThrow();
                }
            }
            return selected == null ? base : selected;
        }
    }

    public enum Phase { MATERIAL, FEATURE, TEMPLATE, SPAWN }
    public enum Kind implements StringRepresentable {
        MATERIAL(Phase.MATERIAL), ORE(Phase.FEATURE), DECORATION(Phase.FEATURE), TEMPLATE(Phase.TEMPLATE), SPAWN(Phase.SPAWN);
        private final Phase phase;
        Kind(Phase phase) { this.phase = phase; }
        public Phase phase() { return phase; }
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum SpawnEvent implements StringRepresentable {
        INITIAL, RESPAWN, PORTAL;
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public record Action(Kind type, Optional<BlockState> material, Optional<BlockState> target, Optional<ContentProfile.Ore> ore,
            Optional<ContentProfile.Decoration> decoration, Optional<ContentProfile.StructureRule> template,
            Optional<SpawnPolicy> spawn, SpawnEvent event) {
        public static final Codec<Action> CODEC = StringRepresentable.fromEnum(Kind::values).dispatch("type", Action::type, Action::payload).validate(a -> {
            if (a.spawn.filter(p -> p.kind() == SpawnPolicy.Kind.MARKER).isPresent()) return DataResult.error(() -> "Immutable spawn rules use fixed, generated, island or team policies; mutable library markers belong in world administration.");
            return DataResult.success(a);
        });
        private static MapCodec<Action> payload(Kind kind) {
            return switch (kind) {
                case MATERIAL -> MaterialCodec.VALUE;
                case ORE -> ContentProfile.Ore.CODEC.fieldOf("ore").xmap(value -> new Action(kind, Optional.empty(), Optional.empty(), Optional.of(value), Optional.empty(), Optional.empty(), Optional.empty(), SpawnEvent.INITIAL), a -> a.ore.orElseThrow());
                case DECORATION -> ContentProfile.Decoration.CODEC.fieldOf("decoration").xmap(value -> new Action(kind, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(value), Optional.empty(), Optional.empty(), SpawnEvent.INITIAL), a -> a.decoration.orElseThrow());
                case TEMPLATE -> ContentProfile.StructureRule.CODEC.fieldOf("template").xmap(value -> new Action(kind, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(value), Optional.empty(), SpawnEvent.INITIAL), a -> a.template.orElseThrow());
                case SPAWN -> SpawnCodec.VALUE;
            };
        }
        private static class MaterialCodec {
            static final MapCodec<Action> VALUE = RecordCodecBuilder.mapCodec(b -> b.group(
                    TerrainMaterial.CODEC.fieldOf("material").forGetter((Action a) -> a.material.orElseThrow()),
                    TerrainMaterial.CODEC.optionalFieldOf("target").forGetter(Action::target)
            ).apply(b, (material, target) -> new Action(Kind.MATERIAL, Optional.of(material), target, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), SpawnEvent.INITIAL)));
        }
        private static class SpawnCodec {
            static final MapCodec<Action> VALUE = RecordCodecBuilder.mapCodec(b -> b.group(
                    SpawnPolicy.CODEC.fieldOf("spawn").forGetter((Action a) -> a.spawn.orElseThrow()),
                    StringRepresentable.fromEnum(SpawnEvent::values).optionalFieldOf("event", SpawnEvent.INITIAL).forGetter(Action::event)
            ).apply(b, (spawn, event) -> new Action(Kind.SPAWN, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(spawn), event)));
        }
    }

    public record Rule(String id, int priority, List<String> requires, Condition when, Action action) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.STRING.fieldOf("id").forGetter(Rule::id),
                Codec.intRange(-1000, 1000).optionalFieldOf("priority", 0).forGetter(Rule::priority),
                Codec.STRING.listOf(0, MAX_RULES).optionalFieldOf("requires", List.of()).forGetter(Rule::requires),
                Condition.CODEC.optionalFieldOf("when", Condition.ALL).forGetter(Rule::when),
                Action.CODEC.fieldOf("action").forGetter(Rule::action)
        ).apply(b, Rule::new));
        public Rule { requires = List.copyOf(requires); }
    }

    public record Region(int minX, int maxX, int minZ, int maxZ) {
        private static final Codec<Integer> COORD = Codec.intRange(-29_999_983, 29_999_983);
        public static final Codec<Region> CODEC = RecordCodecBuilder.<Region>create(b -> b.group(
                COORD.fieldOf("min_x").forGetter(Region::minX), COORD.fieldOf("max_x").forGetter(Region::maxX),
                COORD.fieldOf("min_z").forGetter(Region::minZ), COORD.fieldOf("max_z").forGetter(Region::maxZ)
        ).apply(b, Region::new)).validate(r -> r.minX <= r.maxX && r.minZ <= r.maxZ ? DataResult.success(r) : DataResult.error(() -> "Region minimums must not exceed maximums."));
    }

    public record Distance(int centerX, int centerZ, int min, int max) {
        public static final Codec<Distance> CODEC = RecordCodecBuilder.<Distance>create(b -> b.group(
                Codec.intRange(-29_999_983, 29_999_983).optionalFieldOf("center_x", 0).forGetter(Distance::centerX),
                Codec.intRange(-29_999_983, 29_999_983).optionalFieldOf("center_z", 0).forGetter(Distance::centerZ),
                Codec.intRange(0, 84_852_800).optionalFieldOf("min", 0).forGetter(Distance::min),
                Codec.intRange(0, 84_852_800).fieldOf("max").forGetter(Distance::max)
        ).apply(b, Distance::new)).validate(d -> d.min <= d.max ? DataResult.success(d) : DataResult.error(() -> "Distance minimum exceeds maximum."));
    }

    public record Condition(List<Identifier> dimensions, List<Identifier> biomes, int minY, int maxY, Optional<Region> region,
            Optional<Distance> distance, Optional<Long> seed, int seedModulus, int seedRemainder, long seedSalt, List<String> mods) {
        public static final Condition ALL = new Condition(List.of(), List.of(), -2032, 2031, Optional.empty(), Optional.empty(), Optional.empty(), 1, 0, 0, List.of());
        public static final Codec<Condition> CODEC = RecordCodecBuilder.<Condition>create(b -> b.group(
                Identifier.CODEC.listOf(0, 16).optionalFieldOf("dimensions", List.of()).forGetter(Condition::dimensions),
                Identifier.CODEC.listOf(0, 16).optionalFieldOf("biomes", List.of()).forGetter(Condition::biomes),
                Codec.intRange(-2032, 2031).optionalFieldOf("min_y", -2032).forGetter(Condition::minY),
                Codec.intRange(-2032, 2031).optionalFieldOf("max_y", 2031).forGetter(Condition::maxY),
                Region.CODEC.optionalFieldOf("region").forGetter(Condition::region),
                Distance.CODEC.optionalFieldOf("distance").forGetter(Condition::distance),
                Codec.LONG.optionalFieldOf("seed").forGetter(Condition::seed),
                Codec.intRange(1, 1000000).optionalFieldOf("seed_modulus", 1).forGetter(Condition::seedModulus),
                Codec.intRange(0, 999999).optionalFieldOf("seed_remainder", 0).forGetter(Condition::seedRemainder),
                Codec.LONG.optionalFieldOf("seed_salt", 0L).forGetter(Condition::seedSalt),
                Codec.STRING.listOf(0, 16).optionalFieldOf("mods", List.of()).forGetter(Condition::mods)
        ).apply(b, Condition::new)).validate(c -> c.minY <= c.maxY && c.seedRemainder < c.seedModulus
                && c.mods.stream().allMatch(id -> id.matches("[a-z][a-z0-9_]{0,63}")) ? DataResult.success(c)
                : DataResult.error(() -> "Invalid height range, seed remainder or mod dependency ID."));
        public Condition { dimensions = List.copyOf(dimensions); biomes = List.copyOf(biomes); mods = List.copyOf(mods); }
        public boolean height(int y) { return y >= minY && y <= maxY; }
        public boolean spatial(Context c) {
            if ((!dimensions.isEmpty() && !dimensions.contains(c.dimension)) || (!biomes.isEmpty() && !biomes.contains(c.biome))) return false;
            if (region.filter(r -> c.x < r.minX || c.x > r.maxX || c.z < r.minZ || c.z > r.maxZ).isPresent()) return false;
            if (distance.isPresent()) {
                var d = distance.orElseThrow(); long dx = (long)c.x - d.centerX; long dz = (long)c.z - d.centerZ;
                long squared = dx * dx + dz * dz;
                if (squared < (long)d.min * d.min || squared > (long)d.max * d.max) return false;
            }
            return true;
        }
        public boolean matchesExceptHeight(Context c) {
            return spatial(c) && seed.filter(value -> value != c.seed).isEmpty() && c.mods.containsAll(mods)
                    && Math.floorMod(hash(c.seed, c.x, c.z, seedSalt), seedModulus) == seedRemainder;
        }
        public boolean matches(Context c) { return height(c.y) && matchesExceptHeight(c); }
    }

    public record Context(Identifier dimension, Identifier biome, int x, int y, int z, long seed, Set<String> mods) {}
    public static long hash(long seed, int x, int z, long salt) {
        long value = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xD1B54A32D192ED03L ^ salt * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
    @Override public boolean equals(Object other) { return other instanceof RuleSet set && dimension.equals(set.dimension) && rules.equals(set.rules); }
    @Override public int hashCode() { return Objects.hash(dimension, rules); }
}

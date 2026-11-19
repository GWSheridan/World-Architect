package com.gwsheridan.worldarchitect.rules;

import com.gwsheridan.worldarchitect.spawn.SpawnPolicy;
import com.gwsheridan.worldarchitect.worldgen.ManagedGenerator;
import com.gwsheridan.worldarchitect.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.gwsheridan.worldarchitect.structure.ModularStructure;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.QuartPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.LevelStem;

public final class RuleSpawns {
    private RuleSpawns() {}

    public static Optional<SpawnPolicy> select(ServerLevel owner, RuleSet.SpawnEvent event) {
        if (!(owner.getChunkSource().getGenerator() instanceof ManagedGenerator generator)) return Optional.empty();
        RuleSet rules = generator.dimensionProfile().map(profile -> profile.rules()).orElse(RuleSet.EMPTY);
        for (var rule : rules.ordered()) {
            if (rule.action().type() != RuleSet.Kind.SPAWN || rule.action().event() != event) continue;
            var policy = rule.action().spawn().orElseThrow();
            var target = owner.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, policy.dimension()));
            if (target == null) continue;
            var pos = policy.position();
            var source = target.getChunkSource();
            var biome = target.uncachedBiomeResolver().getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
            var context = new RuleSet.Context(policy.dimension(), biome.unwrapKey().orElseThrow().identifier(), pos.getX(), pos.getY(), pos.getZ(),
                    source.randomState().seed(), RuleDependencies.loaded());
            if (rules.matches(rule, context)) return Optional.of(policy);
        }
        return Optional.empty();
    }

    public static void validate(Map<ResourceKey<LevelStem>, LevelStem> dimensions, HolderLookup.Provider registries) {
        dimensions.forEach((key, stem) -> {
            if (!(stem.generator() instanceof ManagedGenerator managed)) return;
            var rules = managed.dimensionProfile().map(profile -> profile.rules()).orElse(RuleSet.EMPTY);
            for (var rule : rules.rules()) {
                if (rule.action().type() != RuleSet.Kind.SPAWN) continue;
                var policy = rule.action().spawn().orElseThrow();
                var target = dimensions.get(ResourceKey.create(Registries.LEVEL_STEM, policy.dimension()));
                if (target == null) throw new IllegalStateException("Spawn rule " + rule.id() + " targets a missing dimension " + policy.dimension());
                if (rule.action().event() == RuleSet.SpawnEvent.PORTAL && !policy.dimension().equals(key.identifier())) throw new IllegalStateException("Portal spawn rules must target their own dimension.");
                if (policy.kind() == SpawnPolicy.Kind.GENERATED) {
                    var structure = registries.lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, policy.template().orElseThrow())).orElseThrow().value();
                    if (!(structure instanceof ModularStructure modular) || !modular.hasMarker(policy.module(), policy.marker())) throw new IllegalStateException("Spawn rule " + rule.id() + " references an unknown modular marker.");
                    if (!(target.generator() instanceof SolidColumnGenerator solid) || solid.structureRules().stream().noneMatch(selected -> selected.structure().is(policy.template().orElseThrow()))) throw new IllegalStateException("The generated spawn structure must be enabled in its target dimension.");
                }
                if (policy.kind() == SpawnPolicy.Kind.ISLAND || policy.kind() == SpawnPolicy.Kind.TEAM) {
                    if (!(target.generator() instanceof VoidGenerator sky) || sky.island().isEmpty()) throw new IllegalStateException("Island spawn rules need a Skyblock target.");
                    if (policy.kind() == SpawnPolicy.Kind.TEAM && (!policy.dimension().equals(LevelStem.OVERWORLD.identifier()) || sky.island().orElseThrow().personalSlots() == 0)) throw new IllegalStateException("Team spawn rules need personal Overworld islands.");
                } else if (policy.position().getY() <= target.type().value().minY() || policy.position().getY() >= target.type().value().minY() + target.type().value().height() - 1) {
                    throw new IllegalStateException("Spawn rule reference position must fit inside its target dimension.");
                }
            }
        });
    }
}

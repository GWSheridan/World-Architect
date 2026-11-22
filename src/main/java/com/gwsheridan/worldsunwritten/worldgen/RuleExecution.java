package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.config.ContentProfile;
import com.gwsheridan.worldsunwritten.rules.RuleDependencies;
import com.gwsheridan.worldsunwritten.rules.RuleSet;
import com.gwsheridan.worldsunwritten.structure.ModularStructure;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

public final class RuleExecution {
    private RuleExecution() {}

    public static List<ContentProfile.StructureRule> structures(ContentProfile content, RuleSet set) {
        return Stream.concat(content.structures().stream(), set.ordered().stream().filter(rule -> rule.action().type() == RuleSet.Kind.TEMPLATE)
                .map(rule -> rule.action().template().orElseThrow())).toList();
    }

    static Optional<String> problem(SolidColumnGenerator generator) {
        var rules = generator.rules();
        if (rules.validate().error().isPresent()) return Optional.of(rules.validate().error().orElseThrow().message());
        int cells = 0;
        var ids = new java.util.HashSet<Identifier>();
        for (var rule : generator.structureRules()) {
            if (!ids.add(rule.structure().unwrapKey().orElseThrow().identifier())) return Optional.of("A structure is selected in both legacy content and the rule engine.");
        }
        for (var rule : rules.ordered()) {
            if (rule.action().type() == RuleSet.Kind.TEMPLATE) {
                var selected = rule.action().template().orElseThrow();
                if (!selected.structure().isBound() || !(selected.structure().value() instanceof ModularStructure modular)) return Optional.of("Rule-engine templates require a registered modular structure.");
                cells += modular.blockCount();
                if (modular.horizontalSpan() > (selected.separation() + 1) * 16) return Optional.of("Template separation must fit the entire modular assembly.");
            }
            if (rule.action().ore().filter(ore -> ore.minY() < generator.getMinY() || ore.maxY() > generator.getMinY() + generator.getGenDepth() - 1).isPresent()) return Optional.of("Rule ore bounds must fit inside the dimension.");
        }
        return cells > RuleSet.MAX_TEMPLATE_CELLS ? Optional.of("Rule templates exceed 65,536 combined template cells per candidate chunk.") : Optional.empty();
    }

    static void ores(SolidColumnGenerator generator, ChunkAccess chunk, long seed, int[] surfaces, ContentPlacement.BaseMaterial base) {
        for (var rule : generator.rules().ordered()) {
            if (rule.action().type() == RuleSet.Kind.ORE) ContentPlacement.placeOres(chunk, seed, List.of(rule.action().ore().orElseThrow()), surfaces, base,
                    rule.id().hashCode(), pos -> generator.rules().matches(rule, generator.ruleContext(seed, pos.getX(), pos.getY(), pos.getZ())));
        }
    }

    static void decorations(SolidColumnGenerator generator, WorldGenLevel level, ChunkAccess chunk) {
        for (var rule : generator.rules().ordered()) {
            if (rule.action().type() == RuleSet.Kind.DECORATION) ContentPlacement.decorate(level, chunk, generator, List.of(rule.action().decoration().orElseThrow()),
                    rule.id().hashCode(), pos -> generator.rules().matches(rule, generator.ruleContext(level.getSeed(), pos.getX(), pos.getY(), pos.getZ())));
        }
    }

    public static boolean allowsTemplate(SolidColumnGenerator generator, Holder<Structure> selected, long seed, ChunkPos chunk,
            LevelHeightAccessor height, RandomState random) {
        boolean gated = generator.rules().ordered().stream().anyMatch(rule -> rule.action().template().filter(template -> template.structure().equals(selected)).isPresent());
        if (!gated) return true;
        int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ();
        int y = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, random);
        var context = generator.ruleContext(seed, x, y, z);
        long matched = generator.rules().matches(context);
        var ordered = generator.rules().ordered();
        for (int i = 0; i < ordered.size(); i++) {
            var rule = ordered.get(i);
            if (rule.action().type() != RuleSet.Kind.TEMPLATE || (matched & 1L << i) == 0) continue;
            var template = rule.action().template().orElseThrow();
            int salt = template.structure().unwrapKey().orElseThrow().identifier().toString().hashCode() & Integer.MAX_VALUE;
            var placement = new RandomSpreadStructurePlacement(template.spacing(), template.separation(), RandomSpreadType.LINEAR, salt);
            if (placement.getPotentialStructureChunk(seed, chunk.x(), chunk.z()).equals(chunk)) return template.structure().equals(selected);
        }
        return false;
    }

    public static boolean templateCell(SolidColumnGenerator generator, Structure structure, long seed, BlockPos pos) {
        for (var rule : generator.rules().ordered()) {
            if (rule.action().template().filter(template -> template.structure().value() == structure).isPresent()) {
                var context = generator.ruleContext(seed, pos.getX(), pos.getY(), pos.getZ());
                return rule.when().height(pos.getY()) && rule.when().spatial(context);
            }
        }
        return true;
    }
}

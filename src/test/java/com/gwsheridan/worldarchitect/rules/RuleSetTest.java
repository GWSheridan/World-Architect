package com.gwsheridan.worldarchitect.rules;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class RuleSetTest {
    private static final Identifier WORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier PLAINS = Identifier.withDefaultNamespace("plains");
    private static RuleSet.Context context(int x, int y, int z) { return new RuleSet.Context(WORLD, PLAINS, x, y, z, 42, Set.of("minecraft")); }

    @Test void dimensionBiomeHeightRegionDistanceSeedAndModFiltersAllApply() {
        var condition = RuleSet.Condition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"dimensions":["minecraft:overworld"],"biomes":["minecraft:plains"],"min_y":70,"max_y":90,
                 "region":{"min_x":-16,"max_x":-1,"min_z":0,"max_z":16},
                 "distance":{"center_x":0,"center_z":0,"min":5,"max":16},"seed":42,"mods":["minecraft"]}
                """)).getOrThrow();
        assertTrue(condition.matches(context(-16, 70, 0)));
        assertTrue(condition.matches(context(-5, 90, 0)));
        for (var point : List.of(context(-17, 80, 0), context(-1, 80, 0), context(-5, 69, 0), context(-5, 91, 0), context(-5, 80, 17))) assertFalse(condition.matches(point));
        assertFalse(condition.matches(new RuleSet.Context(Identifier.withDefaultNamespace("the_nether"), PLAINS, -5, 80, 0, 42, Set.of("minecraft"))));
        assertFalse(condition.matches(new RuleSet.Context(WORLD, Identifier.withDefaultNamespace("forest"), -5, 80, 0, 42, Set.of("minecraft"))));
        assertFalse(condition.matches(new RuleSet.Context(WORLD, PLAINS, -5, 80, 0, 43, Set.of("minecraft"))));
        assertFalse(condition.matches(new RuleSet.Context(WORLD, PLAINS, -5, 80, 0, 42, Set.of())));
    }

    @Test void higherPriorityRunsFirstAndEditorOrderBreaksTies() {
        var set = set(rule("late", 0, ""), rule("first", 10, ""), rule("second", 10, "first"));
        assertEquals(List.of("first", "second", "late"), set.ordered().stream().map(RuleSet.Rule::id).toList());
        assertEquals(7, set.matches(context(0, 80, 0)));
        var reordered = set(rule("second", 10, ""), rule("first", 10, ""), rule("late", 0, ""));
        assertEquals(List.of("second", "first", "late"), reordered.ordered().stream().map(RuleSet.Rule::id).toList());
    }

    @Test void missingCyclicAndLaterDependenciesAreRejected() {
        assertFalse(parse(List.of(rule("a", 0, "missing"))).isSuccess());
        assertFalse(parse(List.of(rule("a", 0, "b"), rule("b", 0, "a"))).isSuccess());
        assertFalse(parse(List.of(rule("a", 10, "b"), rule("b", 0, ""))).isSuccess());
        assertFalse(parse(List.of(rule("a", 0, ""), rule("a", 0, ""))).isSuccess());
    }

    @Test void dependenciesRequireEarlierConditionsAtTheSameCandidate() {
        var first = rule("first", 10, ""); first.add("when", JsonParser.parseString("{\"region\":{\"min_x\":-16,\"max_x\":-1,\"min_z\":0,\"max_z\":16}}"));
        var set = set(first, rule("dependent", 0, "first"));
        assertEquals(3, set.matches(context(-1, 80, 0)));
        assertEquals(0, set.matches(context(0, 80, 0)));
    }

    @Test void seedPartitionsAreStableForNegativeCoordinates() {
        var condition = RuleSet.Condition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"seed_modulus\":7,\"seed_remainder\":3,\"seed_salt\":91}")).getOrThrow();
        int accepted = 0;
        for (int x = -100; x <= 100; x++) {
            var c = context(x, 80, -33);
            boolean expected = Math.floorMod(RuleSet.hash(42, x, -33, 91), 7) == 3;
            assertEquals(expected, condition.matches(c));
            if (expected) accepted++;
        }
        assertTrue(accepted > 10 && accepted < 60);
        assertNotEquals(RuleSet.hash(42, -17, -33, 91), RuleSet.hash(43, -17, -33, 91));
    }

    @Test void invalidBoundsAndOversizedRuleSetsAreRejected() {
        for (String json : List.of("{\"seed_modulus\":2,\"seed_remainder\":2}", "{\"min_y\":90,\"max_y\":80}", "{\"region\":{\"min_x\":5,\"max_x\":0,\"min_z\":0,\"max_z\":1}}", "{\"mods\":[\"BadMod\"]}", "{\"distance\":{\"min\":10,\"max\":5}}")) assertFalse(RuleSet.Condition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isSuccess());
        var list = new java.util.ArrayList<JsonObject>(); for (int i = 0; i < 33; i++) list.add(rule("rule_" + i, 0, ""));
        assertFalse(parse(list).isSuccess());
    }

    @Test void ruleSetsRoundTripAndEmptySetsHaveCanonicalBindings() {
        var rules = set(rule("spawn", 0, ""));
        assertEquals(rules, RuleSet.CODEC.parse(JsonOps.INSTANCE, RuleSet.CODEC.encodeStart(JsonOps.INSTANCE, rules).getOrThrow()).getOrThrow());
        assertEquals(RuleSet.EMPTY, new RuleSet(WORLD, List.of()));
    }

    private static JsonObject rule(String id, int priority, String requires) {
        var rule = JsonParser.parseString("{\"id\":\"" + id + "\",\"priority\":" + priority + ",\"action\":{\"type\":\"spawn\",\"spawn\":{\"kind\":\"fixed\",\"dimension\":\"minecraft:overworld\",\"position\":[0,80,0]}}}").getAsJsonObject();
        if (!requires.isEmpty()) { var dependencies = new JsonArray(); dependencies.add(requires); rule.add("requires", dependencies); }
        return rule;
    }
    private static RuleSet set(JsonObject... rules) { return parse(List.of(rules)).getOrThrow(); }
    private static com.mojang.serialization.DataResult<RuleSet> parse(List<JsonObject> rules) {
        var json = new JsonObject(); json.addProperty("version", 1); json.addProperty("dimension", "minecraft:overworld");
        var list = new JsonArray(); rules.forEach(list::add); json.add("rules", list);
        return RuleSet.CODEC.parse(JsonOps.INSTANCE, json);
    }
}

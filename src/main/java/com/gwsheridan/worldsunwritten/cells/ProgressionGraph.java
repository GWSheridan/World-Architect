package com.gwsheridan.worldsunwritten.cells;

import java.util.*;

/** Read-only graph evaluation for authoring and player explanations. */
public final class ProgressionGraph {
    private ProgressionGraph() {}

    public static List<List<GrowthRules.Goal>> layers(GrowthRules rules) {
        rules.validate().getOrThrow();
        var done = new HashSet<String>();
        var result = new ArrayList<List<GrowthRules.Goal>>();
        while (done.size() < rules.goals().size()) {
            var row =
                    rules.goals().stream()
                            .filter(g -> !done.contains(g.id()) && done.containsAll(g.requires()))
                            .toList();
            if (row.isEmpty()) throw new IllegalArgumentException("Unreachable objective graph.");
            result.add(row);
            row.forEach(g -> done.add(g.id()));
        }
        return List.copyOf(result);
    }

    public static Set<String> simulate(GrowthRules rules, Set<String> observed) {
        var done = new TreeSet<String>();
        boolean changed;
        do {
            changed = false;
            for (var g : rules.goals())
                if (observed.contains(g.id()) && !done.contains(g.id()) && g.prerequisites(done)) {
                    done.add(g.id());
                    changed = true;
                }
        } while (changed);
        return Collections.unmodifiableSet(done);
    }

    public static String reason(GrowthRules.Goal goal, Set<String> done) {
        if (done.contains(goal.id())) return "Completed";
        if (!goal.prerequisites(done))
            return "Requires "
                    + (goal.any() ? "any of: " : "all of: ")
                    + String.join(", ", goal.requires());
        return "Ready: " + goal.trigger().getSerializedName() + " " + goal.source();
    }
}

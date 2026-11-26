package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class CampaignRehearsal {
    private CampaignRehearsal() {}

    public static List<String> checkpoint(GrowthRules rules, String input) {
        var ids = input.isBlank() ? List.<String>of()
                : java.util.Arrays.stream(input.strip().split("\\s+")).distinct().sorted().toList();
        var unknown = new TreeSet<>(ids);
        rules.goals().forEach(goal -> unknown.remove(goal.id()));
        if (!unknown.isEmpty()) throw new IllegalArgumentException("Unknown objectives: " + String.join(", ", unknown));
        return ids;
    }

    public static List<String> report(CellWorldProfile profile, long seed, int territory,
            Set<Cell> reserved, List<String> checkpoint) {
        var rules = profile.growth();
        var campaign = profile.campaign().orElse(CellCampaign.EMPTY);
        var triggers = rules.goals().stream().filter(goal -> checkpoint.contains(goal.id()))
                .map(goal -> goal.trigger().name() + ":" + goal.source()).collect(java.util.stream.Collectors.toSet());
        var observed = new HashSet<String>();
        for (var goal : rules.goals())
            if (triggers.contains(goal.trigger().name() + ":" + goal.source())) observed.add(goal.id());
        var done = ProgressionGraph.simulate(rules, observed);
        var unlocked = new TreeSet<>(reserved);
        unlocked.add(Cell.ORIGIN);
        int credits = 0;
        for (var goal : rules.goals()) {
            if (!done.contains(goal.id())) continue;
            unlocked.addAll(goal.targets());
            credits += goal.credits();
        }
        var rows = new ArrayList<String>();
        rows.add(done.size() + " of " + rules.goals().size() + " objectives completed at this checkpoint");
        rows.add(credits + " expansion credits awarded · " + unlocked.size() + " cells open or queued");
        for (var goal : rules.goals()) rows.add(goal.title() + " · " + ProgressionGraph.reason(goal, done));

        var frontier = new TreeSet<Cell>();
        for (var tile : unlocked)
            for (var cell : profile.layout().neighbours(tile))
                if (profile.layout().distance(cell) <= rules.radius() && !unlocked.contains(cell)) frontier.add(cell);
        int cheapest = frontier.stream().mapToInt(cell -> campaign.cost(profile, seed, territory, cell)).min().orElse(0);
        if (frontier.isEmpty()) rows.add("There are no remaining adjacent frontier cells.");
        else if (credits < cheapest) rows.add("Further objectives are needed to afford a frontier cell. Minimum cost " + cheapest + ".");
        else rows.add("At least one frontier cell is affordable. Minimum cost " + cheapest + ".");
        frontier.stream().limit(16).forEach(cell ->
                rows.add(cell.key() + " · " + campaign.cost(profile, seed, territory, cell) + " credits"));

        var reachable = new HashSet<Cell>();
        var pending = new ArrayDeque<Cell>();
        reachable.add(Cell.ORIGIN);
        pending.add(Cell.ORIGIN);
        while (!pending.isEmpty())
            for (var cell : profile.layout().neighbours(pending.removeFirst()))
                if (unlocked.contains(cell) && reachable.add(cell)) pending.addLast(cell);
        if (reachable.size() < unlocked.size())
            rows.add((unlocked.size() - reachable.size()) + " awarded cells have no adjacent route from the start.");
        return List.copyOf(rows);
    }
}

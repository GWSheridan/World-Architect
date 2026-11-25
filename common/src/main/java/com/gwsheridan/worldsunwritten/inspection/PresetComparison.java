package com.gwsheridan.worldsunwritten.inspection;

import com.google.gson.JsonElement;
import java.util.*;

/** Stable semantic paths, with explicit work and output bounds. Values never enter a shared report. */
public final class PresetComparison {
    public record Result(List<String> changed, boolean truncated) { public Result { changed = List.copyOf(changed); } }
    private PresetComparison() {}
    public static Result compare(JsonElement saved, JsonElement proposed) {
        var work = new Work(); work.visit("dimensions", saved, proposed, 0);
        return new Result(work.paths, work.truncated);
    }
    private static final class Work {
        final List<String> paths = new ArrayList<>(); int visited; boolean truncated;
        void visit(String path, JsonElement a, JsonElement b, int depth) {
            if (++visited > 65_536 || paths.size() >= 256 || depth > 48 || path.length() > 500) { truncated = true; return; }
            if (a == null || b == null) { if (a != b) paths.add(path + (a == null ? " · added" : " · removed")); return; }
            if (a.isJsonObject() && b.isJsonObject()) {
                var keys = new TreeSet<>(a.getAsJsonObject().keySet()); keys.addAll(b.getAsJsonObject().keySet());
                for (String key : keys) { visit(path + "/" + key, a.getAsJsonObject().get(key), b.getAsJsonObject().get(key), depth + 1); if (truncated) break; }
            } else if (a.isJsonArray() && b.isJsonArray()) {
                var left = a.getAsJsonArray(); var right = b.getAsJsonArray();
                for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
                    visit(path + "[" + i + "]", i < left.size() ? left.get(i) : null, i < right.size() ? right.get(i) : null, depth + 1); if (truncated) break;
                }
            } else if (!a.equals(b)) paths.add(path + " · changed");
        }
    }
}

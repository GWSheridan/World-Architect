package com.gwsheridan.worldsunwritten.rules;

import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import net.neoforged.fml.ModList;

public final class RuleDependencies {
    private RuleDependencies() {}
    private static class Loaded {
        static final Map<String, String> VERSIONS = capture();
        private static Map<String, String> capture() {
            var result = new TreeMap<String, String>();
            ModList.get().getMods().forEach(mod -> result.put(mod.getModId(), mod.getVersion().toString()));
            return Map.copyOf(result);
        }
    }
    public static Set<String> loaded() { return Loaded.VERSIONS.keySet(); }
    public static String version(String id) { return Loaded.VERSIONS.getOrDefault(id, "absent"); }
}

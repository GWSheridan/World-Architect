package com.gwsheridan.worldarchitect.modpack;
import com.google.gson.JsonObject;import java.util.*;import net.minecraft.core.HolderLookup;import net.minecraft.world.level.levelgen.presets.WorldPreset;
public final class PresetStudio {
 private PresetStudio() {}
public record Draft(BundleManifest manifest, JsonObject preset, Map<String, byte[]> templates) {
        public Draft {
            preset = preset.deepCopy();
            var copy = new TreeMap<String, byte[]>(); templates.forEach((key, value) -> copy.put(key, value.clone()));
            templates = java.util.Collections.unmodifiableSortedMap(copy);
        }
        @Override public JsonObject preset() { return preset.deepCopy(); }
        @Override public Map<String, byte[]> templates() {
            var copy = new TreeMap<String, byte[]>(); templates.forEach((key, value) -> copy.put(key, value.clone())); return copy;
        }
        public Draft withManifest(BundleManifest next) {
            if(manifest.inheritance().isPresent()&&next.inheritance().isEmpty())next=next.withInheritance(manifest.inheritance());
            if(manifest.pack().isPresent()&&next.pack().isEmpty())next=next.withPack(manifest.pack());
            return new Draft(next,preset,templates);
        }
    }
public record Checked(Draft draft, WorldPreset preset, HolderLookup.Provider registries, List<String> report, boolean canApply) {
        public Checked { report = List.copyOf(report); }
    }
}

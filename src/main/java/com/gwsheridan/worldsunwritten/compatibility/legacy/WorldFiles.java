package com.gwsheridan.worldsunwritten.compatibility.legacy;

import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolve each world's immutable naming family independently. Never migrate protected bytes. */
public final class WorldFiles {
    private WorldFiles() {}
    public static String namespace(Path data) {
        boolean old = present(data, LegacyIds.NAMESPACE), current = present(data, LegacyIds.CURRENT);
        if (old && current) throw SaveProtection.incompatible("Conflicting legacy and current world data. Restore a consistent backup; neither family was changed.");
        return old ? LegacyIds.NAMESPACE : LegacyIds.CURRENT;
    }
    private static boolean present(Path data, String name) {
        return Files.exists(data.resolve(name)) || Files.exists(data.resolve(name + "-cells"))
                || Files.exists(data.resolve(name + "-generation-v1.dat"))
                || Files.exists(data.resolve(name + "-islands-v1.dat"))
                || Files.exists(data.resolve(name + "-administration-v1.dat"));
    }
    public static Path file(Path data, String suffix) { return data.resolve(namespace(data) + suffix); }
    public static Path directory(Path data) { return data.resolve(namespace(data)); }
    public static String marker(Path data, String suffix) { return namespace(data) + suffix; }
}

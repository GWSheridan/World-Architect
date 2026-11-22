package com.gwsheridan.worldsunwritten.compatibility.legacy;

import java.io.IOException;
import net.minecraft.nbt.CompoundTag;

/** Keep embedded template metadata in its original dialect, including safe spawn markers. */
public final class LegacyMetadata {
    private LegacyMetadata() {}
    public static String header(CompoundTag root) throws IOException {
        if (root.contains(LegacyIds.CURRENT) && root.contains(LegacyIds.NAMESPACE)) throw new IOException("Conflicting template metadata headers.");
        String name = root.contains(LegacyIds.NAMESPACE) ? LegacyIds.NAMESPACE : LegacyIds.CURRENT;
        if (root.contains(name) && root.getCompound(name).isEmpty()) throw new IOException("Malformed template metadata.");
        return name;
    }
    public static String writing() { return LegacyCodecs.constructing() ? LegacyIds.NAMESPACE : LegacyIds.CURRENT; }
}

package com.gwsheridan.worldsunwritten.compatibility.legacy;

import net.minecraft.resources.Identifier;

/** Frozen identifiers are a decoding contract, not the current product identity. */
public final class LegacyIds {
    public static final String NAMESPACE = "worldarchitect";
    public static final String CURRENT = "worldsunwritten";
    private LegacyIds() {}
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(NAMESPACE, path); }
    public static boolean owned(String id) { return id.startsWith(CURRENT + ":") || id.startsWith(NAMESPACE + ":"); }
    public static boolean modular(String id) { return id.equals(CURRENT + ":modular") || id.equals(NAMESPACE + ":modular"); }
    public static boolean bundle(String id) { return id.startsWith(CURRENT + "_bundle/") || id.startsWith(NAMESPACE + "_bundle/"); }
}

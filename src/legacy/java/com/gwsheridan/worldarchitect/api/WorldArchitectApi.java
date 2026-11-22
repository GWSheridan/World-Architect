package com.gwsheridan.worldarchitect.api;

/** Entry point for the documented Java extension contract. */
public final class WorldArchitectApi {
    public static final int VERSION = 1;
    private WorldArchitectApi() {}
    public static void requireVersion(int required) { com.gwsheridan.worldsunwritten.api.WorldsUnwrittenApi.requireVersion(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(required)); }
}

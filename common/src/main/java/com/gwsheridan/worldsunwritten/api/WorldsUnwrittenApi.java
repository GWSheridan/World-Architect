package com.gwsheridan.worldsunwritten.api;

/** Entry point for the documented Java extension contract. */
public final class WorldsUnwrittenApi {
    public static final int VERSION = 1;
    private WorldsUnwrittenApi() {}
    public static void requireVersion(int required) {
        if (required != VERSION) throw new IllegalStateException("Worlds Unwritten API " + required + " is required; installed API is " + VERSION);
    }
}

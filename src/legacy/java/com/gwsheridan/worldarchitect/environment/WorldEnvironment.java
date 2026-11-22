package com.gwsheridan.worldarchitect.environment;

public final class WorldEnvironment {
 private WorldEnvironment() {}
public record Capabilities(boolean time, boolean weather, boolean sky, boolean clouds, boolean portals) {
        public String description() {
            return "Time: " + available(time, "dimension has no default clock or fixes its own time")
                    + "\nWeather: " + available(weather, "dimension has no surface weather")
                    + "\nMob spawning: native spawn checks and spawners supported"
                    + "\nPortal entry: " + available(portals, "custom portals need an integration calling WorldEnvironment.canEnterPortal")
                    + "\nFog: native air fog supported; water, lava and status effects retain their own fog"
                    + "\nSky colour: " + available(sky, "dimension does not use the Overworld skybox")
                    + "\nClouds: " + available(clouds, "dimension does not use the Overworld skybox")
                    + "\nVisuals use native environment attributes; custom renderers can replace them. Client cloud preference still applies.";
        }
        private static String available(boolean value, String reason) { return value ? "supported" : "unsupported (" + reason + ")"; }
    }
}

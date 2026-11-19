package com.gwsheridan.worldarchitect.environment;

import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;

/** A read-only view of a dimension's default clock; never changes the shared clock manager. */
public final class EnvironmentClocks {
    private EnvironmentClocks() {}

    public static ClockManager wrap(Level level, ClockManager original) {
        return new ClockManager() {
            @Override public ClockInstance getInstance(Holder<WorldClock> clock) {
                ClockInstance instance = original.getInstance(clock);
                if (!level.dimensionType().defaultClock().filter(clock::equals).isPresent()) return instance;
                return new ClockInstance() {
                    private EnvironmentProfile.Time time() { return WorldEnvironment.profile(level).time(); }
                    public long totalTicks() { return time().apply(instance.totalTicks()); }
                    public float partialTick() { return time().mode() == EnvironmentProfile.TimeMode.FIXED ? 0 : instance.partialTick(); }
                    public float rate() { return time().mode() == EnvironmentProfile.TimeMode.FIXED ? 0 : instance.rate(); }
                    public boolean isPaused() { return time().mode() == EnvironmentProfile.TimeMode.FIXED || instance.isPaused(); }
                };
            }
        };
    }
}

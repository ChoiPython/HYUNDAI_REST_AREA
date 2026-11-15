package io.shimjari.rest.core;

public final class ParkState {
    private ParkState() {}
    public static boolean canControl(boolean gearAvailable, boolean speedAvailable, boolean gearPark, float speedMetersPerSecond) {
        return gearAvailable && speedAvailable && gearPark && Float.isFinite(speedMetersPerSecond)
            && Math.abs(speedMetersPerSecond) < 0.01f;
    }
}

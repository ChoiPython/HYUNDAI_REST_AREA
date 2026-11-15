package io.shimjari.rest.core;

public final class RestSession {
    public enum Phase { PREPARING, RESTING, AWAKE, FINISHING, REVIEW }
    public final String id;
    public final RestProfile profile;
    public final boolean demo;
    public Phase phase = Phase.PREPARING;
    public long startWall, startElapsed, endWall, endElapsed;
    public int bootCount;
    public Float originalTemperature;
    public Integer originalFan;
    public boolean temperatureTouched, fanTouched;
    public String report = "";
    public long finishedWall;

    public RestSession(String id, RestProfile profile, boolean demo) {
        this.id = id; this.profile = profile.copy(); this.demo = demo;
    }
    public void start(long wall, long elapsed, int boot) {
        startWall = wall; startElapsed = elapsed; bootCount = boot;
        long duration = profile.minutes * 60_000L;
        endWall = wall + duration; endElapsed = elapsed + duration;
        phase = Phase.RESTING;
    }
    public long remaining(long wall, long elapsed, int boot) {
        if (phase != Phase.RESTING) return 0;
        long result = boot == bootCount && elapsed >= startElapsed ? endElapsed - elapsed : endWall - wall;
        return Math.max(0, Math.min(180 * 60_000L, result));
    }
    public void extendFiveMinutes() {
        if (phase != Phase.RESTING) return;
        long maximum = startElapsed + 180 * 60_000L;
        long delta = Math.min(5 * 60_000L, Math.max(0, maximum - endElapsed));
        endElapsed += delta; endWall += delta;
    }
    public void appendReport(String line) { report += (report.isEmpty() ? "" : "\n") + line; }
}

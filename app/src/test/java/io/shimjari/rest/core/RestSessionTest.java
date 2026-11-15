package io.shimjari.rest.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class RestSessionTest {
    private RestSession session(int minutes) {
        RestSession s = new RestSession("test", new RestProfile("p", "test", SeatId.DRIVER, 22, 1, minutes, 0), true);
        s.start(1000000, 10000, 4); return s;
    }
    @Test public void clockChangesDoNotChangeAnActiveTimerOnSameBoot() {
        RestSession s = session(25);
        assertEquals(1490000L, s.remaining(99000000, 20000, 4));
        assertEquals(1490000L, s.remaining(1, 20000, 4));
    }
    @Test public void rebootUsesPersistedWallDeadline() {
        RestSession s = session(25);
        assertEquals(1000000L, s.remaining(1500000, 5000, 5));
    }
    @Test public void expiredTimerNeverBecomesNegative() { assertEquals(0L, session(1).remaining(1200000, 300000, 4)); }
    @Test public void extensionCannotExceedThreeHours() {
        RestSession s = session(179); s.extendFiveMinutes(); s.extendFiveMinutes();
        assertEquals(180 * 60000L, s.endElapsed - s.startElapsed);
        assertEquals(180 * 60000L, s.endWall - s.startWall);
    }
    @Test public void editingAProfileDoesNotChangeAnActiveSession() {
        RestProfile p = new RestProfile("p", "old", SeatId.DRIVER, 22, 1, 25, 0);
        RestSession s = new RestSession("s", p, true); p.temperature = 25; p.name = "new";
        assertEquals(22f, s.profile.temperature, 0.001f); assertEquals("old", s.profile.name);
    }
    @Test(expected=IllegalArgumentException.class) public void invalidDurationIsRejected() { new RestProfile("p", "x", SeatId.DRIVER, 22, 1, 0, 0); }
}

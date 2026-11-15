package io.shimjari.rest.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class RestModeTest {
    @Test public void movingPassengerCanStartWithoutAnyParkReading() {
        assertTrue(RestMode.PASSENGER_RIDE.canStart(true,false,false));
        assertTrue(RestMode.PASSENGER_RIDE.canStart(false,false,false));
    }
    @Test public void driverCannotOverrideKnownMovingVehicleWithConfirmation() {
        assertFalse(RestMode.DRIVER_PARKED.canStart(true,false,true));
        assertTrue(RestMode.DRIVER_PARKED.canStart(true,true,false));
    }
    @Test public void unknownDriverStateRequiresExplicitConfirmation() {
        assertFalse(RestMode.DRIVER_PARKED.canStart(false,false,false));
        assertTrue(RestMode.DRIVER_PARKED.canStart(false,false,true));
    }
    @Test public void passengerNeverSelectsDriverSeat() {
        assertFalse(RestMode.PASSENGER_RIDE.accepts(SeatId.DRIVER));
        assertTrue(RestMode.PASSENGER_RIDE.accepts(SeatId.PASSENGER));
        assertTrue(RestMode.PASSENGER_RIDE.accepts(SeatId.REAR_LEFT));
        assertTrue(RestMode.PASSENGER_RIDE.accepts(SeatId.REAR_RIGHT));
    }
    @Test public void modeSwitchCorrectsSeatAndSnapshotKeepsItsMode() {
        RestProfile p=new RestProfile("p","Guest",SeatId.DRIVER,22,1,25,0);
        p.mode(RestMode.PASSENGER_RIDE);
        RestSession s=new RestSession("s",p,true);
        p.mode(RestMode.DRIVER_PARKED);
        assertEquals(SeatId.PASSENGER,s.profile.seat);
        assertEquals(RestMode.PASSENGER_RIDE,s.profile.mode);
        assertEquals(SeatId.DRIVER,p.seat);
    }
    @Test public void legacyProfilesKeepTheirSeatRole() {
        assertEquals(RestMode.DRIVER_PARKED,RestMode.restore("",SeatId.DRIVER));
        assertEquals(RestMode.PASSENGER_RIDE,RestMode.restore("missing",SeatId.REAR_LEFT));
    }
    @Test public void onlyPassengerAudioContinuesOffScreen() {
        assertTrue(RestMode.PASSENGER_RIDE.backgroundAudio());
        assertFalse(RestMode.DRIVER_PARKED.backgroundAudio());
    }
}

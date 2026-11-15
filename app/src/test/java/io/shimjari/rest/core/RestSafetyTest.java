package io.shimjari.rest.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class RestSafetyTest {
    @Test public void unknownOrMovingStateNeverPermitsControl() {
        assertFalse(ParkState.canControl(false, true, true, 0));
        assertFalse(ParkState.canControl(true, false, true, 0));
        assertFalse(ParkState.canControl(true, true, false, 0));
        assertFalse(ParkState.canControl(true, true, true, 2));
        assertFalse(ParkState.canControl(true, true, true, Float.NaN));
        assertTrue(ParkState.canControl(true, true, true, 0));
    }
    @Test public void readableButNonWritablePropertyCannotBeApplied() {
        VehicleSnapshot s = usable(); s.temperatureWritable = false;
        assertFalse(s.acceptsTemperature(22)); assertTrue(s.acceptsFan(2));
    }
    @Test public void missingOriginalValueCannotBeOverwritten() {
        VehicleSnapshot s = usable(); s.temperature = null; s.fan = null;
        assertFalse(s.acceptsTemperature(22)); assertFalse(s.acceptsFan(2));
    }
    @Test public void invalidSensorValuesAndBoundsDoNotPermitWrites() {
        VehicleSnapshot s = usable(); s.temperature = Float.NaN;
        assertFalse(s.acceptsTemperature(22));
        s = usable(); s.minTemperature = Float.NEGATIVE_INFINITY;
        assertFalse(s.acceptsTemperature(22));
        s = usable(); s.minFan = 5; s.maxFan = 2;
        assertFalse(s.acceptsFan(2));
    }
    @Test public void actualVehicleBoundsOverrideProfileUiRange() {
        VehicleSnapshot s = usable(); s.minTemperature = 20f; s.maxTemperature = 25f; s.maxFan = 4;
        assertFalse(s.acceptsTemperature(19)); assertFalse(s.acceptsTemperature(Float.NaN));
        assertTrue(s.acceptsTemperature(22)); assertFalse(s.acceptsFan(5));
    }
    @Test public void userChangesAreNotClobberedDuringRestore() {
        assertFalse(VehicleSnapshot.shouldRestoreTemperature(24f, 22f));
        assertFalse(VehicleSnapshot.shouldRestoreTemperature(null, 22f));
        assertTrue(VehicleSnapshot.shouldRestoreTemperature(22f, 22f));
        assertFalse(VehicleSnapshot.shouldRestoreFan(4, 1));
        assertTrue(VehicleSnapshot.shouldRestoreFan(1, 1));
    }
    private VehicleSnapshot usable() {
        VehicleSnapshot s = new VehicleSnapshot(); s.temperature = 23f; s.fan = 2;
        s.minTemperature = 16f; s.maxTemperature = 30f; s.minFan = 1; s.maxFan = 8;
        s.temperatureWritable = true; s.fanWritable = true; return s;
    }
}

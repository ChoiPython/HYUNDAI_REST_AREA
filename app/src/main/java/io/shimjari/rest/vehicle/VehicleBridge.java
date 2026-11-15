package io.shimjari.rest.vehicle;

import io.shimjari.rest.core.SeatId;
import io.shimjari.rest.core.VehicleSnapshot;

public interface VehicleBridge {
    interface Result<T> { void ok(T value); void fail(String reason); }
    boolean isDemo();
    void inspect(SeatId seat, Result<VehicleSnapshot> result);
    void parked(Result<Boolean> result);
    void setTemperature(SeatId seat, float value, Result<Float> result);
    void setFan(SeatId seat, int value, Result<Integer> result);
    void close();
}

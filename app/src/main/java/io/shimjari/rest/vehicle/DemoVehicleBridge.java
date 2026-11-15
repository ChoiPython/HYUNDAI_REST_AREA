package io.shimjari.rest.vehicle;

import android.os.Handler;
import android.os.Looper;
import io.shimjari.rest.core.SeatId;
import io.shimjari.rest.core.VehicleSnapshot;
import java.util.EnumMap;

/** An explicit simulator: never reads or controls a real vehicle. */
public final class DemoVehicleBridge implements VehicleBridge {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final EnumMap<SeatId, Float> temperatures = new EnumMap<>(SeatId.class);
    private final EnumMap<SeatId, Integer> fans = new EnumMap<>(SeatId.class);
    public boolean simulatedParked = true;
    public DemoVehicleBridge() {
        for (SeatId seat : SeatId.values()) { temperatures.put(seat, 23f); fans.put(seat, 2); }
    }
    @Override public boolean isDemo() { return true; }
    @Override public void parked(Result<Boolean> result) { main.post(() -> result.ok(simulatedParked)); }
    @Override public void inspect(SeatId seat, Result<VehicleSnapshot> result) {
        VehicleSnapshot value = new VehicleSnapshot();
        value.temperature = temperatures.get(seat); value.fan = fans.get(seat);
        value.minTemperature = 16f; value.maxTemperature = 30f; value.minFan = 1; value.maxFan = 8;
        value.temperatureWritable = true; value.fanWritable = true;
        value.temperatureNote = "체험 값"; value.fanNote = "체험 값"; value.source = "체험 환경";
        main.postDelayed(() -> result.ok(value), 200);
    }
    @Override public void setTemperature(SeatId seat, float value, Result<Float> result) {
        if (!simulatedParked) { result.fail("주행 상태에서는 설정을 적용할 수 없어요."); return; }
        temperatures.put(seat, value); main.postDelayed(() -> result.ok(value), 180);
    }
    @Override public void setFan(SeatId seat, int value, Result<Integer> result) {
        if (!simulatedParked) { result.fail("주행 상태에서는 설정을 적용할 수 없어요."); return; }
        fans.put(seat, value); main.postDelayed(() -> result.ok(value), 180);
    }
    @Override public void close() { main.removeCallbacksAndMessages(null); }
}

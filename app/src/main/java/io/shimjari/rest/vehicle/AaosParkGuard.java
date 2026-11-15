package io.shimjari.rest.vehicle;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import io.shimjari.rest.core.ParkState;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uses public, read-only AAOS properties. Unknown/denied/unavailable means blocked. */
public final class AaosParkGuard {
    private final Car car;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService reads = Executors.newSingleThreadExecutor();
    public AaosParkGuard(Context context) { car = Car.createCar(context.getApplicationContext()); }
    public void check(VehicleBridge.Result<Boolean> result) {
        reads.execute(() -> {
            boolean allowed = false;
            String failure = null;
            try {
                CarPropertyManager manager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
                if (manager == null) throw new IllegalStateException("차량 상태 서비스를 사용할 수 없어요.");
                CarPropertyValue<Integer> gear = manager.getProperty(Integer.class, VehiclePropertyIds.GEAR_SELECTION, 0);
                CarPropertyValue<Float> speed = manager.getProperty(Float.class, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0);
                allowed = ParkState.canControl(gear != null && gear.getStatus() == CarPropertyValue.STATUS_AVAILABLE,
                    speed != null && speed.getStatus() == CarPropertyValue.STATUS_AVAILABLE,
                    gear != null && gear.getValue() != null && gear.getValue() == VehicleGear.GEAR_PARK,
                    speed != null && speed.getValue() != null ? speed.getValue() : Float.NaN);
            } catch (Exception | LinkageError error) { failure = "정차 상태를 확인할 수 없어 공조 자동 적용을 중지했어요."; }
            final boolean parked = allowed; final String reason = failure;
            main.post(() -> { if (reason != null) result.fail(reason); else result.ok(parked); });
        });
    }
    public void close() { reads.shutdownNow(); if (car != null) car.disconnect(); }
}

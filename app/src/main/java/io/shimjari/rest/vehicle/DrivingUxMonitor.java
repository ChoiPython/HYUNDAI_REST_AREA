package io.shimjari.rest.vehicle;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import java.util.function.Consumer;

/** Display-specific restrictions are authoritative; selecting a passenger role never bypasses them. */
public final class DrivingUxMonitor implements AutoCloseable {
    private final Car car;
    private final CarUxRestrictionsManager manager;
    public DrivingUxMonitor(Context context,Consumer<Boolean> changed){
        // Keep the Activity's display context: passenger displays can have different UX rules.
        car=Car.createCar(context);
        manager=(CarUxRestrictionsManager)car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
        if(manager==null){car.disconnect();throw new IllegalStateException("화면 제한 정보를 확인할 수 없어요.");}
        manager.registerListener(value->changed.accept(value==null||value.isRequiresDistractionOptimization()));
        android.car.drivingstate.CarUxRestrictions current=manager.getCurrentCarUxRestrictions();
        changed.accept(current==null||current.isRequiresDistractionOptimization());
    }
    @Override public void close(){try{manager.unregisterListener();}catch(RuntimeException disconnected){/* The vehicle service may already be gone. */}finally{car.disconnect();}}
}

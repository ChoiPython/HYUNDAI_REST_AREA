package io.shimjari.rest.vehicle;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.HVAC;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit;
import io.shimjari.rest.core.SeatId;
import io.shimjari.rest.core.VehicleSnapshot;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;

/** Typed public SDK integration. Never substitutes a simulated value for a failed read. */
public final class PleosVehicleBridge implements VehicleBridge {
    private final Vehicle vehicle;
    private final AaosParkGuard guard;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean closed;

    public PleosVehicleBridge(Context context) {
        vehicle = new Vehicle(context.getApplicationContext());
        vehicle.initialize();
        guard = new AaosParkGuard(context);
    }
    @Override public boolean isDemo() { return false; }
    private SeatArea area(SeatId seat) {
        switch (seat) {
            case DRIVER: return new SeatArea(SeatValue.ROW_1_LEFT);
            case PASSENGER: return new SeatArea(SeatValue.ROW_1_RIGHT);
            case REAR_LEFT: return new SeatArea(SeatValue.ROW_2_LEFT);
            default: return new SeatArea(SeatValue.ROW_2_RIGHT);
        }
    }
    private final class PendingResult<T> implements Result<T> {
        private final AtomicBoolean delivered = new AtomicBoolean();
        private final Result<T> original;
        private final Runnable timeout;
        PendingResult(Result<T> original) {
            this.original = original;
            timeout = () -> { if (!closed && delivered.compareAndSet(false, true)) original.fail("차량 응답이 늦어지고 있어요. 연결을 확인해 주세요."); };
            main.postDelayed(timeout, 7000);
        }
        boolean active() { return !closed && !delivered.get(); }
        @Override public void ok(T value) { main.post(() -> { if (!closed && delivered.compareAndSet(false, true)) { main.removeCallbacks(timeout); original.ok(value); } }); }
        @Override public void fail(String reason) { main.post(() -> { if (!closed && delivered.compareAndSet(false, true)) { main.removeCallbacks(timeout); original.fail(reason); } }); }
    }
    private <T> PendingResult<T> bounded(Result<T> original) { return new PendingResult<>(original); }
    @Override public void parked(Result<Boolean> result) {
        Result<Boolean> done = bounded(result);
        guard.check(done);
    }
    @Override public void inspect(SeatId seat, Result<VehicleSnapshot> result) {
        Result<VehicleSnapshot> done = bounded(result);
        VehicleSnapshot snapshot = new VehicleSnapshot(); snapshot.source = "Pleos 차량 응답";
        SeatArea selected = area(seat);
        try {
            HVAC hvac = vehicle.getHVAC();
            hvac.checkHvacTemperatureCapabilities((config, areas) -> {
                AreaBounds<Float> bounds = areas.get(selected);
                if (bounds == null || !readable(config.getAccessType().name())) {
                    snapshot.temperatureNote = "선택 좌석의 온도를 읽을 수 없어요.";
                    inspectFan(hvac, selected, snapshot, done); return Unit.INSTANCE;
                }
                snapshot.minTemperature = bounds.getMinValue(); snapshot.maxTemperature = bounds.getMaxValue();
                snapshot.temperatureWritable = writable(config.getAccessType().name());
                hvac.getHvacTemperature(selected, TemperatureUnit.CELSIUS, value -> {
                    snapshot.temperature = value;
                    snapshot.temperatureNote = value == null ? "현재 설정값을 확인할 수 없어요." : "차량에서 확인한 설정값";
                    inspectFan(hvac, selected, snapshot, done); return Unit.INSTANCE;
                }, error -> { snapshot.temperatureNote = "온도 조회 실패"; inspectFan(hvac, selected, snapshot, done); return Unit.INSTANCE; });
                return Unit.INSTANCE;
            }, error -> { snapshot.temperatureNote = "온도 기능을 확인할 수 없어요."; inspectFan(hvac, selected, snapshot, done); return Unit.INSTANCE; });
        } catch (Exception | LinkageError error) { done.fail("Pleos 차량 서비스에 연결할 수 없어요. 체험 모드에서도 휴식 기능을 사용할 수 있어요."); }
    }
    private void inspectFan(HVAC hvac, SeatArea selected, VehicleSnapshot snapshot, Result<VehicleSnapshot> done) {
        try {
            hvac.checkHvacFanSpeedCapabilities((config, areas) -> {
                AreaBounds<Integer> bounds = areas.get(selected);
                if (bounds == null || !readable(config.getAccessType().name())) {
                    snapshot.fanNote = "선택 좌석의 풍량을 읽을 수 없어요."; done.ok(snapshot); return Unit.INSTANCE;
                }
                snapshot.minFan = bounds.getMinValue(); snapshot.maxFan = bounds.getMaxValue();
                snapshot.fanWritable = writable(config.getAccessType().name());
                hvac.getHvacFanSpeed(selected, value -> {
                    snapshot.fan = value; snapshot.fanNote = value == null ? "현재 풍량을 확인할 수 없어요." : "차량에서 확인한 설정값";
                    done.ok(snapshot); return Unit.INSTANCE;
                }, error -> { snapshot.fanNote = "풍량 조회 실패"; done.ok(snapshot); return Unit.INSTANCE; });
                return Unit.INSTANCE;
            }, error -> { snapshot.fanNote = "풍량 기능을 확인할 수 없어요."; done.ok(snapshot); return Unit.INSTANCE; });
        } catch (Exception | LinkageError error) { snapshot.fanNote = "풍량 조회 실패"; done.ok(snapshot); }
    }
    private boolean readable(String access) { return access.equals("READ") || access.equals("READ_WRITE"); }
    private boolean writable(String access) { return access.equals("WRITE") || access.equals("READ_WRITE"); }

    @Override public void setTemperature(SeatId seat, float value, Result<Float> result) {
        PendingResult<Float> done = bounded(result);
        parked(new Result<Boolean>() {
            @Override public void ok(Boolean parked) {
                if (!done.active()) return;
                if (!parked) { done.fail("P단과 정차 상태에서만 환경을 적용할 수 있어요."); return; }
                inspect(seat, new Result<VehicleSnapshot>() {
                    @Override public void ok(VehicleSnapshot snapshot) {
                        if (!done.active()) return;
                        if (!snapshot.acceptsTemperature(value)) { done.fail("온도가 선택 좌석의 지원 범위를 벗어났어요."); return; }
                        try {
                            vehicle.getHVAC().setHvacTemperature(area(seat), value, TemperatureUnit.CELSIUS, (a, accepted) -> {
                                vehicle.getHVAC().getHvacTemperature(area(seat), TemperatureUnit.CELSIUS,
                                    actual -> { if (actual != null && Math.abs(actual - value) < 0.11f) done.ok(actual); else done.fail("온도 요청 후 실제 설정값을 확인하지 못했어요."); return Unit.INSTANCE; },
                                    error -> { done.fail("온도 요청 후 읽기에 실패했어요."); return Unit.INSTANCE; });
                                return Unit.INSTANCE;
                            }, error -> { done.fail("온도를 적용하지 못했어요."); return Unit.INSTANCE; });
                        } catch (Exception error) { done.fail("온도 적용 중 연결이 끊겼어요."); }
                    }
                    @Override public void fail(String reason) { done.fail(reason); }
                });
            }
            @Override public void fail(String reason) { done.fail(reason); }
        });
    }
    @Override public void setFan(SeatId seat, int value, Result<Integer> result) {
        PendingResult<Integer> done = bounded(result);
        parked(new Result<Boolean>() {
            @Override public void ok(Boolean parked) {
                if (!done.active()) return;
                if (!parked) { done.fail("P단과 정차 상태에서만 환경을 적용할 수 있어요."); return; }
                inspect(seat, new Result<VehicleSnapshot>() {
                    @Override public void ok(VehicleSnapshot snapshot) {
                        if (!done.active()) return;
                        if (!snapshot.acceptsFan(value)) { done.fail("풍량이 선택 좌석의 지원 범위를 벗어났어요."); return; }
                        try {
                            vehicle.getHVAC().setHvacFanSpeed(area(seat), value, (a, accepted) -> {
                                vehicle.getHVAC().getHvacFanSpeed(area(seat), actual -> {
                                    if (actual != null && actual == value) done.ok(actual); else done.fail("풍량 요청 후 실제 설정값을 확인하지 못했어요.");
                                    return Unit.INSTANCE;
                                }, error -> { done.fail("풍량 요청 후 읽기에 실패했어요."); return Unit.INSTANCE; });
                                return Unit.INSTANCE;
                            }, error -> { done.fail("풍량을 적용하지 못했어요."); return Unit.INSTANCE; });
                        } catch (Exception error) { done.fail("풍량 적용 중 연결이 끊겼어요."); }
                    }
                    @Override public void fail(String reason) { done.fail(reason); }
                });
            }
            @Override public void fail(String reason) { done.fail(reason); }
        });
    }
    @Override public void close() {
        closed = true; main.removeCallbacksAndMessages(null);
        guard.close(); vehicle.release();
    }
}

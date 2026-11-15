package io.shimjari.rest;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import io.shimjari.rest.core.RestProfile;
import io.shimjari.rest.core.RestSession;
import io.shimjari.rest.core.VehicleSnapshot;
import io.shimjari.rest.vehicle.DemoVehicleBridge;
import io.shimjari.rest.vehicle.PleosVehicleBridge;
import io.shimjari.rest.vehicle.VehicleBridge;
import io.shimjari.rest.music.MusicController;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RestController {
    public interface Observer { void changed(); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    public final Store store;
    public final AlarmScheduler alarms;
    public final SoundPlayer sounds;
    public final MusicController music;
    public List<RestProfile> profiles;
    public RestProfile selected;
    public RestSession session;
    public VehicleBridge vehicle;
    public String message = "";
    public boolean busy;
    private boolean visible;
    private boolean windowFocused;
    private final List<Observer> observers = new ArrayList<>();

    public RestController(Context context) {
        this.context = context.getApplicationContext(); store = new Store(context); alarms = new AlarmScheduler(context); sounds = new SoundPlayer(context);
        sounds.listener(this::publish);music=new MusicController(context,this::publish);
        profiles = store.profiles(); selected = profiles.get(0);
        for (RestProfile p : profiles) if (p.id.equals(store.selectedId())) selected = p;
        session = store.session();
        if (session != null && (session.phase == RestSession.Phase.PREPARING || session.phase == RestSession.Phase.FINISHING)) {
            session.phase = RestSession.Phase.AWAKE;
            session.appendReport("앱이 다시 시작되었어요. 변경된 차량 설정은 휴식 종료 후 확인해 주세요."); store.session(session);
        }
        boolean demo = session != null ? session.demo : store.demo();
        if (!openVehicle(demo)) { vehicle = new DemoVehicleBridge(); message = "차량 연결이 없어 체험 모드로 열었어요."; }
        if (session != null && !session.demo && vehicle.isDemo()) message = "이전 차량 세션을 찾았어요. 다시 연결한 뒤 복원하거나 차량에서 직접 설정을 확인해 주세요.";
        refresh();
        // Force-stop clears Android alarms; reopening a saved rest must restore its wake-up.
        if (session != null && session.phase == RestSession.Phase.RESTING) alarms.schedule(session);
    }
    private boolean openVehicle(boolean demo) {
        try {
            if (!demo) Class.forName("android.car.Car");
            VehicleBridge next = demo ? new DemoVehicleBridge() : new PleosVehicleBridge(context);
            if (vehicle != null) vehicle.close(); vehicle = next; return true;
        } catch (Exception | LinkageError error) { return false; }
    }
    public void observe(Observer observer) { if (!observers.contains(observer)) observers.add(observer); }
    public void remove(Observer observer) { observers.remove(observer); }
    private void publish() { for (Observer value : new ArrayList<>(observers)) value.changed(); }
    public void visible(boolean value) { visible = value; if (!value) sounds.stop(); else refresh(); }
    public void focused(boolean value) {windowFocused=value;if(value)main.post(this::refresh);}
    public int boot() { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
    public long remaining() { return session == null ? 0 : session.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot()); }
    public void refresh() {
        if (session != null && session.phase == RestSession.Phase.RESTING && remaining() == 0) alarm(session.id);
        if (session != null && session.phase == RestSession.Phase.RESTING && visible) {if(windowFocused)sounds.play(session.profile);sounds.remaining(remaining());}
    }
    public void saveProfile() { store.saveProfiles(profiles); store.selectedId(selected.id); publish(); }
    public void select(RestProfile profile) { if (session != null) return; selected = profile; saveProfile(); }
    public void connect(boolean demo, VehicleBridge.Result<VehicleSnapshot> result) {
        if (session != null && session.demo != demo) { result.fail("휴식을 마친 뒤 연결 모드를 바꿀 수 있어요."); return; }
        if (!openVehicle(demo)) { result.fail("이 기기에서 Pleos 차량 서비스를 찾지 못했어요."); return; }
        store.demo(demo);
        busy = true; publish();
        main.postDelayed(() -> vehicle.inspect(selected.seat, new VehicleBridge.Result<VehicleSnapshot>() {
            @Override public void ok(VehicleSnapshot snapshot) { busy = false; message = demo ? "체험 환경을 준비했어요." : "차량에서 지원 정보를 확인했어요."; publish(); result.ok(snapshot); }
            @Override public void fail(String reason) { busy = false; message = reason; publish(); result.fail(reason); }
        }), demo ? 0 : 700);
    }
    public void preview(VehicleBridge.Result<VehicleSnapshot> result) {
        if (busy || session != null) return;
        busy = true; publish();
        vehicle.inspect(selected.seat, new VehicleBridge.Result<VehicleSnapshot>() {
            @Override public void ok(VehicleSnapshot snapshot) { busy = false; publish(); result.ok(snapshot); }
            @Override public void fail(String reason) { busy = false; publish(); result.fail(reason); }
        });
    }
    public void begin(VehicleSnapshot snapshot, boolean applyEnvironment) {
        if (session != null || busy) return;
        session = new RestSession(UUID.randomUUID().toString(), selected, vehicle.isDemo());
        session.originalTemperature = snapshot.temperature; session.originalFan = snapshot.fan;
        store.session(session); busy = true; publish();
        RestSession current = session;
        if (!applyEnvironment) { current.appendReport("환경 자동 적용 없이 타이머를 시작했어요."); start(current); return; }
        vehicle.parked(new VehicleBridge.Result<Boolean>() {
            @Override public void ok(Boolean parked) {
                if (!parked) { current.appendReport("정차 조건이 충족되지 않아 환경을 변경하지 않았어요."); start(current); return; }
                applyTemperature(current, snapshot);
            }
            @Override public void fail(String reason) { current.appendReport(reason + " 환경은 변경하지 않았어요."); start(current); }
        });
    }
    private void applyTemperature(RestSession current, VehicleSnapshot snapshot) {
        if (!snapshot.acceptsTemperature(current.profile.temperature)) { current.appendReport("온도: 선택한 설정을 지원하지 않아 유지했어요."); applyFan(current, snapshot); return; }
        current.temperatureTouched = true; store.session(current);
        vehicle.setTemperature(current.profile.seat, current.profile.temperature, new VehicleBridge.Result<Float>() {
            @Override public void ok(Float value) { current.appendReport("온도 설정값 확인: " + value + "°C"); applyFan(current, snapshot); }
            @Override public void fail(String reason) { current.appendReport("온도: " + reason); applyFan(current, snapshot); }
        });
    }
    private void applyFan(RestSession current, VehicleSnapshot snapshot) {
        if (!snapshot.acceptsFan(current.profile.fan)) { current.appendReport("풍량: 선택한 설정을 지원하지 않아 유지했어요."); start(current); return; }
        current.fanTouched = true; store.session(current);
        vehicle.setFan(current.profile.seat, current.profile.fan, new VehicleBridge.Result<Integer>() {
            @Override public void ok(Integer value) { current.appendReport("풍량 설정값 확인: " + value); start(current); }
            @Override public void fail(String reason) { current.appendReport("풍량: " + reason); start(current); }
        });
    }
    private void start(RestSession current) {
        if (session != current) return;
        current.start(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot()); store.session(current);
        alarms.schedule(current); busy = false; if (visible && windowFocused) sounds.play(current.profile); publish();
    }
    public void extend() { if (session == null || session.phase != RestSession.Phase.RESTING) return; session.extendFiveMinutes(); store.session(session); alarms.schedule(session); publish(); }
    public boolean alarm(String id) {
        if (session == null || !session.id.equals(id) || session.phase != RestSession.Phase.RESTING) return false;
        // A stale broadcast from a prior extension must not wake the current session early.
        if (remaining() > 1500) { alarms.schedule(session); return false; }
        session.phase = RestSession.Phase.AWAKE; store.session(session); sounds.stop(); publish(); return true;
    }
    public void finish() {
        if (session == null || busy || session.phase == RestSession.Phase.REVIEW) return;
        RestSession current = session; busy = true; current.phase = RestSession.Phase.FINISHING;
        current.finishedWall = System.currentTimeMillis(); store.session(current); alarms.cancel(); sounds.stop(); publish();
        if (current.demo != vehicle.isDemo()) { current.appendReport("차량 연결이 달라 원래 설정을 복원하지 못했어요. 차량에서 직접 확인해 주세요."); review(current); return; }
        if (!current.temperatureTouched && !current.fanTouched) { review(current); return; }
        vehicle.inspect(current.profile.seat, new VehicleBridge.Result<VehicleSnapshot>() {
            @Override public void ok(VehicleSnapshot snapshot) { restoreTemperature(current, snapshot); }
            @Override public void fail(String reason) { current.appendReport("복원 확인 실패: " + reason + " 차량 설정을 직접 확인해 주세요."); review(current); }
        });
    }
    private void restoreTemperature(RestSession current, VehicleSnapshot snapshot) {
        if (!current.temperatureTouched || current.originalTemperature == null) { restoreFan(current, snapshot); return; }
        if (!VehicleSnapshot.shouldRestoreTemperature(snapshot.temperature, current.profile.temperature)) {
            current.appendReport("온도: 중간에 바뀌었거나 현재 값을 확인할 수 없어 유지했어요."); restoreFan(current, snapshot); return;
        }
        vehicle.setTemperature(current.profile.seat, current.originalTemperature, new VehicleBridge.Result<Float>() {
            @Override public void ok(Float value) { current.appendReport("온도 복원 확인: " + value + "°C"); restoreFan(current, snapshot); }
            @Override public void fail(String reason) { current.appendReport("온도 복원 실패: " + reason); restoreFan(current, snapshot); }
        });
    }
    private void restoreFan(RestSession current, VehicleSnapshot snapshot) {
        if (!current.fanTouched || current.originalFan == null) { review(current); return; }
        if (!VehicleSnapshot.shouldRestoreFan(snapshot.fan, current.profile.fan)) {
            current.appendReport("풍량: 중간에 바뀌었거나 현재 값을 확인할 수 없어 유지했어요."); review(current); return;
        }
        vehicle.setFan(current.profile.seat, current.originalFan, new VehicleBridge.Result<Integer>() {
            @Override public void ok(Integer value) { current.appendReport("풍량 복원 확인: " + value); review(current); }
            @Override public void fail(String reason) { current.appendReport("풍량 복원 실패: " + reason); review(current); }
        });
    }
    private void review(RestSession current) { current.phase = RestSession.Phase.REVIEW; store.session(current); busy = false; publish(); }
    public void feedback(String value) {
        if (session == null || session.phase != RestSession.Phase.REVIEW) return;
        store.addHistory(session, value); store.session(null); session = null; message = "이번 쉼을 기록했어요."; publish();
    }
}

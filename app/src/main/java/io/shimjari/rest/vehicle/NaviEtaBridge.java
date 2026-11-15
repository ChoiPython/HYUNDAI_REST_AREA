package io.shimjari.rest.vehicle;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.DrivingInfo;
import ai.pleos.playground.navi.data.RouteStateInfo;
import ai.pleos.playground.navi.constants.NaviErrorCode;
import kotlin.Unit;
import java.util.function.Consumer;

/** Reads remaining seconds only. Never requests a route, GPS coordinates or a route change. */
public final class NaviEtaBridge implements AutoCloseable {
    private final NaviHelper helper;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Consumer<Integer> seconds;
    private final Consumer<String> status;
    private boolean connected,closed,routing;
    private long lastPoll;
    private final NaviHelperEventListener listener=new NaviHelperEventListener(){
        @Override public void onRouteStateInfo(RouteStateInfo info){main.post(()->{if(closed)return;routing=info!=null&&info.isRouting();if(routing)requestDestination();else status.accept("활성 길안내가 없어 마지막 기상 예약을 유지해요.");});}
        @Override public void onDestinationInfo(DestinationInfo info){if(info!=null)deliver(info.getRemainTime());}
        @Override public void onDrivingInfo(DrivingInfo info){if(info!=null&&info.getDestination()!=null)deliver(info.getDestination().getDuration());}
        @Override public void onRouteCancelled(){main.post(()->{routing=false;status.accept("길안내가 종료되어 마지막 기상 예약을 유지해요.");});}
        @Override public void onError(NaviErrorCode error){main.post(()->{if(!closed)status.accept("내비게이션 응답을 확인하지 못했어요. 마지막 기상 예약은 유지돼요.");});}
    };
    public NaviEtaBridge(Context context,Consumer<Integer> seconds,Consumer<String> status){
        this.seconds=seconds;this.status=status;helper=new NaviHelper(context.getApplicationContext());
        helper.initialize(()->{main.post(()->{if(closed)return;connected=true;helper.addListener(listener);poll();});return Unit.INSTANCE;},failure->{main.post(()->{if(!closed)status.accept("내비게이션 서비스 연결에 실패했어요. 직접 입력한 도착 시각을 사용할 수 있어요.");});return Unit.INSTANCE;});
    }
    private void deliver(int remaining){main.post(()->{if(!closed&&connected&&routing&&remaining>=0&&remaining<=12600)seconds.accept(remaining);});}
    private void requestDestination(){try{helper.getDestinationInfo();}catch(RuntimeException e){status.accept("내비게이션 조회 권한 또는 길안내 정보를 확인해 주세요.");}}
    public void poll(){if(closed||!connected)return;long now=android.os.SystemClock.elapsedRealtime();if(lastPoll>0&&now-lastPoll<15000)return;lastPoll=now;try{helper.getRouteStateInfo();}catch(RuntimeException e){status.accept("내비게이션 경로 정보를 읽지 못했어요.");}}
    @Override public void close(){closed=true;main.removeCallbacksAndMessages(null);try{if(connected)helper.removeListener(listener);helper.release();}catch(RuntimeException ignored){}connected=false;}
}

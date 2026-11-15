package io.shimjari.rest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import io.shimjari.rest.core.RestSession;

/** Runs only user-started passenger audio, and releases itself when audio stops. */
public final class AudioPlaybackService extends Service implements RestController.Observer {
    private static final String CHANNEL="passenger_audio";
    private static final int ID=52;
    private RestController controller;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean started;
    private long transientSince;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!started)return;
        controller.refresh();
        if(controller.sounds.interrupted()){
            if(transientSince==0)transientSince=android.os.SystemClock.elapsedRealtime();
            if(android.os.SystemClock.elapsedRealtime()-transientSince>30000){controller.audioCommand("pause");return;}
        }else transientSince=0;
        reconcile();if(started)main.postDelayed(this,1000);
    }};

    @Override public void onCreate(){
        super.onCreate();controller=((RestApp)getApplication()).controller();
        NotificationChannel channel=new NotificationChannel(CHANNEL,"동승자 휴식 음악",NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);channel.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        controller.observe(this);
    }
    private PendingIntent action(String command,int id){
        return PendingIntent.getService(this,id,new Intent(this,AudioPlaybackService.class).setAction(command),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,53,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(controller.sounds.title().isEmpty()?"동승자 휴식 음악":controller.sounds.title())
            .setContentText("내비게이션을 보거나 화면을 꺼도 음악이 이어져요")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"일시정지",action("pause",54)).build())
            .addAction(new Notification.Action.Builder(null,"다음 곡",action("next",55)).build())
            .addAction(new Notification.Action.Builder(null,"음악 끄기",action("stop",56)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(controller.sounds.token()).setShowActionsInCompactView(0,1,2)).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // The private service is started only by an explicit app or media-control action.
        startForeground(ID,notification());started=true;controller.audioServiceActive(true);
        String action=intent==null?"stop":intent.getAction();
        if(!controller.passengerAudioSession()){controller.audioCommand("stop");stopAudioService();return START_NOT_STICKY;}
        controller.performAudio(action==null?"start":action);
        main.removeCallbacks(tick);main.post(tick);
        return START_NOT_STICKY;
    }
    private void reconcile(){
        if(!controller.passengerAudioSession()||!controller.sounds.ongoing()){stopAudioService();return;}
        getSystemService(NotificationManager.class).notify(ID,notification());
    }
    private void stopAudioService(){started=false;main.removeCallbacks(tick);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void changed(){if(started)main.post(this::reconcile);}
    @Override public void onTaskRemoved(Intent intent){controller.audioCommand("stop");stopAudioService();super.onTaskRemoved(intent);}
    @Override public void onDestroy(){started=false;main.removeCallbacksAndMessages(null);controller.remove(this);controller.audioServiceActive(false);if(controller.sounds.ongoing())controller.sounds.stop();else controller.sounds.releasePaused();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

package io.shimjari.rest;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import io.shimjari.rest.core.RestSession;
import io.shimjari.rest.core.RestMode;

public final class AlarmScheduler {
    public static final String CHANNEL = "rest_wake";
    public static final String PASSENGER_CHANNEL="passenger_wake";
    private final Context context;
    private final AlarmManager manager;
    public AlarmScheduler(Context context) {
        this.context = context;
        manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "휴식 기상 알림", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("예약한 휴식 종료 시간 알림");
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        NotificationChannel quiet=new NotificationChannel(PASSENGER_CHANNEL,"동승자 휴식 종료 안내",NotificationManager.IMPORTANCE_LOW);
        quiet.setDescription("운전자를 방해하지 않는 조용한 동승자 휴식 안내");quiet.setSound(null,null);quiet.enableVibration(false);
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(quiet);
    }
    private PendingIntent broadcast(String sessionId) {
        Intent intent = new Intent(context, AlarmReceiver.class).putExtra("session", sessionId);
        return PendingIntent.getBroadcast(context, 40, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public boolean exactAvailable() { return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms(); }
    public boolean notificationsAvailable() {
        return ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled()
            && (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }
    public void schedule(RestSession session) {
        PendingIntent pending = broadcast(session.id);
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        long trigger = SystemClock.elapsedRealtime() + Math.max(500, session.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot));
        try {
            if (exactAvailable()) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending);
            else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending);
        } catch (SecurityException denied) { manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending); }
    }
    public void cancel() { manager.cancel(broadcast("")); }
    public void notifyFinished(RestSession session){
        if(!notificationsAvailable())return;
        boolean passenger=session.profile.mode==RestMode.PASSENGER_RIDE;
        PendingIntent open=PendingIntent.getActivity(context,41,new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(context,passenger?PASSENGER_CHANNEL:CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle(passenger?"쉼자리 · 동승자 휴식 시간이 끝났어요":"쉼자리 · 일어날 시간이에요")
            .setContentText(passenger?"음악을 멈췄어요. 준비되면 휴식을 마쳐 주세요.":"천천히 몸을 깨우고, 준비되면 휴식을 마쳐 주세요.")
            .setContentIntent(open).setAutoCancel(true).setCategory(passenger?Notification.CATEGORY_REMINDER:Notification.CATEGORY_ALARM).build();
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(41,notice);
    }
}

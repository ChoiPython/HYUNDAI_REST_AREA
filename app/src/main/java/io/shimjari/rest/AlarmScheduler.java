package io.shimjari.rest;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import io.shimjari.rest.core.RestSession;

public final class AlarmScheduler {
    public static final String CHANNEL = "rest_wake";
    private final Context context;
    private final AlarmManager manager;
    public AlarmScheduler(Context context) {
        this.context = context;
        manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "휴식 기상 알림", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("예약한 휴식 종료 시간 알림");
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
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
}

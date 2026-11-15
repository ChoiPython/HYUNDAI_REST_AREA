package io.shimjari.rest;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        RestController controller = ((RestApp) context.getApplicationContext()).controller();
        if (!controller.alarm(intent.getStringExtra("session"))) return;
        if (!controller.alarms.notificationsAvailable()) return;
        PendingIntent open = PendingIntent.getActivity(context, 41, new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, AlarmScheduler.CHANNEL)
            .setSmallIcon(io.shimjari.rest.R.drawable.ic_launcher).setContentTitle("쉼자리 · 일어날 시간이에요")
            .setContentText("천천히 몸을 깨우고, 준비되면 휴식을 마쳐 주세요.")
            .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).build();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(41, notification);
    }
}

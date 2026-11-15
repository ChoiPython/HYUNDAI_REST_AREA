package io.shimjari.rest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        RestController controller = ((RestApp) context.getApplicationContext()).controller();
        controller.alarm(intent.getStringExtra("session"));
    }
}

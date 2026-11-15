package io.shimjari.rest;
import android.content.*;
public final class PersonalWakeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){new PersonalWake(context).fire(intent.getStringExtra("id"),intent.getLongExtra("deadline",0));}
}

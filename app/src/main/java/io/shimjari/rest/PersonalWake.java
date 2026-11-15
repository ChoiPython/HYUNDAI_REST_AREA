package io.shimjari.rest;

import android.app.*;
import android.content.*;
import android.os.*;

/** A phone-local reservation remains scheduled when the vehicle link is lost. */
public final class PersonalWake {
    public static final String CHANNEL="personal_passenger_wake";
    private final Context context;
    private final android.content.SharedPreferences prefs;
    public PersonalWake(Context context){
        this.context=context.getApplicationContext();prefs=context.getSharedPreferences("personal_wake",Context.MODE_PRIVATE);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"동승자 개인 기상",NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null,null);channel.enableVibration(true);channel.setVibrationPattern(new long[]{0,400,250,400,250,600});
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private PendingIntent alarm(String id,long deadline){return PendingIntent.getBroadcast(context,61,new Intent(context,PersonalWakeReceiver.class).putExtra("id",id).putExtra("deadline",deadline),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public void schedule(String id,long deadline,long serverNow){
        if(id.equals(prefs.getString("id",""))&&(prefs.getBoolean("suppressed",false)||prefs.getBoolean("fired",false)||deadline==prefs.getLong("deadline",0)))return;
        long local=System.currentTimeMillis()+Math.max(0,deadline-serverNow);
        if(!prefs.edit().putString("id",id).putLong("deadline",deadline).putLong("localWall",local).putBoolean("fired",false).putBoolean("suppressed",false).commit())throw new IllegalStateException("개인 알림을 저장하지 못했어요.");
        reserve(id,deadline,local);
    }
    public void restore(){if(!prefs.getBoolean("fired",false)&&!prefs.getBoolean("suppressed",false)&&prefs.contains("id"))reserve(prefs.getString("id",""),prefs.getLong("deadline",0),prefs.getLong("localWall",0));}
    private void reserve(String id,long deadline,long local){
        AlarmManager manager=context.getSystemService(AlarmManager.class);long trigger=SystemClock.elapsedRealtime()+Math.max(500,local-System.currentTimeMillis());
        try{if(Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms())manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,alarm(id,deadline));else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,alarm(id,deadline));}
        catch(SecurityException denied){manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,alarm(id,deadline));}
    }
    public void cancel(){context.getSystemService(AlarmManager.class).cancel(alarm("",0));prefs.edit().clear().apply();}
    public void cancelIf(String id){if(id.equals(prefs.getString("id","")))cancel();}
    public void suppress(){context.getSystemService(AlarmManager.class).cancel(alarm("",0));prefs.edit().putBoolean("suppressed",true).apply();}
    public long when(){return prefs.getBoolean("fired",false)||prefs.getBoolean("suppressed",false)?0:prefs.getLong("localWall",0);}
    public void fire(String id,long deadline){
        if(!id.equals(prefs.getString("id",""))||deadline!=prefs.getLong("deadline",0)||prefs.getBoolean("fired",false)||prefs.getBoolean("suppressed",false))return;
        prefs.edit().putBoolean("fired",true).commit();
        if(!context.getSystemService(NotificationManager.class).areNotificationsEnabled())return;
        PendingIntent open=PendingIntent.getActivity(context,62,new Intent(context,CompanionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("쉼자리 · 이제 천천히 일어나세요")
            .setContentText("예약한 기상 시간이 됐어요. 도착 준비를 시작해 주세요.").setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).build();
        context.getSystemService(NotificationManager.class).notify(62,notice);
    }
}

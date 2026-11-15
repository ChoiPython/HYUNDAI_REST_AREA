package io.shimjari.rest.companion;

import android.app.*;
import android.content.*;
import android.os.*;
import io.shimjari.rest.*;
import org.json.JSONObject;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Explicit, visible LAN connection. No boot receiver, public web service or cloud relay. */
public final class CompanionService extends Service {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService connection=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor clients=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private final Set<SSLSocket> openSockets=ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private String role="";
    private volatile SSLServerSocket server;
    private CompanionManager manager;
    private static final int NOTIFICATION=70;
    private final Runnable maintenance=new Runnable(){@Override public void run(){
        if(!running)return;
        manager.expire();manager.pollNavigation();if("host".equals(role)&&manager.hostIdle()){manager.error("사용 중인 휴대폰이 없어 차량 연결을 종료했어요. 필요할 때 다시 열어 주세요.");stopSelf();return;}
        main.postDelayed(this,5000);
    }};
    @Override public void onCreate(){super.onCreate();manager=((RestApp)getApplication()).controller().companions;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String requested=intent==null?"":intent.getAction();
        if("stop".equals(requested)||(!"host".equals(requested)&&!"phone".equals(requested))){stopSelf();return START_NOT_STICKY;}
        if(running){if(!role.equals(requested))manager.error("현재 연결을 종료한 뒤 기기 역할을 바꿔 주세요.");return START_NOT_STICKY;}
        role=requested;
        NotificationChannel channel=new NotificationChannel("companion_link","차량과 동승자 휴대폰 연결",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);channel.enableVibration(false);getSystemService(NotificationManager.class).createNotificationChannel(channel);
        PendingIntent open=PendingIntent.getActivity(this,71,new Intent(this,CompanionActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close=PendingIntent.getService(this,72,new Intent(this,CompanionService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(this,"companion_link").setSmallIcon(R.drawable.ic_launcher).setContentTitle("host".equals(role)?"쉼자리 · 동승자 휴대폰 연결 중":"쉼자리 · 차량과 연결 중")
            .setContentText("좌석 설정·휴식 상태·기상 시각을 주고받고 있어요").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).addAction(new Notification.Action.Builder(null,"연결 종료",close).build()).build();
        startForeground(NOTIFICATION,notice);running=true;main.postDelayed(maintenance,5000);
        connection.execute("host".equals(role)?this::host:this::phone);return START_NOT_STICKY;
    }
    private void host(){
        try{
            server=LocalTls.server();if(!running){server.close();return;}String cert=LocalTls.serverFingerprint(),addresses=addresses();main.post(()->{if(running)manager.serverReady(cert,addresses);});
            while(running){SSLSocket socket=(SSLSocket)server.accept();
                if(!socket.getInetAddress().isSiteLocalAddress()&&!socket.getInetAddress().isLoopbackAddress()){socket.close();continue;}
                openSockets.add(socket);
                try{clients.execute(()->serve(socket));}catch(RejectedExecutionException full){openSockets.remove(socket);socket.close();}
            }
        }catch(Exception stopped){if(running)main.post(()->{manager.error("차량 연결을 열지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.");stopSelf();});}
    }
    private void serve(SSLSocket socket){
        try(SSLSocket client=socket){
            client.setSoTimeout(5000);client.startHandshake();JSONObject input=LocalTls.read(client.getInputStream());
            FutureTask<JSONObject> task=new FutureTask<>(()->manager.handle(input));main.post(task);
            JSONObject response;try{response=task.get(3,TimeUnit.SECONDS);}catch(TimeoutException timeout){task.cancel(false);return;}
            LocalTls.write(client.getOutputStream(),response);
        }catch(Exception invalid){/* Invalid, unauthenticated and timed-out clients receive no application data. */}
        finally{openSockets.remove(socket);}
    }
    private static String addresses()throws Exception{
        List<String> list=new ArrayList<>();Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()){NetworkInterface network=interfaces.nextElement();if(!network.isUp())continue;Enumeration<InetAddress> values=network.getInetAddresses();while(values.hasMoreElements()){InetAddress ip=values.nextElement();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()&&!ip.isLoopbackAddress())list.add(ip.getHostAddress()+":"+LocalTls.PORT);}}
        return list.isEmpty()?"Wi-Fi 또는 차량 핫스폿에 연결한 뒤 다시 열어 주세요.":android.text.TextUtils.join("\n",list);
    }
    private void phone(){
        int failures=0;
        while(running){
            try{JSONObject state=manager.poll();failures=0;main.post(()->{if(running)manager.receive(state);});}
            catch(Exception failure){failures++;final int count=failures;main.post(()->manager.error(count<6?"차량 연결을 다시 확인하고 있어요. 개인 기상 예약은 유지돼요.":"차량 연결이 끊겼어요. 마지막 개인 기상 예약은 유지돼요."));if(failures>=6){main.post(this::stopSelf);return;}}
            try{Thread.sleep(5000);}catch(InterruptedException stopped){Thread.currentThread().interrupt();return;}
        }
    }
    @Override public void onTaskRemoved(Intent intent){stopSelf();super.onTaskRemoved(intent);}
    @Override public void onDestroy(){
        running=false;main.removeCallbacksAndMessages(null);
        try{if(server!=null)server.close();}catch(Exception ignored){}
        for(SSLSocket socket:openSockets)try{socket.close();}catch(Exception ignored){}
        clients.shutdownNow();connection.shutdownNow();manager.stopped();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

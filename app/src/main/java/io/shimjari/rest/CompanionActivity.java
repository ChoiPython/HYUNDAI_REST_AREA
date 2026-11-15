package io.shimjari.rest;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import io.shimjari.rest.companion.*;
import io.shimjari.rest.core.*;
import io.shimjari.rest.vehicle.DrivingUxMonitor;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class CompanionActivity extends Activity implements RestController.Observer {
    private UiStyle ui;
    private RestController c;
    private CompanionManager link;
    private LinearLayout body;
    private ScrollView activeScroll;
    private boolean renderedPhone;
    private int restoreScrollY;
    private boolean phone,restricted;
    private DrivingUxMonitor ux;
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean bold){return ui.text(value,size,ui.text,bold);}
    private void gap(int height){body.addView(new android.view.View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(String value,Runnable click){Button button=ui.button(value,false,false,click);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(8);body.addView(button,params);return button;}
    private void note(String title,String description){gap(20);body.addView(text(title,18,true));gap(7);TextView detail=ui.text(description,14,ui.muted,false);body.addView(detail);}
    private String time(long wall){return wall<=0?"아직 예약하지 않았어요":new SimpleDateFormat("M월 d일 a h:mm",Locale.KOREA).format(new Date(wall));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new UiStyle(this);c=((RestApp)getApplication()).controller();link=c.companions;phone=saved==null?!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE):saved.getBoolean("phone");
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))try{ux=new DrivingUxMonitor(this,value->{restricted=value;render();});}catch(Exception|LinkageError e){restricted=true;}
        render();
    }
    @Override protected void onStart(){super.onStart();c.observe(this);render();}
    @Override protected void onStop(){c.remove(this);super.onStop();}
    @Override protected void onDestroy(){if(ux!=null)ux.close();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("phone",phone);}
    @Override public void changed(){runOnUiThread(this::render);}
    private void render(){
        if(isFinishing()||c==null)return;
        restoreScrollY=activeScroll!=null&&renderedPhone==phone?(activeScroll.isLaidOut()?activeScroll.getScrollY():restoreScrollY):0;
        renderedPhone=phone;
        ScrollView scroll=new ScrollView(this);activeScroll=scroll;ui.restoreScroll(scroll,restoreScrollY);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(20),dp(24),dp(28));body.setBackgroundColor(ui.background);scroll.addView(body);setContentView(scroll);ui.systemBars(false);scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        body.addView(text("휴대폰과 차량 연결",24,true));
        if(restricted){note("차량 화면 제한이 적용 중이에요","연결과 음악은 별도로 이어집니다. 연결 설정은 주차 후 또는 허용된 승객 화면에서 해 주세요.");return;}
        button("휴식 화면으로 돌아가기",this::finish);
        LinearLayout tabs=new LinearLayout(this);tabs.setGravity(Gravity.CENTER_VERTICAL);gap(16);body.addView(tabs);
        for(boolean selectPhone:new boolean[]{false,true}){Button tab=ui.button(selectPhone?"동승자 휴대폰":"차량",phone==selectPhone,false,()->{phone=selectPhone;render();});ui.compact(tab,14);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(!selectPhone)params.rightMargin=dp(8);tabs.addView(tab,params);}

        note(phone?"동승자 휴대폰":"차량 연결 관리",phone?"같은 Wi-Fi 또는 차량 핫스폿에서 연결해 주세요. 별명·좌석·음악·선호 환경과 기상 예약만 주고받습니다.":"출발 전에 휴대폰을 승인하고 음악 제어를 맡길 사람을 정해 주세요. 주행 중 차량 화면 제한은 유지됩니다.");
        if(!link.status.isEmpty())body.addView(text(link.status,15,true));
        if(phone)phone();else host();
    }
    private void host(){
        button(link.hostRunning?"새 연결 코드 열기":"휴대폰 연결받기",link::startHost).setEnabled(!link.clientRunning);
        if(link.hostRunning){
            note("차량 주소",link.addresses);body.addView(text("인증 코드  "+LocalTls.display(link.fingerprint),23,true));
            body.addView(text("연결 코드  "+link.pin,25,true));body.addView(text("연결 코드는 2분 동안 유효해요. 휴대폰 인증 코드와 비교한 뒤 승인해 주세요.",14,false));
            button("연결 종료",link::stopLink);
        }
        for(CompanionManager.Pending pending:link.pending()){
            note("연결 승인 요청 · "+pending.name,"직접 연결하려는 휴대폰인지 확인해 주세요.");button(pending.name+" 연결 허용",()->link.approve(pending.id,true));button(pending.name+" 연결 거절",()->link.approve(pending.id,false));
        }
        if(link.tripArrival>0)note("함께 사용하는 도착 예상 시각",time(link.tripArrival)+" · "+link.arrivalSource);
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)){
            note("내비게이션 도착 연동",link.navigationStatus+"\n현재 길안내의 남은 시간만 추출해 사용합니다. 주소·좌표는 휴대폰에 보내거나 저장하지 않습니다.");
            button(link.navigationEnabled?"내비게이션 연동 중지":"내비게이션 도착 연동",()->{if(link.navigationEnabled){link.stopNavigation();render();}else navigation();}).setEnabled(link.hostRunning);
        }
        button("차량 도착 예상 시각 설정",()->arrival(link.tripArrival,wall->{try{link.tripArrival(wall);}catch(IllegalArgumentException invalid){toast(invalid.getMessage());}}));
        List<CompanionManager.Peer> peers=link.peers();
        if(peers.isEmpty())note("등록된 동승자가 없어요","휴대폰에서 차량 주소를 입력하고 차량 인증·연결 코드를 확인하면 여기에 승인 요청이 나타나요.");
        for(CompanionManager.Peer peer:peers){
            RestProfile p=peer.profile;note(p.name+" · "+p.seat.label,p.temperature+"°C · 풍량 "+p.fan+" · "+p.soundName()+"\n"+(peer.resting?"휴식 중 · 기상 "+time(peer.deadline):"휴식 대기 또는 종료"));
            if(p.arrivalEnabled)body.addView(text("도착 "+time(p.arrivalWall)+" · "+p.wakeLeadMinutes+"분 전 기상",14,false));
            button(peer.id.equals(link.musicOwner)?p.name+" 님이 음악을 제어해요":p.name+"에게 음악 제어 맡기기",()->link.musicOwner(peer.id));
            button(p.name+" 연결 해제",()->new AlertDialog.Builder(this).setTitle("이 휴대폰의 연결 승인을 해제할까요?").setMessage("휴대폰에 이미 예약된 기상 알림은 유지됩니다.").setPositiveButton("승인 해제",(d,w)->link.revoke(peer.id)).setNegativeButton("취소",null).show());
        }
        if(!peers.isEmpty())button("공용 환경 제안 확인",()->{
            List<RestProfile> list=new ArrayList<>();for(CompanionManager.Peer p:peers)list.add(p.profile);CabinPlan plan=new CabinPlan(list);
            String message="풍량은 가장 약한 요청인 "+plan.fan+"단계를 제안해요.\n"+(plan.musicConflict?"음악 요청이 달라 무음을 제안해요. 음악 대표가 최종 선택할 수 있어요.":"동승자들의 소리 선택이 같아요.")+"\n좌석별 온도 요청은 각 동승자 카드에서 확인해 주세요.\n차량에 자동으로 적용하지 않고 선호 설정에 저장합니다.";
            new AlertDialog.Builder(this).setTitle("함께 사용하는 환경 제안").setMessage(message).setPositiveButton("선호 설정에 저장",(d,w)->link.applyCabinDraft()).setNegativeButton("닫기",null).show();
        });
    }
    private void phone(){
        if(!link.paired()){
            button(link.busy?"연결 확인 중…":"차량 주소로 연결",this::connect).setEnabled(!link.busy&&!link.hostRunning);
            if(link.busy)button("연결 취소",link::stopLink);
        }else{
            if(!link.clientRunning)button("차량에 다시 연결",link::resumeClient);
            button("내 좌석·온도·음악 설정",()->{c.selected.mode(RestMode.PASSENGER_RIDE);c.saveProfile();finish();});
            button("내 설정을 차량으로 보내기",link::sendProfile).setEnabled(!link.busy&&link.clientRunning);
            JSONObject state=link.remote;
            if(state!=null){
                try{
                    RestProfile p=c.store.profile(state.getJSONObject("profile"));
                    note("차량이 받은 내 설정",p.name+" · "+p.seat.label+"\n"+p.temperature+"°C · 풍량 "+p.fan+" · "+p.soundName());
                    if(p.arrivalEnabled)body.addView(text("도착 "+time(p.arrivalWall)+" · "+p.wakeLeadMinutes+"분 전 기상",15,false));
                    if(state.optLong("tripArrival")>state.optLong("now")){
                        body.addView(text("차량 도착 "+time(state.optLong("tripArrival"))+" · "+state.optString("arrivalSource"),15,false));
                        for(int lead:new int[]{1,5,10,15})button("차량 도착 "+lead+"분 전 깨우기",()->link.useCarArrival(lead)).setEnabled(!link.busy&&link.clientRunning);
                    }
                    note(state.optBoolean("resting")?"동승자 휴식 중":"휴식 대기 또는 종료","내 기상 예약  "+time(link.personalWake()));
                    if(!c.alarms.notificationsAvailable())body.addView(text("개인 기상 알림을 받으려면 이 휴대폰의 알림을 허용해 주세요.",15,true));
                    if(Build.VERSION.SDK_INT>=31&&!getSystemService(AlarmManager.class).canScheduleExactAlarms())body.addView(text("정확한 알람 권한이 없어 절전 중 기상 안내가 늦어질 수 있어요.",15,true));
                    button("동승자 휴식 시작",this::startRemote).setEnabled(!link.busy&&link.clientRunning&&!state.optBoolean("resting"));
                    button("동승자 휴식 마치기",()->link.clientOp("finish",new JSONObject())).setEnabled(!link.busy&&link.clientRunning&&state.optBoolean("resting"));
                    if(state.optBoolean("musicOwner")){
                        note("내가 맡은 차량 음악",state.optString("carTrack")+"\n"+state.optString("carAudio"));
                        for(String[] action:new String[][]{{"play","차량 음악 재생"},{"pause","차량 음악 일시정지"},{"next","차량 다음 곡"},{"stop","차량 음악 끄기"}})button(action[1],()->request("audio","command",action[0])).setEnabled(!link.busy&&link.clientRunning&&state.optBoolean("resting"));
                    }else note("차량 음악 제어","차량에서 음악 제어를 맡긴 동승자만 차량 스피커의 음악을 바꿀 수 있어요.");
                    if(state.optLong("started")>0&&!state.optBoolean("resting")){
                        note("이번 휴식은 어땠나요","평가는 다음 선호 설정을 제안하는 데 사용해요.");
                        for(String feedback:new String[]{"편안했어요","추웠어요","더웠어요","소리가 거슬렸어요","자세가 불편했어요"})button(feedback,()->request("feedback","feedback",feedback)).setEnabled(!link.busy&&link.clientRunning);
                        FeedbackAdvice advice=FeedbackAdvice.from(p,state.optString("feedback"));if(advice!=null){body.addView(text(advice.reason,15,false));button("다음 설정 추천 적용",link::acceptFeedback).setEnabled(!link.busy&&link.clientRunning);}
                    }
                }catch(JSONException invalid){note("설정 확인 필요","차량에 다시 연결해 주세요.");}
            }
            button("연결 종료",link::stopLink);
            button("이 차량 연결 정보 지우기",()->new AlertDialog.Builder(this).setTitle("저장된 차량 연결을 지울까요?").setMessage("이미 예약된 개인 기상 알림은 유지돼요.").setPositiveButton("지우기",(d,w)->link.forget()).setNegativeButton("취소",null).show());
        }
        note("이 휴대폰의 개인 기상",time(link.personalWake())+"\n차량 연결이 끊겨도 마지막 기상 예약은 유지돼요.");
        android.os.Vibrator vibrator=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);
        body.addView(text(vibrator!=null&&vibrator.hasVibrator()?"개인 기상은 휴대폰의 진동 알림을 사용해요. 방해 금지·알림·배터리 설정에 따라 전달이 달라질 수 있어요.":"이 기기는 진동을 지원하지 않아 화면 알림으로 안내해요.",14,false));
        button("이 휴대폰 기상 예약 해제",link::cancelPersonalWake);
        button("기상 알림 허용",()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},83);else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));});
        if(Build.VERSION.SDK_INT>=31&&!getSystemService(AlarmManager.class).canScheduleExactAlarms())button("정확한 기상 알림 허용",()->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName()))));
    }
    private void request(String op,String key,String value){try{link.clientOp(op,new JSONObject().put(key,value));}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    private void startRemote(){
        if(!c.alarms.notificationsAvailable()){
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},83);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            toast("알림을 허용한 뒤 휴식 시작을 다시 눌러 주세요.");return;
        }
        link.clientOp("start",new JSONObject());
    }
    private void navigation(){
        try{android.content.pm.PermissionInfo info=getPackageManager().getPermissionInfo("pleos.car.permission.NAVI_ROUTE",0);if((info.protectionLevel&android.content.pm.PermissionInfo.PROTECTION_MASK_BASE)==android.content.pm.PermissionInfo.PROTECTION_DANGEROUS&&checkSelfPermission(info.name)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{info.name},84);return;}}catch(PackageManager.NameNotFoundException ignored){}
        link.startNavigation();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==84&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)link.startNavigation();render();}
    private void connect(){
        EditText address=new EditText(this);address.setSingleLine(true);address.setHint("예: 192.168.1.10:44389");address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("차량에 표시된 주소").setView(address).setPositiveButton("차량 확인",null).setNegativeButton("취소",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=address.getText().toString().trim();try{new LocalEndpoint(value);}catch(IllegalArgumentException invalid){address.setError(invalid.getMessage());return;}dialog.dismiss();link.probe(value,new CompanionManager.Result<String>(){
            @Override public void ok(String cert){if(isFinishing())return;new AlertDialog.Builder(CompanionActivity.this).setTitle("차량 인증 코드를 비교해 주세요").setMessage(LocalTls.display(cert)+"\n\n실제 차량 화면의 인증 코드와 같은지 확인해 주세요. 다르면 연결하지 마세요.").setPositiveButton("차량과 같아요",(a,b)->code(value,cert)).setNegativeButton("취소",null).show();}
            @Override public void fail(String reason){toast(reason);}
        });}));dialog.show();
    }
    private void code(String address,String cert){
        EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});input.setHint("차량의 연결 코드 6자리");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("차량 연결 코드").setView(input).setPositiveButton("연결 요청",null).setNegativeButton("취소",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String pin=input.getText().toString();if(!pin.matches("[0-9]{6}")){input.setError("6자리 코드를 입력해 주세요.");return;}dialog.dismiss();link.pair(address,cert,pin);}));dialog.show();
    }
    private interface ArrivalChoice{void selected(long wall);}
    private void arrival(long initial,ArrivalChoice chosen){
        Calendar date=Calendar.getInstance();date.setTimeInMillis(initial>System.currentTimeMillis()?initial:System.currentTimeMillis()+30*60_000);
        new TimePickerDialog(this,(view,hour,minute)->{try{chosen.selected(WakePlan.nextTime(System.currentTimeMillis(),hour,minute,TimeZone.getDefault()));}catch(IllegalArgumentException invalid){toast(invalid.getMessage());}},date.get(Calendar.HOUR_OF_DAY),date.get(Calendar.MINUTE),true).show();
    }
    private void toast(String value){if(!isFinishing())Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}

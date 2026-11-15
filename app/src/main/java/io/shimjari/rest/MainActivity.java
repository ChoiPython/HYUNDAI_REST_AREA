package io.shimjari.rest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import io.shimjari.rest.core.RestProfile;
import io.shimjari.rest.core.RestSession;
import io.shimjari.rest.core.RestMode;
import io.shimjari.rest.core.WakePlan;
import io.shimjari.rest.core.FeedbackAdvice;
import io.shimjari.rest.core.SeatId;
import io.shimjari.rest.core.VehicleSnapshot;
import io.shimjari.rest.core.MusicTrack;
import io.shimjari.rest.core.MusicContext;
import io.shimjari.rest.music.MusicController;
import io.shimjari.rest.music.MusicRepository;
import io.shimjari.rest.vehicle.DemoVehicleBridge;
import io.shimjari.rest.vehicle.VehicleBridge;
import io.shimjari.rest.vehicle.DrivingUxMonitor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntConsumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Setup on an allowed display, with separate passenger-trip and parked-driver sessions. */
public final class MainActivity extends Activity implements RestController.Observer {
    private UiStyle ui;
    private int BG,SURFACE,INK,MUTED,ACCENT,SOFT,GREEN;
    private RestController c;
    private LinearLayout root, content;
    private TextView remainingLabel;
    private ScrollView activeScroll;
    private String renderedScreen="";
    private int restoreScrollY;
    private String page = "home";
    private DrivingUxMonitor uxMonitor;
    private boolean drivingRestricted;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (c != null) {
                c.refresh();
                if (remainingLabel != null && c.session != null && c.session.phase == RestSession.Phase.RESTING) remainingLabel.setText(duration(c.remaining()));
            }
            tick.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui=new UiStyle(this);BG=ui.background;SURFACE=ui.surface;INK=ui.text;MUTED=ui.muted;ACCENT=ui.accent;SOFT=ui.soft;GREEN=ui.muted;
        c = ((RestApp) getApplication()).controller();
        if (saved != null) page = saved.getString("page", "home");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        render();
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)){
            try{uxMonitor=new DrivingUxMonitor(this,restricted->{runOnUiThread(()->{drivingRestricted=restricted;if(restricted)c.drivingRestriction();render();});});}
            catch(Exception|LinkageError unavailable){drivingRestricted=true;render();}
        }
    }
    @Override protected void onStart() { super.onStart(); c.observe(this); c.visible(true); tick.post(ticker); render(); }
    @Override protected void onStop() { tick.removeCallbacks(ticker); c.remove(this); c.visible(false); super.onStop(); }
    @Override protected void onDestroy(){if(uxMonitor!=null)uxMonitor.close();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(c!=null)c.focused(hasFocus);}
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("page", page); super.onSaveInstanceState(state); }
    @Override public void changed() { render(); }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, int color, boolean bold) {
        return ui.text(value,size,color,bold);
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void space(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private void gap(LinearLayout parent, int width) { parent.addView(new View(this), new LinearLayout.LayoutParams(dp(width), 1)); }
    private Button button(String label, boolean primary, Runnable click) {
        boolean onDark=c!=null&&c.session!=null&&c.session.phase!=RestSession.Phase.REVIEW;
        return ui.button(label,primary,onDark,click);
    }
    private LinearLayout card() {
        LinearLayout card = column(); card.setPadding(dp(22), dp(18), dp(22), dp(18)); card.setBackground(shape(SURFACE, 18)); return card;
    }
    private void label(LinearLayout parent, String title, String caption) {
        parent.addView(text(title, 18, INK, true)); if (caption != null) { space(parent, 4); parent.addView(text(caption, 13, MUTED, false)); }
    }
    private void nav(String key, String title, String icon, LinearLayout rail, boolean wide) {
        Button b = button((wide ? icon + "   " : "") + title, page.equals(key), () -> { page = key; render(); });
        b.setTextSize(wide?15:13);if(!wide){b.setPadding(dp(4),dp(8),dp(4),dp(8));b.setSingleLine(true);} LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(wide ? -1 : 0, dp(54));
        if (wide) p.bottomMargin = dp(10); else { p.weight = 1; p.setMargins(dp(3), 0, dp(3), 0); }
        rail.addView(b, p);
    }
    private void render() {
        if (c == null || isFinishing()) return;
        String screenKey=drivingRestricted?"restricted":c.session==null?"page:"+page:"session:"+c.session.id+":"+c.session.phase;
        restoreScrollY=screenKey.equals(renderedScreen)&&activeScroll!=null?(activeScroll.isLaidOut()?activeScroll.getScrollY():restoreScrollY):0;
        renderedScreen=screenKey;activeScroll=null;
        remainingLabel = null;
        if(drivingRestricted){renderRestricted();return;}
        if (c.session != null) { renderSession(); return; }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams window = getWindow().getAttributes(); window.screenBrightness = -1; getWindow().setAttributes(window);
        boolean wide = getResources().getConfiguration().screenWidthDp >= 800;
        root = wide ? row() : column(); root.setBackgroundColor(BG); root.setGravity(Gravity.TOP);
        if (wide) {
            LinearLayout rail = column(); rail.setPadding(dp(18), dp(30), dp(18), dp(20));
            LinearLayout brand = row(); brand.addView(new MoonView(false), new LinearLayout.LayoutParams(dp(36), dp(36)));
            gap(brand, 8); brand.addView(text("쉼, 자리", 24, INK, true)); rail.addView(brand); space(rail, 7);
            rail.addView(text("당신을 위한 작은 쉼", 11, MUTED, false)); space(rail, 40);
            nav("home", "휴식 준비", "◌", rail, true); nav("profiles", "나의 프로필", "○", rail, true);
            nav("music", "오늘의 음악", "♫", rail, true);
            nav("history", "쉼의 기록", "◷", rail, true); nav("settings", "설정", "⋯", rail, true);
            View filler = new View(this); rail.addView(filler, new LinearLayout.LayoutParams(1, 0, 1));
            rail.addView(text("천천히, 편안하게.\nSHIMJARI", 12, MUTED, false));
            root.addView(rail, new LinearLayout.LayoutParams(dp(190), -1));
        } else {
            LinearLayout header = row(); header.setPadding(dp(20), dp(12), dp(20), 0); header.addView(text("쉼, 자리", 25, INK, true)); root.addView(header);
        }
        ScrollView scroll = new ScrollView(this); activeScroll=scroll;scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = column(); content.setPadding(dp(wide ? 18 : 20), dp(26), dp(wide ? 28 : 20), dp(24)); scroll.addView(content);
        root.addView(scroll, wide ? new LinearLayout.LayoutParams(0, -1, 1) : new LinearLayout.LayoutParams(-1, 0, 1));
        if (!wide) {
            LinearLayout bar = row(); bar.setPadding(dp(8), dp(8), dp(8), dp(8));
            nav("home", "휴식", "", bar, false); nav("profiles", "프로필", "", bar, false);
            nav("music", "음악", "", bar, false);
            nav("history", "기록", "", bar, false); nav("settings", "설정", "", bar, false); root.addView(bar);
        }
        if (page.equals("profiles")) profiles(); else if(page.equals("music"))music();else if (page.equals("history")) history(); else if (page.equals("settings")) settings(); else home(wide);
        applyRoot(root);
    }
    private void applyRoot(LinearLayout view) {
        int left=view.getPaddingLeft(),top=view.getPaddingTop(),right=view.getPaddingRight(),bottom=view.getPaddingBottom();
        setContentView(view);
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(left+insets.getSystemWindowInsetLeft(),top+insets.getSystemWindowInsetTop(),right+insets.getSystemWindowInsetRight(),bottom+insets.getSystemWindowInsetBottom());return insets;
        });
        if(activeScroll!=null)ui.restoreScroll(activeScroll,restoreScrollY);
        ui.systemBars(c.session!=null&&c.session.phase!=RestSession.Phase.REVIEW);
    }
    private void pageHeader(String eyebrow, String title) {
        LinearLayout top=row();
        TextView section=text(eyebrow,12,MUTED,true);top.addView(section,new LinearLayout.LayoutParams(0,-2,1));gap(top,12);
        TextView badge=text(c.vehicle.isDemo()?"체험 모드":"차량 연결",12,ACCENT,true);
        badge.setPadding(dp(10),dp(7),dp(10),dp(7));badge.setBackground(shape(SOFT,8));
        badge.setOnClickListener(v->{page="settings";render();});top.addView(badge);content.addView(top);space(content,12);
        content.addView(text(title,getResources().getConfiguration().screenWidthDp>=800?28:25,INK,true));space(content,22);
    }

    private void home(boolean wide) {
        pageHeader("휴식 준비", "편안한 쉼을 시작해요");
        boolean passenger=c.selected.mode==RestMode.PASSENGER_RIDE;
        LinearLayout modes=card();label(modes,"누가 쉬고 있나요?",null);space(modes,14);
        LinearLayout modeRow=row();
        for(RestMode mode:RestMode.values()){
            Button choice=button(mode==RestMode.PASSENGER_RIDE?"동승자":"운전자",c.selected.mode==mode,()->{c.selected.mode(mode);c.saveProfile();});choice.setSingleLine(true);choice.setContentDescription(mode.label+" · "+mode.description);
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1);if(mode==RestMode.PASSENGER_RIDE)mp.rightMargin=dp(8);modeRow.addView(choice,mp);
        }
        modes.addView(modeRow);content.addView(modes);space(content,14);
        content.addView(button("휴대폰과 차량 연결",false,()->startActivity(new Intent(this,CompanionActivity.class))));space(content,14);
        LinearLayout hero=row();hero.setPadding(dp(18),dp(18),dp(18),dp(18));hero.setBackground(shape(SOFT,16));
        hero.addView(new MoonView(false),new LinearLayout.LayoutParams(dp(48),dp(48)));gap(hero,16);
        LinearLayout words=column();words.addView(text(passenger?"이동 중에도 편안하게":"주차한 뒤, 잠시 쉬어가요",18,INK,true));space(words,6);
        words.addView(text(passenger?"음악과 기상 시간을 준비해요.":"P단에 주차한 뒤 휴식을 시작해요.",14,MUTED,false));
        hero.addView(words,new LinearLayout.LayoutParams(0,-2,1));content.addView(hero);space(content,18);

        LinearLayout profile = card(); LinearLayout title = row(); LinearLayout who = column(); label(who, c.selected.name, "휴식할 자리를 선택해 주세요");
        title.addView(who, new LinearLayout.LayoutParams(0, -2, 1));gap(title,10);Button change=button("변경",false,()->chooseProfile());ui.compact(change,14);title.addView(change,new LinearLayout.LayoutParams(dp(56),-2)); profile.addView(title); space(profile, 14);
        LinearLayout seats = row();
        for (SeatId seat : SeatId.values()) {
            if(!c.selected.mode.accepts(seat))continue;
            Button b = button(seat.label.replace("뒷좌석 ", "뒤 "), seat == c.selected.seat, () -> { c.selected.seat = seat; c.saveProfile(); });
            b.setTextSize(wide ? 14 : 12); b.setPadding(dp(6), dp(8), dp(6), dp(8));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(0, 0, dp(6), 0); seats.addView(b, p);
        }
        profile.addView(seats); content.addView(profile); space(content, 14);
        if(passenger){
            LinearLayout arrival=card();label(arrival,"도착 전에 깨워 주세요",c.selected.arrivalEnabled?"직접 입력한 도착 "+clock(c.selected.arrivalWall)+" · "+c.selected.wakeLeadMinutes+"분 전 기상":"직접 입력하거나 휴대폰 연결 화면에서 차량 내비게이션을 연동해요.");space(arrival,10);
            arrival.addView(button("도착 예상 시각 설정",false,()->chooseArrival(c.selected,()->c.saveProfile())));space(arrival,9);
            LinearLayout leads=row();for(int lead:new int[]{1,5,10,15}){Button b=button(lead+"분 전",c.selected.wakeLeadMinutes==lead,()->{c.selected.wakeLeadMinutes=lead;c.saveProfile();});ui.compact(b,13);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(lead!=15)p.rightMargin=dp(5);leads.addView(b,p);}arrival.addView(leads);
            if(c.selected.arrivalEnabled){space(arrival,8);arrival.addView(button("휴식 시간 기준으로 바꾸기",false,()->{c.selected.arrivalEnabled=false;c.saveProfile();}));}
            content.addView(arrival);space(content,14);
        }
        FeedbackAdvice advice=FeedbackAdvice.from(c.selected,c.store.lastFeedback(c.selected));
        if(advice!=null){LinearLayout suggestion=card();label(suggestion,"지난 휴식을 바탕으로 제안해요",advice.reason);space(suggestion,10);suggestion.addView(button("추천 설정을 선호에 저장",false,()->{c.selected.temperature=advice.profile.temperature;c.selected.sound=advice.profile.sound;c.saveProfile();}));content.addView(suggestion);space(content,14);}

        LinearLayout environment = wide ? row() : column();
        LinearLayout thermal = card(); label(thermal, "편안한 온도와 바람", passenger?"출발 전 준비할 선호 · 주행 중 자동 변경 없음":"선호 설정 · 실제 적용 전 차량 지원 확인"); space(thermal, 12);
        valueControl(thermal, "온도", String.format(Locale.KOREA, "%.1f°C", c.selected.temperature), () -> adjustTemperature(-0.5f), () -> adjustTemperature(0.5f));
        space(thermal, 7); valueControl(thermal, "풍량", c.selected.fan + "단계", () -> adjustFan(-1), () -> adjustFan(1));
        LinearLayout rest = card(); label(rest, "얼마나 쉬어갈까요", passenger&&c.selected.arrivalEnabled?"현재 도착 전 기상 예약이 휴식 시간보다 우선해요":passenger?"도착 시간을 생각해 직접 정해 주세요":"기상 후 준비할 시간도 생각해 주세요"); space(rest, 12);
        valueControl(rest, "휴식 시간", c.selected.minutes + "분", () -> adjustMinutes(-1), () -> adjustMinutes(1)); space(rest, 9);
        timeStepControl(rest,"15분",15);space(rest,8);timeStepControl(rest,"1시간",60);

        if (wide) {
            environment.addView(thermal,new LinearLayout.LayoutParams(0,-2,1)); gap(environment,14); environment.addView(rest,new LinearLayout.LayoutParams(0,-2,1));
        } else { environment.addView(thermal); space(environment,14); environment.addView(rest); }
        content.addView(environment); space(content,14);
        LinearLayout sound = card(); label(sound,"이번 쉼의 소리","소리는 선택 사항이에요. 다른 탑승자와 함께 결정해 주세요."); space(sound,12);
        String[] sounds={"조용히","잔잔한 선율","빗소리","추천 음악"};
        choiceGrid(sound,sounds,c.selected.sound,wide?4:2,choice->{if(choice==3&&c.selected.music.isEmpty()){page="music";render();return;}c.selected.sound=choice;c.saveProfile();},false);

        space(sound,12);sound.addView(button("오늘의 상황으로 음악 추천받기",false,()->{page="music";render();}));
        if(c.selected.sound==3&&!c.selected.music.isEmpty()){space(sound,9);MusicTrack t=c.selected.music.get(0);sound.addView(text(t.title+" — "+t.artist+" 외 "+(c.selected.music.size()-1)+"곡 · Audius",13,ACCENT,false));}
        content.addView(sound);space(content,18);
        Button start=button(c.busy?"환경을 확인하고 있어요…":passenger?"동승자 휴식 시작   →":"주차 후 운전자 휴식 시작   →",true,this::preview); start.setEnabled(!c.busy);content.addView(start,new LinearLayout.LayoutParams(-1,-2));space(content,12);
        String note=c.vehicle.isDemo()?"체험 모드에서는 실제 차량을 변경하지 않아요.":"공조 설정은 P단·정차 확인 후 지원되는 항목에만 적용해요.";
        content.addView(text(note,12,MUTED,false));
        if(!c.message.isEmpty()){space(content,8);content.addView(text(c.message,13,GREEN,false));}
    }
    private void valueControl(LinearLayout parent,String label,String value,Runnable minus,Runnable plus){
        LinearLayout line=row(),valueColumn=column();valueColumn.addView(text(label,13,MUTED,false));space(valueColumn,5);
        TextView number=text(value,24,INK,true);number.setSingleLine(true);valueColumn.addView(number);
        line.addView(valueColumn,new LinearLayout.LayoutParams(0,-2,1));gap(line,12);
        Button left=button("−",false,minus),right=button("+",false,plus);
        left.setContentDescription(label+" 줄이기");right.setContentDescription(label+" 늘리기");
        left.setPadding(0,0,0,0);right.setPadding(0,0,0,0);left.setTextSize(24);right.setTextSize(24);
        line.addView(left,new LinearLayout.LayoutParams(dp(48),dp(52)));gap(line,8);line.addView(right,new LinearLayout.LayoutParams(dp(48),dp(52)));parent.addView(line);
    }

    private void timeStepControl(LinearLayout parent,String label,int step){
        LinearLayout control=row();control.setBackground(shape(SOFT,12));
        Button minus=button("−",false,()->adjustMinutes(-step)),plus=button("+",false,()->adjustMinutes(step));
        minus.setContentDescription(label+" 줄이기");plus.setContentDescription(label+" 늘리기");
        minus.setPadding(0,0,0,0);plus.setPadding(0,0,0,0);minus.setTextSize(22);plus.setTextSize(22);
        control.addView(minus,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView caption=text(label,14,INK,true);caption.setGravity(Gravity.CENTER);control.addView(caption,new LinearLayout.LayoutParams(0,dp(52),1));
        control.addView(plus,new LinearLayout.LayoutParams(dp(52),dp(52)));parent.addView(control,new LinearLayout.LayoutParams(-1,-2));
    }
    private void adjustTemperature(float delta){c.selected.temperature=Math.max(16,Math.min(30,c.selected.temperature+delta));c.saveProfile();}
    private void adjustFan(int delta){c.selected.fan=Math.max(1,Math.min(8,c.selected.fan+delta));c.saveProfile();}
    private void adjustMinutes(int delta){c.selected.minutes=Math.max(1,Math.min(180,c.selected.minutes+delta));c.saveProfile();}
    private String clock(long wall){return new SimpleDateFormat("a h:mm",Locale.KOREA).format(new Date(wall));}
    private void chooseArrival(RestProfile profile,Runnable saved){
        java.util.Calendar date=java.util.Calendar.getInstance();date.setTimeInMillis(profile.arrivalWall>System.currentTimeMillis()?profile.arrivalWall:System.currentTimeMillis()+30*60_000L);
        new TimePickerDialog(this,(view,hour,minute)->{try{long wall=WakePlan.nextTime(System.currentTimeMillis(),hour,minute,java.util.TimeZone.getDefault());WakePlan.deadline(System.currentTimeMillis(),wall,profile.wakeLeadMinutes);if(c.session!=null&&profile==c.session.profile){c.arrival(wall,profile.wakeLeadMinutes);return;}profile.arrivalWall=wall;profile.arrivalEnabled=true;saved.run();}catch(IllegalArgumentException invalid){toast(invalid.getMessage());}},date.get(java.util.Calendar.HOUR_OF_DAY),date.get(java.util.Calendar.MINUTE),true).show();
    }
    private void choiceGrid(LinearLayout parent,String[] labels,int selected,int columns,IntConsumer choose,boolean disabled){
        LinearLayout line=null;
        for(int i=0;i<labels.length;i++){
            if(i%columns==0){line=row();parent.addView(line);if(i>0){LinearLayout.LayoutParams rowParams=(LinearLayout.LayoutParams)line.getLayoutParams();rowParams.topMargin=dp(8);}}
            final int value=i;Button item=button(labels[i],i==selected,()->choose.accept(value));ui.compact(item,14);item.setEnabled(!disabled);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(i%columns!=columns-1)params.rightMargin=dp(8);line.addView(item,params);
        }
        if(line!=null)for(int i=labels.length%columns;i>0&&i<columns;i++)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
    }
    private void musicChoices(LinearLayout parent,String[] labels,int selected,IntConsumer choose){
        boolean wide=getResources().getConfiguration().screenWidthDp>=800;
        int columns=wide?labels.length:labels.length==3?3:2;
        if(!wide&&getResources().getConfiguration().fontScale>1.15f)columns=2;
        choiceGrid(parent,labels,selected,columns,value->{choose.accept(value);c.music.edited();},c.music.busy());
    }
    private void music(){
        MusicController m=c.music;MusicContext context=m.context;
        pageHeader("오늘의 음악","지금 어울리는 음악");
        LinearLayout situation=card();label(situation,"지금은 어떻게 쉬고 싶나요?","원하는 기분을 직접 골라 주세요.\n통화 내용은 수집하지 않아요.");space(situation,14);
        musicChoices(situation,new String[]{"잠들기","쉬기","기분 전환"},context.purpose.ordinal(),i->context.purpose=MusicContext.Purpose.values()[i]);space(situation,10);
        label(situation,"지금의 기분","최근 대화 후의 느낌도 여기에서 선택할 수 있어요.");space(situation,10);
        musicChoices(situation,new String[]{"선택 안 함","차분함","긴장됨","지침","들뜸"},context.feeling.ordinal(),i->context.feeling=MusicContext.Feeling.values()[i]);space(situation,12);
        label(situation,"오늘의 날씨",m.weatherNote);space(situation,10);
        musicChoices(situation,new String[]{"선택 안 함","맑음","흐림","비","눈"},context.weather.ordinal(),i->{context.weather=MusicContext.Weather.values()[i];m.weatherNote="직접 선택한 날씨예요.";});
        Button weather=button(m.loadingWeather?"지역 예보를 가져오고 있어요…":"지역을 골라 날씨 가져오기",false,()->new AlertDialog.Builder(this).setTitle("어느 지역에서 쉬고 있나요?").setItems(MusicRepository.CITIES,(d,i)->m.weather(i)).setNegativeButton("취소",null).show());weather.setEnabled(!m.busy());situation.addView(weather);space(situation,9);
        situation.addView(text("선택한 도시의 예보를 사용해요. GPS 위치를 수집하지 않아요.",12,MUTED,false));space(situation,15);
        label(situation,"선호하는 장르","직접 고른 장르를 날씨보다 우선해요.");space(situation,10);
        String[] genres={"","Ambient","Lo-Fi","Jazz","Classical"};int selected=0;for(int i=0;i<genres.length;i++)if(genres[i].equals(context.preferredGenre))selected=i;
        musicChoices(situation,new String[]{"분위기에 맞게","앰비언트","로파이","재즈","클래식"},selected,i->context.preferredGenre=genres[i]);
        content.addView(situation);space(content,15);
        LinearLayout service=card();label(service,"공개 음악 서비스 Audius","추천한 음악은 쉼, 자리 안에서 재생해요. 멜론의 곡 목록과는 달라요.");space(service,8);
        service.addView(text("검색할 때 음악 장르·분위기 태그가 Audius로 전달돼요.\n기분 선택 자체와 통화·연락처 정보는 보내지 않아요. 음악 재생에는 인터넷이 필요해요.",13,MUTED,false));space(service,14);
        Button search=button(m.loadingMusic?"어울리는 음악을 찾고 있어요…":"오늘의 음악 추천받기",true,m::search);search.setEnabled(!m.busy());service.addView(search);content.addView(service);space(content,15);
        if(!m.reason.isEmpty()){content.addView(text(m.reason,14,ACCENT,false));space(content,10);}
        if(!m.note.isEmpty()){content.addView(text(m.note,14,INK,false));space(content,14);}
        for(int i=0;i<m.results.size();i++){
            final int selectedIndex=i;MusicTrack t=m.results.get(i);LinearLayout track=card();
            label(track,t.title,t.artist+" · Audius");space(track,8);
            String info=t.genre+(!t.mood.isEmpty()?" · "+t.mood:"")+" · "+(t.seconds/60)+":"+String.format(Locale.US,"%02d",t.seconds%60);
            track.addView(text(info,13,MUTED,false));space(track,12);
            track.addView(button("이 곡부터 휴식 음악으로 선택",true,()->{
                c.selected.music.clear();for(int k=0;k<m.results.size();k++)c.selected.music.add(m.results.get((selectedIndex+k)%m.results.size()));
                c.selected.sound=3;page="home";c.saveProfile();toast("휴식을 시작하면 선택한 음악 목록을 순서대로 재생해요.");
            }));
            if(!t.permalink.isEmpty()){space(track,8);track.addView(button("Audius에서 곡 보기",false,()->openWeb(t.pageUrl())));}
            content.addView(track);space(content,12);
        }
        space(content,10);content.addView(text("곡의 공개 장르·무드 태그와 선택한 조건을 비교해 추천해요.\n음악 출처: Audius 및 각 아티스트. 날씨: Open-Meteo 모델 예보(CC BY 4.0).",12,MUTED,false));space(content,8);
        content.addView(button("날씨 데이터 출처·라이선스",false,()->openWeb("https://open-meteo.com/en/terms")));
    }
    private void openWeb(String address){
        android.net.Uri uri=android.net.Uri.parse(address);
        if(!"https".equals(uri.getScheme())||(!"audius.co".equals(uri.getHost())&&!"open-meteo.com".equals(uri.getHost())))return;
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception unavailable){toast("이 기기에서 링크를 열 수 있는 앱이 없어요.");}
    }
    private void chooseProfile(){
        String[] names=new String[c.profiles.size()];for(int i=0;i<names.length;i++)names[i]=c.profiles.get(i).name;
        new AlertDialog.Builder(this).setTitle("누가 쉬어가나요?").setItems(names,(d,i)->c.select(c.profiles.get(i)))
            .setNeutralButton("새 프로필",(d,w)->editProfile(null)).setNegativeButton("닫기",null).show();
    }
    private void profiles(){
        pageHeader("나의 프로필", "내게 맞는 쉼");
        for(RestProfile p:c.profiles){
            LinearLayout card=card();label(card,p.name,p.mode.label+" · "+p.seat.label+" · "+p.temperature+"°C · "+p.minutes+"분 · "+p.soundName());space(card,14);
            LinearLayout actions=row();
            Button select=button(p==c.selected?"사용 중":"선택",p==c.selected,()->{c.select(p);page="home";render();});
            Button rename=button("이름 변경",false,()->editProfile(p));ui.compact(select,13);ui.compact(rename,13);
            actions.addView(select,new LinearLayout.LayoutParams(0,-2,1));gap(actions,8);actions.addView(rename,new LinearLayout.LayoutParams(0,-2,1));
            if(c.profiles.size()>1){gap(actions,8);Button remove=button("삭제",false,()->new AlertDialog.Builder(this).setTitle("프로필을 삭제할까요?").setMessage("이 프로필의 선호 설정이 삭제돼요. 지난 휴식 기록은 유지돼요.").setPositiveButton("삭제",(d,w)->{c.profiles.remove(p);if(c.selected==p)c.selected=c.profiles.get(0);c.saveProfile();}).setNegativeButton("취소",null).show());ui.compact(remove,13);actions.addView(remove,new LinearLayout.LayoutParams(0,-2,1));}

            card.addView(actions);content.addView(card);space(content,12);
        }
        content.addView(button("+ 새 프로필 만들기",true,()->editProfile(null)));space(content,15);
        content.addView(text("프로필 설정은 휴식 준비 화면에서 바꿀 수 있어요.\n모든 프로필은 이 기기에만 저장돼요.",13,MUTED,false));
    }
    private void editProfile(RestProfile profile){
        if(profile==null&&c.profiles.size()>=8){toast("최대 8개의 프로필을 저장할 수 있어요.");return;}
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(profile==null?"":profile.name);input.setHint("별명을 입력해 주세요");input.setPadding(dp(24),dp(16),dp(24),dp(16));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(profile==null?"새로운 쉼의 주인공":"프로필 이름 변경").setView(input).setPositiveButton("저장",null).setNegativeButton("취소",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();if(name.isEmpty()||name.length()>16){input.setError("1~16자의 별명을 입력해 주세요.");return;}
            if(profile==null){RestProfile p=new RestProfile(UUID.randomUUID().toString(),name,SeatId.PASSENGER,22f,1,25,0);c.profiles.add(p);c.selected=p;}else profile.name=name;
            c.saveProfile();dialog.dismiss();
        }));dialog.show();
    }
    private void history(){
        pageHeader("쉼의 기록","쌓여가는 나의 쉼");JSONArray rows=c.store.history();
        if(rows.length()==0){LinearLayout empty=card();empty.addView(new MoonView(false),new LinearLayout.LayoutParams(-1,dp(140)));label(empty,"첫 번째 쉼을 기다리고 있어요","휴식을 마치면 느낌과 사용한 환경을 기록해 드려요.");space(empty,18);empty.addView(button("휴식 준비하기",true,()->{page="home";render();}));content.addView(empty);return;}
        for(int i=0;i<rows.length();i++){
            JSONObject j=rows.optJSONObject(i);if(j==null)continue;LinearLayout card=card();
            String when=new SimpleDateFormat("M월 d일  HH:mm",Locale.KOREA).format(new Date(j.optLong("start")));
            label(card,j.optString("profile")+" · "+j.optString("feedback"),when+(j.optBoolean("demo")?" · 체험 기록":" · 차량 연결 기록"));space(card,9);
            String modeLabel=RestMode.restore(j.optString("mode"),j.optString("seat").equals(SeatId.DRIVER.label)?SeatId.DRIVER:SeatId.PASSENGER).label;
            card.addView(text(modeLabel+" · "+j.optString("seat")+"   "+j.optDouble("temperature")+"°C   풍량 "+j.optInt("fan"),14,INK,false));space(card,10);
            if(j.optString("feedback").equals("추웠어요"))card.addView(text("다음에는 선호 온도를 조금 높여 보세요.",13,GREEN,false));
            if(j.optString("feedback").equals("더웠어요"))card.addView(text("다음에는 선호 온도를 조금 낮춰 보세요.",13,GREEN,false));
            card.addView(button("환경 적용 내역",false,()->new AlertDialog.Builder(this).setTitle("환경 적용과 복원 내역").setMessage(j.optString("report")).setPositiveButton("닫기",null).show()));
            content.addView(card);space(content,12);
        }
        content.addView(text("기록은 사용자가 남긴 휴식 느낌이며 수면 단계 측정 결과가 아니에요.",12,MUTED,false));
    }
    private void settings(){
        pageHeader("설정","쉼, 자리 설정");
        LinearLayout connection=card();label(connection,"차량 연결",c.vehicle.isDemo()?"현재: 체험 모드 · 실제 차량 변경 없음":"현재: Pleos 연결 모드 · 기능별 응답 확인 필요");space(connection,14);
        connection.addView(text("자동 공조에는 차량 속도 조회 권한이 필요해요.\n시스템에서 위치 권한으로 표시될 수 있지만 GPS 좌표는 읽지 않아요.",13,MUTED,false));space(connection,12);
        connection.addView(button("Pleos 차량에 연결",true,this::connectPleos));space(connection,8);connection.addView(button("체험 모드로 사용",false,()->c.connect(true,new VehicleBridge.Result<VehicleSnapshot>(){
            @Override public void ok(VehicleSnapshot s){toast("체험 모드로 바꿨어요.");}@Override public void fail(String reason){message(reason);}
        })));content.addView(connection);space(content,14);
        LinearLayout alarm=card();label(alarm,"기상 알림",c.alarms.notificationsAvailable()?"기상 알림이 허용되어 있어요.":"앱을 벗어나도 알림을 받으려면 알림을 허용해 주세요.");space(alarm,12);
        alarm.addView(button("알림 설정",false,this::notificationSettings));space(alarm,8);
        alarm.addView(text(c.alarms.exactAvailable()?"정확한 시간의 알림을 사용할 수 있어요.":"절전 중 알림이 지연될 수 있어요. 정확한 알림 권한을 설정할 수 있어요.",13,MUTED,false));
        if(Build.VERSION.SDK_INT>=31&&!c.alarms.exactAvailable()){space(alarm,8);alarm.addView(button("정확한 알림 허용",false,()->{try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("기기 설정에서 알람 권한을 확인해 주세요.");}}));}
        content.addView(alarm);space(content,14);
        LinearLayout privacy=card();label(privacy,"내 기기에 저장하는 프로필과 기록","연락처·GPS 위치·마이크·통화 내용을 수집하지 않아요.\n선택한 음악·지역 예보를 조회할 때 Audius·Open-Meteo에 연결해요.\n동승자 음악은 알림과 함께 배경에서 이어지고, 운전자 음악은 화면을 벗어나면 멈춰요.");space(privacy,12);
        privacy.addView(button("휴식 기록 지우기",false,()->new AlertDialog.Builder(this).setTitle("모든 휴식 기록을 지울까요?").setMessage("삭제한 기록은 되돌릴 수 없어요. 프로필은 유지돼요.").setPositiveButton("기록 삭제",(d,w)->{c.store.clearHistory();toast("휴식 기록을 지웠어요.");}).setNegativeButton("취소",null).show()));content.addView(privacy);space(content,14);
        content.addView(button("휴대폰과 차량 연결",false,()->startActivity(new Intent(this,CompanionActivity.class))));space(content,12);
        content.addView(text("쉼, 자리 1.3.1\n도착 전 개인 기상과 같은 네트워크의 휴대폰 연결을 지원해요.\n차량 내비게이션 연동은 서비스와 권한이 지원되는 경우에 사용할 수 있어요.\n운전자 화면 제한을 해제하거나 좌석을 자동 이동하지 않아요.\n습도 제어·수면 단계 판정은 제공하지 않아요.",13,MUTED,false));
        if(c.vehicle instanceof DemoVehicleBridge){space(content,18);content.addView(button("체험 환경: "+(((DemoVehicleBridge)c.vehicle).simulatedParked?"주차 중":"주행 중"),false,()->{DemoVehicleBridge d=(DemoVehicleBridge)c.vehicle;d.simulatedParked=!d.simulatedParked;render();}));}
    }
    private void connectPleos(){
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
            && checkSelfPermission("android.car.permission.CAR_SPEED")!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{"android.car.permission.CAR_SPEED"},10);return;
        }
        connectPleosNow();
    }
    private void connectPleosNow(){
        c.connect(false,new VehicleBridge.Result<VehicleSnapshot>(){
            @Override public void ok(VehicleSnapshot s){showSnapshot(s);}
            @Override public void fail(String reason){message(reason);}
        });
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==10){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED)connectPleosNow();
            else message("차량 속도 권한이 없어 정차 상태를 확인할 수 없어요. 자동 공조 제어 없이 체험 모드와 타이머를 사용할 수 있어요.");
        }
        render();
    }
    private void notificationSettings(){
        if(Build.VERSION.SDK_INT>=33&&!c.alarms.notificationsAvailable())requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9);
        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    private void preview(){
        if(c.selected.mode==RestMode.PASSENGER_RIDE){
            RestProfile p=c.selected;
            String change=p.name+" · "+p.seat.label+" · "+p.minutes+"분\n소리: "+p.soundName()+"\n\n운전 중인 사람은 이용할 수 없어요.\n공용 스피커 사용은 함께 탄 사람과 먼저 결정해 주세요.\n음악은 다른 화면에서도 이어지고 종료 시 조용히 안내해요.\n주행 중 공조와 좌석은 자동으로 변경하지 않아요.";
            if(!c.alarms.notificationsAvailable())change+="\n\n알림이 꺼져 있어 화면을 벗어나면 종료 안내를 놓칠 수 있어요.";
            new AlertDialog.Builder(this).setTitle("동승자 휴식을 시작할까요?").setMessage(change)
                .setPositiveButton("음악·타이머 시작",(d,w)->c.begin(new VehicleSnapshot(),false,false))
                .setNeutralButton("출발 전 공조도 준비",(d,w)->previewEnvironment()).setNegativeButton("취소",null).show();
            return;
        }
        previewEnvironment();
    }
    private void previewEnvironment(){
        c.preview(new VehicleBridge.Result<VehicleSnapshot>(){
            @Override public void ok(VehicleSnapshot snapshot){
                RestProfile p=c.selected;
                boolean driver=p.mode==RestMode.DRIVER_PARKED;
                String change=(c.vehicle.isDemo()?"체험 환경에 적용해요. 실제 차량은 바뀌지 않아요.\n\n":"P단과 정차 상태를 확인한 뒤 지원되는 항목만 적용해요.\n\n")
                    +p.name+" · "+p.seat.label+" · "+p.minutes+"분\n온도: "+(snapshot.acceptsTemperature(p.temperature)?snapshot.temperature+" → "+p.temperature+"°C":"지원 범위 확인 필요 · 현재 설정 유지")
                    +"\n풍량: "+(snapshot.acceptsFan(p.fan)?snapshot.fan+" → "+p.fan+"단계":"지원 범위 확인 필요 · 현재 설정 유지")+"\n소리: "+p.soundName()
                    +"\n\n공용 공조와 스피커는 다른 탑승자에게도 영향을 줄 수 있어요.";
                if(!c.alarms.notificationsAvailable())change+="\n알림이 꺼져 있어요. 앱을 열어 두거나 설정에서 알림을 허용해 주세요.";
                if(!c.alarms.exactAvailable())change+="\n절전 중에는 기상 알림이 지연될 수 있어요.";
                if(driver)change+="\n\n운전을 마치고 주차한 경우에만 시작해 주세요. 정차 확인이 불가능하면 공조 없이 타이머만 사용해요.";
                new AlertDialog.Builder(MainActivity.this).setTitle("이번 쉼을 준비할까요?").setMessage(change)
                    .setPositiveButton(driver?"주차했고 환경 적용 후 시작":"환경 적용 후 시작",(d,w)->c.begin(snapshot,true,driver))
                    .setNeutralButton(driver?"주차했고 타이머만 시작":"공조 없이 시작",(d,w)->c.begin(snapshot,false,driver)).setNegativeButton("취소",null).show();
            }
            @Override public void fail(String reason){boolean driver=c.selected.mode==RestMode.DRIVER_PARKED;new AlertDialog.Builder(MainActivity.this).setTitle("차량 환경을 확인하지 못했어요").setMessage(reason+"\n환경 변경 없이 타이머를 시작할 수 있어요."+(driver?"\n실제로 주차한 경우에만 확인해 주세요.":"")).setPositiveButton(driver?"주차했고 타이머 시작":"공조 없이 시작",(d,w)->c.begin(new VehicleSnapshot(),false,driver)).setNegativeButton("닫기",null).show();}
        });
    }
    private void showSnapshot(VehicleSnapshot s){new AlertDialog.Builder(this).setTitle("차량에서 확인한 환경").setMessage("온도: "+(s.temperature==null?"확인되지 않음":s.temperature+"°C")+"\n"+s.temperatureNote+"\n\n풍량: "+(s.fan==null?"확인되지 않음":s.fan)+"\n"+s.fanNote).setPositiveButton("닫기",null).show();}
    private void renderSession(){
        RestSession s=c.session;boolean review=s.phase==RestSession.Phase.REVIEW;
        boolean passenger=s.profile.mode==RestMode.PASSENGER_RIDE;
        if(passenger)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams window=getWindow().getAttributes();window.screenBrightness=review||passenger?-1:0.12f;getWindow().setAttributes(window);
        LinearLayout screen=column();screen.setBackgroundColor(review?BG:0xFF1C1D1F);
        ScrollView scroll=new ScrollView(this);activeScroll=scroll;scroll.setFillViewport(true);LinearLayout body=column();body.setGravity(Gravity.CENTER_HORIZONTAL);body.setPadding(dp(28),dp(28),dp(28),dp(24));scroll.addView(body);screen.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        int foreground=review?INK:0xFFF6F7F9,secondary=review?MUTED:0xFFBCC0C8;
        TextView brand=text("쉼, 자리   ·   "+(s.demo?"체험 모드":"차량 연결 모드"),13,secondary,false);brand.setGravity(Gravity.CENTER);body.addView(brand);space(body,22);
        if(review){
            body.addView(new MoonView(false),new LinearLayout.LayoutParams(dp(90),dp(90)));space(body,14);
            TextView reviewTitle=text("이번 쉼은 어땠나요?",28,INK,true);reviewTitle.setGravity(Gravity.CENTER);body.addView(reviewTitle);space(body,8);TextView reviewNote=text("당신에게 편안한 환경을 조금씩 알아가요.",15,MUTED,false);reviewNote.setGravity(Gravity.CENTER);body.addView(reviewNote);space(body,24);
            for(String feedback:new String[]{"편안했어요","추웠어요","더웠어요","소리가 거슬렸어요","자세가 불편했어요"}){Button b=button(feedback,feedback.equals("편안했어요"),()->{c.feedback(feedback);page="history";render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(Math.min(dp(330),getResources().getDisplayMetrics().widthPixels-dp(56)),-2);p.bottomMargin=dp(9);body.addView(b,p);}
            space(body,12);Button skip=button("평가 없이 기록하기",false,()->{c.feedback("평가하지 않음");page="history";render();});body.addView(skip);
            space(body,20);TextView report=text(s.report,12,MUTED,false);body.addView(report,new LinearLayout.LayoutParams(-1,-2));
        }else{
            body.addView(new MoonView(true),new LinearLayout.LayoutParams(dp(116),dp(116)));space(body,18);
            String heading=s.phase==RestSession.Phase.AWAKE?(passenger?"휴식 시간이 끝났어요":"일어날 시간이에요"):s.phase==RestSession.Phase.PREPARING?"편안한 쉼을 준비하고 있어요":s.phase==RestSession.Phase.FINISHING?"환경을 확인하고 있어요":passenger?"동승자가 쉬고 있어요":"지금은, 편히 쉬어가세요";
            TextView title=text(heading,27,foreground,true);title.setGravity(Gravity.CENTER);body.addView(title);space(body,12);
            remainingLabel=text(s.phase==RestSession.Phase.RESTING?duration(c.remaining()):s.phase==RestSession.Phase.AWAKE?"좋은 쉼이었나요":"잠시만요",s.phase==RestSession.Phase.RESTING?76:32,foreground,true);remainingLabel.setGravity(Gravity.CENTER);remainingLabel.setSingleLine(true);remainingLabel.setAutoSizeTextTypeUniformWithConfiguration(32,76,2,android.util.TypedValue.COMPLEX_UNIT_SP);body.addView(remainingLabel,new LinearLayout.LayoutParams(-1,dp(100)));space(body,8);
            String detail=s.phase==RestSession.Phase.RESTING?new SimpleDateFormat("a h:mm",Locale.KOREA).format(new Date(s.endWall))+(passenger?"에 음악을 멈추고 조용히 알려드릴게요":"에 깨워드릴게요"):"천천히 몸을 깨우고, 준비되면 휴식을 마쳐 주세요.";
            TextView sub=text(detail,15,secondary,false);sub.setGravity(Gravity.CENTER);body.addView(sub);space(body,24);
            TextView settings=text(s.profile.mode.label+"   ·   "+s.profile.name+"   ·   "+s.profile.seat.label+"   ·   "+s.profile.soundName(),14,secondary,false);settings.setGravity(Gravity.CENTER);body.addView(settings);space(body,10);
            TextView env=text(passenger?"주행 중에는 공조와 좌석을 자동으로 바꾸지 않아요":"선호 설정   "+s.profile.temperature+"°C   /   풍량 "+s.profile.fan+"단계",13,secondary,false);env.setGravity(Gravity.CENTER);body.addView(env);space(body,20);
            if(s.phase==RestSession.Phase.RESTING && s.profile.sound>0){
                TextView now=text((c.sounds.title().isEmpty()?s.profile.soundName():c.sounds.title())+"\n"+c.sounds.status(),14,secondary,false);now.setGravity(Gravity.CENTER);body.addView(now);space(body,10);
                LinearLayout controls=row();Button pause=button("음악 일시정지",false,()->c.audioCommand("pause")),restart=button("다시 재생",false,()->c.audioCommand("play"));
                controls.addView(pause,new LinearLayout.LayoutParams(0,-2,1));gap(controls,8);controls.addView(restart,new LinearLayout.LayoutParams(0,-2,1));body.addView(controls,new LinearLayout.LayoutParams(-1,-2));space(body,8);
                if(s.profile.sound==3){body.addView(button("다음 곡",false,()->c.audioCommand("next")));space(body,8);}
                body.addView(text(passenger?"종료 30초 전부터 소리가 줄어들어요. 다른 화면에서도 음악이 이어져요.":"종료 30초 전부터 소리가 줄어들어요. 앱을 벗어나면 음악을 멈춰요.",12,secondary,false));space(body,16);
                if(passenger){body.addView(button("다른 화면으로 이동",false,()->moveTaskToBack(true)));space(body,14);}
            }
            if(!c.busy){
                if(s.phase==RestSession.Phase.RESTING){Button extend=button(s.profile.mode==RestMode.PASSENGER_RIDE&&s.profile.arrivalEnabled?"도착 예상 시각 변경":"5분 더 쉬기",false,()->{if(s.profile.mode==RestMode.PASSENGER_RIDE&&s.profile.arrivalEnabled)chooseArrival(s.profile,()->{try{c.arrival(s.profile.arrivalWall,s.profile.wakeLeadMinutes);}catch(IllegalArgumentException invalid){toast(invalid.getMessage());}});else c.extend();});body.addView(extend,new LinearLayout.LayoutParams(Math.min(dp(330),getResources().getDisplayMetrics().widthPixels-dp(56)),-2));space(body,10);}
                Button finish=button("휴식 마치기",true,c::finish);body.addView(finish,new LinearLayout.LayoutParams(Math.min(dp(330),getResources().getDisplayMetrics().widthPixels-dp(56)),-2));space(body,14);
                Button report=button("환경 적용 내역 보기",false,()->new AlertDialog.Builder(this).setTitle("적용 요청과 확인 내역").setMessage(s.report).setPositiveButton("닫기",null).show());body.addView(report);
            }
            space(body,16);TextView note=text("좌석은 자동으로 움직이지 않아요.",12,secondary,false);note.setGravity(Gravity.CENTER);body.addView(note);
        }
        applyRoot(screen);
    }
    private void renderRestricted(){
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams window=getWindow().getAttributes();window.screenBrightness=-1;getWindow().setAttributes(window);
        LinearLayout screen=column();screen.setGravity(Gravity.CENTER);screen.setPadding(dp(30),dp(30),dp(30),dp(30));screen.setBackgroundColor(BG);
        TextView title=text(c.passengerAudioSession()?"동승자 휴식이 이어지고 있어요":"차량의 화면 제한이 적용 중이에요",26,INK,true);title.setGravity(Gravity.CENTER);screen.addView(title);space(screen,18);
        TextView note=text("상세 설정은 주차 후 또는 사용이 허용된 승객 화면에서 해 주세요.\n음악은 차량 미디어 제어로 조작할 수 있어요.",16,MUTED,false);note.setGravity(Gravity.CENTER);screen.addView(note);
        applyRoot(screen);
    }
    private String duration(long millis){long seconds=(millis+999)/1000;return String.format(Locale.KOREA,"%02d:%02d",seconds/60,seconds%60);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void message(String value){new AlertDialog.Builder(this).setTitle("연결 안내").setMessage(value).setPositiveButton("닫기",null).show();}

    private final class MoonView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final boolean night;
        private final RectF arc=new RectF();
        MoonView(boolean night){super(MainActivity.this);this.night=night;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f;
            p.setStyle(Paint.Style.FILL);p.setColor(night?0xFF303236:ui.border);canvas.drawCircle(cx,cy,size*0.46f,p);
            p.setColor(night?0xFFE1E4EB:0xFF737D8F);canvas.drawCircle(cx-size*0.02f,cy+size*0.01f,size*0.28f,p);
            p.setColor(night?0xFF303236:ui.border);canvas.drawCircle(cx+size*0.12f,cy-size*0.08f,size*0.26f,p);
            p.setColor(night?0xFFBCC0C8:0xFF737D8F);canvas.drawCircle(cx+size*0.29f,cy-size*0.24f,size*0.025f,p);canvas.drawCircle(cx-size*0.30f,cy-size*0.26f,size*0.015f,p);
            arc.set(cx-size*0.37f,cy-size*0.38f,cx+size*0.37f,cy+size*0.38f);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(size*0.013f);p.setColor(night?0xFF737D8F:0xFFA9B0BD);canvas.drawArc(arc,32,62,false,p);p.setStyle(Paint.Style.FILL);
        }
    }
}

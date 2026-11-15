package io.shimjari.rest.companion;

import android.content.*;
import android.os.*;
import io.shimjari.rest.*;
import io.shimjari.rest.core.*;
import io.shimjari.rest.vehicle.NaviEtaBridge;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** All mutations run on the main thread; transport threads only deliver framed TLS messages. */
public final class CompanionManager {
    public static final class Peer {
        public String id,tokenHash,feedback="";
        public RestProfile profile;
        public long started,deadline,lastSeen;
        public int revision;
        public boolean resting,wakeDue;
    }
    public static final class Pending {
        public String id,name,peerId="",token="";
        public long until;
        public int decision;
    }
    public interface Result<T>{void ok(T value);void fail(String reason);}
    private final Context context;
    private final RestController rest;
    private final Runnable changed;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService requests=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,Peer> peers=new LinkedHashMap<>();
    private final LinkedHashMap<String,Pending> pending=new LinkedHashMap<>();
    private final PersonalWake wake;
    public volatile boolean hostRunning,clientRunning;
    public boolean busy;
    public String status="",fingerprint="",pin="",addresses="",musicOwner="";
    public long pairingUntil,tripArrival;
    public boolean navigationEnabled;
    public String navigationStatus="내비게이션 연동 전",arrivalSource="직접 입력";
    private NaviEtaBridge navigation;
    private int attempts;
    private volatile boolean cancelPair;
    private String clientAddress="",clientPin="",clientId="",clientToken="";
    public JSONObject remote;

    public CompanionManager(Context context,RestController rest,Runnable changed){
        this.context=context;this.rest=rest;this.changed=changed;prefs=context.getSharedPreferences("companion",Context.MODE_PRIVATE);wake=new PersonalWake(context);wake.restore();
        try{
            JSONArray array=new JSONArray(prefs.getString("peers","[]"));
            for(int i=0;i<Math.min(3,array.length());i++){
                JSONObject j=array.getJSONObject(i);Peer p=new Peer();p.id=j.getString("id");p.tokenHash=j.getString("hash");p.profile=rest.store.profile(j.getJSONObject("profile"));
                p.started=j.optLong("started");p.deadline=j.optLong("deadline");p.resting=j.optBoolean("resting")&&p.deadline>System.currentTimeMillis();p.wakeDue=j.optBoolean("wakeDue")||(j.optBoolean("resting")&&!p.resting);p.feedback=j.optString("feedback");p.revision=j.optInt("revision");peers.put(p.id,p);
            }
            musicOwner=prefs.getString("musicOwner","");tripArrival=prefs.getLong("tripArrival",0);arrivalSource=prefs.getString("arrivalSource","직접 입력");
            clientAddress=prefs.getString("address","");clientPin=prefs.getString("fingerprint","");clientId=prefs.getString("clientId","");
            if(!clientId.isEmpty())clientToken=LinkSecrets.open(prefs.getString("clientSecret",""));
        }catch(Exception invalid){clientAddress="";clientId="";clientToken="";status="저장된 연결을 복구하지 못했어요. 다시 페어링해 주세요.";}
    }
    public List<Peer> peers(){return new ArrayList<>(peers.values());}
    public List<Pending> pending(){List<Pending> list=new ArrayList<>();for(Pending p:pending.values())if(p.decision==0&&p.until>System.currentTimeMillis())list.add(p);return list;}
    public boolean paired(){return !clientId.isEmpty()&&!clientToken.isEmpty();}
    public long personalWake(){return wake.when();}
    public void cancelPersonalWake(){wake.suppress();status="이 휴대폰의 현재 기상 예약을 해제했어요.";changed.run();}
    private void save(){
        try{
            JSONArray array=new JSONArray();for(Peer p:peers.values())array.put(new JSONObject().put("id",p.id).put("hash",p.tokenHash).put("profile",rest.store.profileJson(p.profile)).put("started",p.started).put("deadline",p.deadline).put("resting",p.resting).put("wakeDue",p.wakeDue).put("feedback",p.feedback).put("revision",p.revision));
            prefs.edit().putString("peers",array.toString()).putString("musicOwner",musicOwner).putLong("tripArrival",tripArrival).putString("arrivalSource",arrivalSource).apply();
        }catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    public void startHost(){
        if(clientRunning){status="휴대폰 연결을 종료한 뒤 차량 연결을 열어 주세요.";changed.run();return;}
        pin=String.format(Locale.ROOT,"%06d",new SecureRandom().nextInt(1_000_000));pairingUntil=System.currentTimeMillis()+120_000;attempts=0;pending.clear();
        status="차량 연결을 준비하고 있어요.";changed.run();startService("host");
    }
    public void resumeClient(){if(paired()){status="차량에 다시 연결하고 있어요.";changed.run();startService("phone");}}
    private void startService(String role){try{context.startForegroundService(new Intent(context,CompanionService.class).setAction(role));}catch(RuntimeException denied){status="앱 화면을 연 상태에서 연결을 다시 시작해 주세요.";changed.run();}}
    public void stopLink(){cancelPair=true;context.stopService(new Intent(context,CompanionService.class));status="연결을 종료했어요. 이미 예약한 개인 기상 알림은 유지돼요.";changed.run();}
    public void serverReady(String identity,String address){fingerprint=identity;addresses=address;hostRunning=true;status="같은 Wi-Fi나 핫스폿에서 휴대폰을 연결해 주세요.";changed.run();}
    public void stopped(){hostRunning=false;clientRunning=false;stopNavigation();changed.run();}
    public void error(String reason){status=reason;changed.run();}
    public void approve(String id,boolean accept){
        Pending q=pending.get(id);if(q==null||q.until<System.currentTimeMillis())return;
        if(!accept||peers.size()>=3){q.decision=-1;changed.run();return;}
        SeatId seat=null;for(SeatId candidate:new SeatId[]{SeatId.PASSENGER,SeatId.REAR_LEFT,SeatId.REAR_RIGHT}){boolean occupied=false;for(Peer p:peers.values())if(p.profile.seat==candidate)occupied=true;if(!occupied){seat=candidate;break;}}
        if(seat==null){q.decision=-1;changed.run();return;}
        try{
            Peer p=new Peer();p.id=UUID.randomUUID().toString();q.peerId=p.id;q.token=LocalTls.random();p.tokenHash=LocalTls.hash(q.token.getBytes(StandardCharsets.UTF_8));p.profile=new RestProfile("peer:"+p.id,q.name,seat,22,1,25,0);peers.put(p.id,p);q.decision=1;save();changed.run();
        }catch(Exception e){q.decision=-1;status="연결 승인을 저장하지 못했어요.";changed.run();}
    }
    public void revoke(String id){peers.remove(id);if(id.equals(musicOwner))musicOwner="";save();changed.run();}
    public void musicOwner(String id){if(!peers.containsKey(id))return;musicOwner=id;save();changed.run();}
    public void tripArrival(long arrival){
        long now=System.currentTimeMillis();WakePlan.deadline(now,arrival,1);
        for(Peer p:peers.values())if(p.resting&&p.profile.arrivalEnabled)WakePlan.deadline(now,arrival,p.profile.wakeLeadMinutes);
        if(rest.session!=null&&rest.session.phase==RestSession.Phase.RESTING&&rest.session.profile.arrivalEnabled&&rest.session.profile.id.startsWith("peer:")&&WakePlan.deadline(now,arrival,rest.session.profile.wakeLeadMinutes)-rest.session.startWall>WakePlan.MAX_REST)throw new IllegalArgumentException("한 번의 휴식은 최대 3시간이에요.");
        stopNavigation();arrivalSource="직접 입력";tripArrival=arrival;for(Peer p:peers.values())if(p.profile.arrivalEnabled){p.profile.arrivalWall=arrival;if(p.resting)p.deadline=WakePlan.deadline(now,arrival,p.profile.wakeLeadMinutes);p.revision++;}
        if(rest.session!=null&&rest.session.profile.arrivalEnabled&&rest.session.profile.id.startsWith("peer:"))rest.arrival(arrival,rest.session.profile.wakeLeadMinutes);
        save();changed.run();
    }
    public void applyCabinDraft(){
        List<RestProfile> list=new ArrayList<>();for(Peer p:peers.values())list.add(p.profile);
        CabinPlan plan=new CabinPlan(list);rest.selected.mode(RestMode.PASSENGER_RIDE);rest.selected.fan=plan.fan;
        if(plan.sound==3&&!list.isEmpty()){rest.selected.music.clear();rest.selected.music.addAll(list.get(0).music);}rest.selected.sound=plan.sound;rest.saveProfile();
        status="공용 풍량·소리 제안을 선호에 저장했어요. 차량에는 아직 적용하지 않았어요.";changed.run();
    }
    public void expire(){
        long now=System.currentTimeMillis();boolean modified=false;
        for(Peer p:peers.values())if(p.resting&&p.deadline<=now){p.resting=false;p.wakeDue=true;p.revision++;modified=true;}
        pending.values().removeIf(p->p.until<now);
        if(modified){save();changed.run();}
    }
    public boolean hostIdle(){if(System.currentTimeMillis()<pairingUntil)return false;long now=SystemClock.elapsedRealtime();for(Peer p:peers.values())if(p.lastSeen>0&&now-p.lastSeen<90_000)return false;return true;}
    private static JSONObject object(){return new JSONObject();}
    private static JSONObject ok()throws JSONException{return object().put("ok",true);}
    public JSONObject handle(JSONObject q){
        try{
            expire();String op=q.getString("op");long now=System.currentTimeMillis();
            if("pair".equals(op)){
                if(now>pairingUntil||attempts>=5||peers.size()>=3)throw new IllegalArgumentException("접수 중인 연결 코드가 없어요. 차량에서 다시 열어 주세요.");
                String provided=q.optString("pin");
                if(!MessageDigest.isEqual(pin.getBytes(StandardCharsets.US_ASCII),provided.getBytes(StandardCharsets.US_ASCII))){attempts++;throw new IllegalArgumentException("연결 코드를 확인해 주세요.");}
                String name=q.optString("name").trim();if(name.isEmpty()||name.length()>16||name.matches(".*[\\p{Cntrl}].*"))throw new IllegalArgumentException("별명은 1~16자로 입력해 주세요.");
                if(pending.size()>=3)throw new IllegalArgumentException("대기 중인 연결을 먼저 확인해 주세요.");
                Pending p=new Pending();p.id=LocalTls.random();p.name=name;p.until=now+120_000;pending.put(p.id,p);changed.run();return ok().put("pending",p.id);
            }
            if("pairStatus".equals(op)){
                Pending p=pending.get(q.optString("pending"));if(p==null||p.until<now||p.decision<0)throw new IllegalArgumentException("연결이 거절되었거나 만료됐어요.");
                JSONObject result=ok().put("approved",p.decision==1);
                if(p.decision==1)result.put("id",p.peerId).put("token",p.token);return result;
            }
            Peer p=peers.get(q.optString("id"));String token=q.optString("token");
            if(p==null||token.length()!=64||!MessageDigest.isEqual(p.tokenHash.getBytes(StandardCharsets.US_ASCII),LocalTls.hash(token.getBytes(StandardCharsets.UTF_8)).getBytes(StandardCharsets.US_ASCII)))return object().put("ok",false).put("revoked",true).put("error","차량 연결 승인이 필요해요.");
            p.lastSeen=SystemClock.elapsedRealtime();
            if("profile".equals(op)){
                RestProfile incoming=rest.store.profile(q.getJSONObject("profile"));
                if(incoming.mode!=RestMode.PASSENGER_RIDE||!incoming.mode.accepts(incoming.seat)||incoming.name.length()>16||incoming.name.matches(".*[\\p{Cntrl}].*"))throw new IllegalArgumentException("동승자 좌석과 별명을 확인해 주세요.");
                for(Peer other:peers.values())if(other!=p&&other.profile.seat==incoming.seat)throw new IllegalArgumentException("이미 다른 동승자가 선택한 좌석이에요.");
                RestProfile corrected=new RestProfile("peer:"+p.id,incoming.name,incoming.seat,incoming.temperature,incoming.fan,incoming.minutes,incoming.sound);
                corrected.music.addAll(incoming.music);corrected.arrivalEnabled=incoming.arrivalEnabled;corrected.arrivalWall=incoming.arrivalWall;corrected.wakeLeadMinutes=incoming.wakeLeadMinutes;
                if(corrected.arrivalEnabled&&tripArrival>now)corrected.arrivalWall=tripArrival;
                if(p.resting){if(corrected.arrivalEnabled)p.deadline=WakePlan.deadline(now,corrected.arrivalWall,corrected.wakeLeadMinutes);else if(p.profile.arrivalEnabled)throw new IllegalArgumentException("진행 중인 도착 전 기상은 종료 후 변경해 주세요.");}
                p.profile=corrected;p.revision++;save();changed.run();
            }else if("start".equals(op)){
                if(p.resting)return ok().put("state",state(p));
                p.deadline=p.profile.arrivalEnabled?WakePlan.deadline(now,p.profile.arrivalWall,p.profile.wakeLeadMinutes):now+p.profile.minutes*60_000L;
                p.started=now;p.resting=true;p.wakeDue=false;p.revision++;save();
                if(p.id.equals(musicOwner)&&p.profile.sound>0&&rest.session==null)rest.beginPassengerFromPhone(p.profile);
                changed.run();
            }else if("finish".equals(op)){
                p.resting=false;p.wakeDue=false;p.deadline=0;p.revision++;save();if(rest.session!=null&&rest.session.profile.id.equals(p.profile.id))rest.finish();changed.run();
            }else if("feedback".equals(op)){
                String feedback=q.optString("feedback");if(!Arrays.asList("편안했어요","추웠어요","더웠어요","소리가 거슬렸어요","자세가 불편했어요").contains(feedback))throw new IllegalArgumentException("평가를 선택해 주세요.");
                if(p.started>0){RestSession record=new RestSession(p.id+":"+p.started,p.profile,rest.vehicle.isDemo());record.startWall=p.started;record.finishedWall=now;rest.store.addHistory(record,feedback);}
                p.feedback=feedback;p.revision++;save();changed.run();
            }else if("audio".equals(op)){
                if(!p.id.equals(musicOwner))throw new IllegalArgumentException("차량에서 음악 제어를 맡긴 동승자만 조작할 수 있어요.");
                if(!p.resting)throw new IllegalArgumentException("동승자 휴식을 먼저 시작해 주세요.");
                String command=q.optString("command");if(!Arrays.asList("play","pause","next","previous","stop").contains(command))throw new IllegalArgumentException("지원하는 재생 제어를 선택해 주세요.");
                if(rest.session==null){if(!"play".equals(command))throw new IllegalArgumentException("차량 음악을 먼저 재생해 주세요.");rest.beginPassengerFromPhone(p.profile);}
                else if(!rest.session.profile.id.equals(p.profile.id)||rest.session.phase!=RestSession.Phase.RESTING)throw new IllegalArgumentException("차량에서 다른 휴식을 사용 중이거나 이전 휴식을 마무리해야 해요.");
                else {if("play".equals(command))rest.remoteMusic(p.profile);rest.audioCommand(command);}
            }else if(!"state".equals(op))throw new IllegalArgumentException("지원하지 않는 연결 요청이에요.");
            return ok().put("state",state(p));
        }catch(Exception invalid){try{return object().put("ok",false).put("error",invalid instanceof IllegalArgumentException?invalid.getMessage():"연결 요청 내용을 확인해 주세요.");}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    }
    private JSONObject state(Peer p)throws JSONException{
        return object().put("revision",p.revision).put("profile",rest.store.profileJson(p.profile)).put("resting",p.resting).put("wakeDue",p.wakeDue).put("started",p.started).put("deadline",p.deadline).put("now",System.currentTimeMillis()).put("feedback",p.feedback)
            .put("tripArrival",tripArrival).put("arrivalSource",arrivalSource).put("musicOwner",p.id.equals(musicOwner)).put("carTrack",rest.sounds.title()).put("carAudio",rest.sounds.status());
    }
    public void probe(String address,Result<String> result){
        if(hostRunning){result.fail("차량 연결을 종료한 뒤 휴대폰으로 연결해 주세요.");return;}
        busy=true;status="차량 인증 정보를 확인하고 있어요.";changed.run();
        requests.execute(()->{try{LocalEndpoint endpoint=new LocalEndpoint(address);String certificate=LocalTls.probe(endpoint);main.post(()->{busy=false;result.ok(certificate);changed.run();});}catch(Exception e){main.post(()->{busy=false;status="차량에 연결하지 못했어요. 같은 네트워크와 주소를 확인해 주세요.";result.fail(status);changed.run();});}});
    }
    public void pair(String address,String certificate,String code){
        cancelPair=false;busy=true;status="차량에서 휴대폰 연결을 승인해 주세요.";changed.run();String name=rest.selected.name;
        requests.execute(()->{
            try{
                LocalEndpoint endpoint=new LocalEndpoint(address);JSONObject response=LocalTls.request(endpoint,certificate,object().put("op","pair").put("pin",code).put("name",name));check(response);
                String ticket=response.getString("pending");long until=SystemClock.elapsedRealtime()+115_000;
                while(!cancelPair&&SystemClock.elapsedRealtime()<until){
                    JSONObject answer=LocalTls.request(endpoint,certificate,object().put("op","pairStatus").put("pending",ticket));check(answer);
                    if(answer.optBoolean("approved")){
                        String id=answer.getString("id"),token=answer.getString("token"),sealed=LinkSecrets.seal(token);
                        main.post(()->{clientAddress=endpoint.toString();clientPin=certificate;clientId=id;clientToken=token;prefs.edit().putString("address",clientAddress).putString("fingerprint",clientPin).putString("clientId",id).putString("clientSecret",sealed).apply();busy=false;status="차량이 연결을 승인했어요.";changed.run();startService("phone");});return;
                    }
                    Thread.sleep(1000);
                }
                throw new IllegalArgumentException("연결 승인이 취소되었거나 만료됐어요.");
            }catch(Exception failure){main.post(()->{busy=false;status=failure instanceof IllegalArgumentException?failure.getMessage():"연결을 완료하지 못했어요. 인증 코드와 차량 승인을 확인해 주세요.";changed.run();});}
        });
    }
    private static void check(JSONObject result){if(!result.optBoolean("ok"))throw new IllegalArgumentException(result.optString("error","연결 요청을 완료하지 못했어요."));}
    public JSONObject poll()throws Exception{return authenticated("state",object());}
    private JSONObject authenticated(String op,JSONObject payload)throws Exception{
        payload.put("op",op).put("id",clientId).put("token",clientToken);JSONObject result=LocalTls.request(new LocalEndpoint(clientAddress),clientPin,payload);check(result);return result.getJSONObject("state");
    }
    public void receive(JSONObject state){
        if(!paired())return;
        if(remote!=null&&state.optInt("revision")<remote.optInt("revision"))return;
        if((state.optBoolean("resting")||state.optBoolean("wakeDue"))&&state.optLong("deadline")>0)wake.schedule(clientId+":"+state.optLong("started"),state.optLong("deadline"),state.optLong("now"));
        JSONObject comparable=new JSONObject();try{Iterator<String> keys=state.keys();while(keys.hasNext()){String key=keys.next();if(!"now".equals(key))comparable.put(key,state.get(key));}}catch(JSONException ignored){return;}
        String before=remote==null?"":withoutNow(remote);remote=state;clientRunning=true;
        if(!before.equals(comparable.toString())){status="차량과 연결되어 있어요.";changed.run();}
    }
    private static String withoutNow(JSONObject object){try{JSONObject value=new JSONObject(object.toString());value.remove("now");return value.toString();}catch(JSONException e){return "";}}
    public void clientOp(String op,JSONObject payload){
        if(!paired()||busy)return;busy=true;changed.run();
        requests.execute(()->{try{JSONObject result=authenticated(op,payload);main.post(()->{busy=false;receive(result);if("finish".equals(op))wake.cancel();status="차량에 반영했어요.";changed.run();});}catch(Exception e){main.post(()->{busy=false;status=e instanceof IllegalArgumentException?e.getMessage():"차량에 전달하지 못했어요. 연결을 확인해 주세요.";changed.run();});}});
    }
    public void sendProfile(){RestProfile profile=rest.selected.copy();profile.mode(RestMode.PASSENGER_RIDE);try{clientOp("profile",object().put("profile",rest.store.profileJson(profile)));}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    public void acceptFeedback(){
        if(remote==null)return;try{RestProfile profile=rest.store.profile(remote.getJSONObject("profile"));FeedbackAdvice advice=FeedbackAdvice.from(profile,remote.optString("feedback"));if(advice!=null)clientOp("profile",object().put("profile",rest.store.profileJson(advice.profile)));}catch(JSONException invalid){status="추천을 적용하지 못했어요.";changed.run();}
    }
    public void startNavigation(){
        if(!hostRunning){status="휴대폰 연결받기를 먼저 열어 주세요.";changed.run();return;}
        if(!context.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE)){status="내비게이션 연동은 Pleos 차량 기기에서 사용할 수 있어요.";changed.run();return;}
        stopNavigation();navigationEnabled=true;navigationStatus="NaviHelper 연결 중";
        try{navigation=new NaviEtaBridge(context,this::navigationArrival,value->{navigationStatus=value;changed.run();});}
        catch(RuntimeException|LinkageError unavailable){navigationEnabled=false;navigationStatus="내비게이션 권한 또는 서비스를 확인하지 못했어요. 직접 입력은 사용할 수 있어요.";}
        changed.run();
    }
    public void stopNavigation(){if(navigation!=null){navigation.close();navigation=null;}navigationEnabled=false;}
    public void pollNavigation(){if(navigation!=null)navigation.poll();}
    private void navigationArrival(int seconds){
        long now=System.currentTimeMillis(),arrival=now+seconds*1000L;
        try{
            for(Peer p:peers.values())if(p.resting&&p.profile.arrivalEnabled)WakePlan.automaticDeadline(now,arrival,p.profile.wakeLeadMinutes);
            if("내비게이션".equals(arrivalSource)&&Math.abs(arrival-tripArrival)<15000)return;
            tripArrival=arrival;arrivalSource="내비게이션";navigationStatus="현재 길안내의 남은 시간을 받았어요.";
            for(Peer p:peers.values())if(p.profile.arrivalEnabled){p.profile.arrivalWall=arrival;if(p.resting){p.deadline=WakePlan.automaticDeadline(now,arrival,p.profile.wakeLeadMinutes);if(p.deadline<=now){p.resting=false;p.wakeDue=true;}}p.revision++;}
            if(rest.session!=null&&rest.session.profile.id.startsWith("peer:")&&rest.session.profile.arrivalEnabled)rest.arrivalAutomatic(arrival,rest.session.profile.wakeLeadMinutes);
            save();changed.run();
        }catch(IllegalArgumentException invalid){navigationStatus="내비게이션 도착 시간이 기상 범위를 벗어나 마지막 예약을 유지해요.";changed.run();}
    }
    public void useCarArrival(int lead){
        if(remote==null)return;try{RestProfile profile=rest.store.profile(remote.getJSONObject("profile"));long arrival=remote.optLong("tripArrival");WakePlan.deadline(remote.optLong("now"),arrival,lead);profile.arrivalEnabled=true;profile.arrivalWall=arrival;profile.wakeLeadMinutes=lead;clientOp("profile",object().put("profile",rest.store.profileJson(profile)));}catch(Exception invalid){status="차량의 도착 시각 또는 기상 여유 시간을 확인해 주세요.";changed.run();}
    }
    public void forget(){stopLink();clientId="";clientToken="";remote=null;prefs.edit().remove("address").remove("fingerprint").remove("clientId").remove("clientSecret").apply();changed.run();}
}

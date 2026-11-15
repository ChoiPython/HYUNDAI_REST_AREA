package io.shimjari.rest.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import io.shimjari.rest.core.MusicContext;
import io.shimjari.rest.core.MusicTrack;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only official, public, read-only endpoints. No credentials or call/microphone data. */
public final class MusicRepository {
    public interface Result<T>{void ok(T value);void fail(String message);}
    public static final String[] CITIES={"서울","부산","대구","인천","광주","대전","울산","제주"};
    private static final String[] LAT={"37.5665","35.1796","35.8714","37.4563","35.1595","36.3504","35.5384","33.4996"};
    private static final String[] LON={"126.9780","129.0756","128.6014","126.7052","126.8526","127.3845","129.3114","126.5312"};
    private static final String UA="Shimjari/1.1 (+https://github.com/ChoiPython/HYUNDAI_REST_AREA)";
    private final ExecutorService network=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final SharedPreferences cache;
    public MusicRepository(Context context){cache=context.getSharedPreferences("music_public_cache",Context.MODE_PRIVATE);}
    private String encode(String value){try{return URLEncoder.encode(value,"UTF-8");}catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}}
    private JSONObject json(String key,String address,long lifetime)throws Exception{
        long now=System.currentTimeMillis(),age=now-cache.getLong(key+"_time",0);
        String saved=cache.getString(key,null);
        if(saved!=null && age>=0 && age<lifetime)return new JSONObject(saved);
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(12000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent",UA);connection.setRequestProperty("Accept","application/json");
        try{
            int status=connection.getResponseCode();
            if(status!=200)throw new IllegalStateException(status==429?"요청이 많아 잠시 쉬고 있어요. 잠시 후 다시 시도해 주세요.":"서비스에 연결하지 못했어요. (HTTP "+status+")");
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){if(output.size()+count>2*1024*1024)throw new IllegalStateException("응답이 너무 커서 중지했어요.");output.write(buffer,0,count);}
            }
            String raw=output.toString(StandardCharsets.UTF_8.name());JSONObject response=new JSONObject(raw);
            cache.edit().putString(key,raw).putLong(key+"_time",now).apply();return response;
        }finally{connection.disconnect();}
    }
    private List<MusicTrack> fetch(MusicContext context,boolean restrictMood)throws Exception{
        String key="tracks_"+context.genre()+"_"+(restrictMood?context.targetMood():"all");
        String address="https://api.audius.co/v1/tracks/search?app_name=Shimjari&limit=50&sort_method=popular&includePurchaseable=false&genre="+encode(context.genre())+(restrictMood?"&mood="+encode(context.targetMood()):"");
        JSONArray rows=json(key,address,10*60*1000L).optJSONArray("data");List<MusicTrack> found=new ArrayList<>();
        if(rows==null)return found;
        LinkedHashMap<String,MusicTrack> unique=new LinkedHashMap<>();
        for(int i=0;i<rows.length();i++)try{
            JSONObject t=rows.getJSONObject(i),access=t.optJSONObject("access");
            boolean conditions=t.has("stream_conditions")&&!t.isNull("stream_conditions");
            if(!MusicTrack.openStream(t.optBoolean("is_streamable",false),t.optBoolean("is_stream_gated",true),access!=null&&access.optBoolean("stream",false),conditions))continue;
            if(t.optBoolean("is_delete")||t.optBoolean("is_unlisted"))continue;
            JSONObject artist=t.optJSONObject("user");
            MusicTrack track=new MusicTrack(t.getString("id"),t.optString("title"),artist==null?"":artist.optString("name"),t.optString("genre"),t.isNull("mood")?"":t.optString("mood"),t.optString("permalink"),t.optDouble("bpm",0),t.optInt("duration"),t.optString("tags"));
            if(!track.title.isEmpty()&&!track.artist.isEmpty())unique.put(track.id,track);
        }catch(Exception ignored){/* A malformed or restricted item is never promoted into a playable result. */}
        found.addAll(unique.values());return context.rank(found);
    }
    public void search(MusicContext context,Result<List<MusicTrack>> result){
        MusicContext request=context.copy();
        network.execute(()->{
            try{
                List<MusicTrack> values=fetch(request,true);
                if(values.isEmpty())values=fetch(request,false);
                List<MusicTrack> selected=new ArrayList<>(values.subList(0,Math.min(5,values.size())));
                main.post(()->result.ok(selected));
            }catch(Exception error){main.post(()->result.fail("음악을 불러오지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."));}
        });
    }
    public static final class WeatherResult{
        public final MusicContext.Weather weather;public final String label;
        WeatherResult(MusicContext.Weather weather,String label){this.weather=weather;this.label=label;}
    }
    public void weather(int city,Result<WeatherResult> result){
        if(city<0||city>=CITIES.length){result.fail("지역을 선택해 주세요.");return;}
        network.execute(()->{
            try{
                String address="https://api.open-meteo.com/v1/forecast?latitude="+LAT[city]+"&longitude="+LON[city]+"&current=temperature_2m,weather_code&timezone=Asia%2FSeoul";
                JSONObject j=json("weather_"+city,address,15*60*1000L).getJSONObject("current");
                MusicContext.Weather weather=MusicContext.weatherCode(j.getInt("weather_code"));
                String label=CITIES[city]+" · "+j.getDouble("temperature_2m")+"°C · "+j.getString("time").replace('T',' ')+" 기준 모델 예보 · Open-Meteo";
                main.post(()->result.ok(new WeatherResult(weather,label)));
            }catch(Exception error){main.post(()->result.fail("날씨 연결에 실패했어요. 아래에서 날씨를 직접 선택해 주세요."));}
        });
    }
}

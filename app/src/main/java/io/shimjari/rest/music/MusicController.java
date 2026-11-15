package io.shimjari.rest.music;

import android.content.Context;
import io.shimjari.rest.core.MusicContext;
import io.shimjari.rest.core.MusicTrack;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class MusicController {
    public final MusicContext context=new MusicContext();
    public List<MusicTrack> results=new ArrayList<>();
    public boolean loadingMusic,loadingWeather;
    public String note="",weatherNote="날씨는 직접 고르거나 지역 예보를 가져올 수 있어요.",reason="";
    private final MusicRepository repository;
    private final Runnable changed;
    public MusicController(Context context,Runnable changed){repository=new MusicRepository(context);this.changed=changed;}
    public boolean busy(){return loadingMusic||loadingWeather;}
    public void search(){
        if(busy())return;
        context.hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);reason=context.reason();note="Audius에서 음악을 찾고 있어요…";results=new ArrayList<>();loadingMusic=true;changed.run();
        repository.search(context,new MusicRepository.Result<List<MusicTrack>>(){
            @Override public void ok(List<MusicTrack> tracks){results=tracks;loadingMusic=false;note=tracks.isEmpty()?"이 조건으로 재생할 수 있는 곡을 찾지 못했어요. 장르를 바꿔 보세요.":"공개 재생이 허용된 곡 "+tracks.size()+"개를 찾았어요. 듣고 싶은 곡부터 선택해 주세요.";changed.run();}
            @Override public void fail(String message){loadingMusic=false;note=message;changed.run();}
        });
    }
    public void weather(int city){
        if(busy())return;
        loadingWeather=true;weatherNote="지역 예보를 가져오고 있어요…";changed.run();
        repository.weather(city,new MusicRepository.Result<MusicRepository.WeatherResult>(){
            @Override public void ok(MusicRepository.WeatherResult result){context.weather=result.weather;weatherNote=result.label;loadingWeather=false;results.clear();changed.run();}
            @Override public void fail(String message){weatherNote=message;loadingWeather=false;changed.run();}
        });
    }
    public void edited(){results.clear();note="";reason="";changed.run();}
}

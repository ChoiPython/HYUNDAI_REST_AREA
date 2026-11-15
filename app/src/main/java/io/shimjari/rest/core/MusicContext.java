package io.shimjari.rest.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Explicit preferences first; weather and time are suggestions, never emotion detection. */
public final class MusicContext {
    public enum Purpose { NAP, REST, REFRESH }
    public enum Feeling { UNSPECIFIED, CALM, TENSE, TIRED, BRIGHT }
    public enum Weather { UNKNOWN, CLEAR, CLOUDY, RAIN, SNOW }
    public Purpose purpose=Purpose.NAP;
    public Feeling feeling=Feeling.UNSPECIFIED;
    public Weather weather=Weather.UNKNOWN;
    public String preferredGenre="";
    public int hour=12;
    public MusicContext copy(){MusicContext c=new MusicContext();c.purpose=purpose;c.feeling=feeling;c.weather=weather;c.preferredGenre=preferredGenre;c.hour=hour;return c;}
    public boolean quiet(){return purpose==Purpose.NAP || feeling==Feeling.TENSE || feeling==Feeling.TIRED;}
    public String genre(){
        if(!preferredGenre.isEmpty())return preferredGenre;
        if(quiet())return "Ambient";
        if(purpose==Purpose.REFRESH)return weather==Weather.RAIN||weather==Weather.SNOW?"Jazz":"Lo-Fi";
        return hour>=20||hour<7||weather==Weather.RAIN?"Ambient":"Lo-Fi";
    }
    public String targetMood(){return quiet()||purpose==Purpose.REST?"Peaceful":"Uplifting";}
    public String reason(){
        String purposeText=purpose==Purpose.NAP?"잠깐 잠들기":purpose==Purpose.REST?"조용히 쉬기":"기분 전환";
        String moodText=feeling==Feeling.UNSPECIFIED?"기분 선택 없음":feeling==Feeling.CALM?"차분함":feeling==Feeling.TENSE?"긴장됨":feeling==Feeling.TIRED?"지침":"밝은 기분";
        String weatherText=weather==Weather.UNKNOWN?"날씨 미선택":weather==Weather.CLEAR?"맑음":weather==Weather.CLOUDY?"흐림":weather==Weather.RAIN?"비":"눈";
        String genreText=genre().equals("Ambient")?"앰비언트":genre().equals("Lo-Fi")?"로파이":genre().equals("Jazz")?"재즈":"클래식";
        return purposeText+" · "+moodText+" · "+weatherText+" · "+(hour>=20||hour<7?"밤":"낮")+"\n"+genreText+" 음악에서 목적과 날씨·시간에 어울리는 공개 태그를 함께 살펴봤어요.";
    }
    public double score(MusicTrack track){
        double score=track.genre.equalsIgnoreCase(genre())?100:0;
        if(track.mood.equalsIgnoreCase(targetMood()))score+=60;
        if(track.bpm>0)score+=Math.max(0,25-Math.abs(track.bpm-(quiet()?70:100))/3);
        if(weather==Weather.RAIN&&has(track,"rain","rainy","water","piano"))score+=6;
        if(weather==Weather.SNOW&&has(track,"snow","winter","warm","piano"))score+=6;
        if(weather==Weather.CLEAR&&has(track,"sun","sunny","morning","light"))score+=6;
        if(weather==Weather.CLOUDY&&has(track,"cloud","cloudy","soft","mellow"))score+=6;
        if(hour>=20||hour<7){if(has(track,"night","sleep","dream"))score+=4;}
        else if(has(track,"day","morning","sunny"))score+=4;
        if(feeling==Feeling.TENSE&&has(track,"calm","peace","relax","comfort"))score+=8;
        if(feeling==Feeling.TIRED&&has(track,"gentle","relax","rest"))score+=8;
        if(feeling==Feeling.BRIGHT&&has(track,"bright","happy","uplifting"))score+=8;
        if(feeling==Feeling.CALM&&has(track,"mellow","calm","peaceful"))score+=8;
        return score;
    }
    private boolean has(MusicTrack track,String...words){String value=(" "+track.tags+" "+track.title+" ").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ");for(String word:words)if(value.contains(" "+word+" "))return true;return false;}
    public List<MusicTrack> rank(List<MusicTrack> candidates){List<MusicTrack> copy=new ArrayList<>(candidates);copy.sort(Comparator.comparingDouble(this::score).reversed());return copy;}
    public static Weather weatherCode(int code){
        if(code==0||code==1)return Weather.CLEAR;
        if(code==2||code==3||code==45||code==48)return Weather.CLOUDY;
        if(code==71||code==73||code==75||code==77||code==85||code==86)return Weather.SNOW;
        if(code==51||code==53||code==55||code==56||code==57||code==61||code==63||code==65||code==66||code==67||code==80||code==81||code==82||code==95||code==96||code==97||code==99)return Weather.RAIN;
        return Weather.UNKNOWN;
    }
}

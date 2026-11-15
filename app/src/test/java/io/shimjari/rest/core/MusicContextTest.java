package io.shimjari.rest.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MusicContextTest {
    private MusicTrack track(String id,String genre,String mood,double bpm){return new MusicTrack(id,"track","artist",genre,mood,"/artist/track",bpm,240);}
    @Test public void explicitGenreWinsOverWeatherAndDefault(){MusicContext c=new MusicContext();c.weather=MusicContext.Weather.RAIN;c.preferredGenre="Jazz";assertEquals("Jazz",c.genre());}
    @Test public void weatherDoesNotInferTheUsersEmotion(){MusicContext c=new MusicContext();c.weather=MusicContext.Weather.RAIN;assertEquals(MusicContext.Feeling.UNSPECIFIED,c.feeling);assertTrue(c.reason().contains("기분 선택 없음"));}
    @Test public void napKeepsQuietMoodEvenWhenFeelingBright(){MusicContext c=new MusicContext();c.feeling=MusicContext.Feeling.BRIGHT;assertEquals("Peaceful",c.targetMood());assertEquals("Ambient",c.genre());}
    @Test public void restUsesTimeAsASecondaryContext(){MusicContext c=new MusicContext();c.purpose=MusicContext.Purpose.REST;c.hour=12;assertEquals("Lo-Fi",c.genre());c.hour=23;assertEquals("Ambient",c.genre());}
    @Test public void rankingPrefersRequestedGenreAndMoodWithoutMutatingInput(){MusicContext c=new MusicContext();MusicTrack wrong=track("abc2","Rock","Excited",150),right=track("abc3","Ambient","Peaceful",70);List<MusicTrack> source=new ArrayList<>(Arrays.asList(wrong,right));assertSame(right,c.rank(source).get(0));assertSame(wrong,source.get(0));}
    @Test public void unknownTempoRemainsUnknown(){MusicTrack t=track("abc4","Ambient","",Double.NaN);assertEquals(0,t.bpm,0);assertTrue(Double.isFinite(new MusicContext().score(t)));}
    @Test public void everyAccessRestrictionBlocksPlayback(){assertTrue(MusicTrack.openStream(true,false,true,false));assertFalse(MusicTrack.openStream(false,false,true,false));assertFalse(MusicTrack.openStream(true,true,true,false));assertFalse(MusicTrack.openStream(true,false,false,false));assertFalse(MusicTrack.openStream(true,false,true,true));}
    @Test(expected=IllegalArgumentException.class) public void injectedTrackIdCannotBecomeARequest(){track("../../private","Ambient","Peaceful",70);}
    @Test public void changingProfileQueueDoesNotChangeARestSession(){RestProfile p=new RestProfile("p","test",SeatId.PASSENGER,22,1,25,3);p.music.add(track("abc5","Ambient","Peaceful",70));RestSession s=new RestSession("s",p,true);p.music.clear();assertEquals(1,s.profile.music.size());assertEquals("abc5",s.profile.music.get(0).id);}
    @Test public void weatherCodesCoverRainSnowAndUnknown(){assertEquals(MusicContext.Weather.RAIN,MusicContext.weatherCode(61));assertEquals(MusicContext.Weather.SNOW,MusicContext.weatherCode(85));assertEquals(MusicContext.Weather.UNKNOWN,MusicContext.weatherCode(-1));assertEquals(MusicContext.Weather.UNKNOWN,MusicContext.weatherCode(999));}
    @Test public void weatherCanChangeOrderWithinTheSameGenre(){MusicContext c=new MusicContext();MusicTrack rain=new MusicTrack("rain1","track","artist","Ambient","Peaceful","/a/rain",70,240,"rain water"),sun=new MusicTrack("sun22","track","artist","Ambient","Peaceful","/a/sun",70,240,"sun light");c.weather=MusicContext.Weather.RAIN;assertSame(rain,c.rank(Arrays.asList(sun,rain)).get(0));c.weather=MusicContext.Weather.CLEAR;assertSame(sun,c.rank(Arrays.asList(rain,sun)).get(0));}
    @Test public void timeCanChangeOrderWithinTheSameGenre(){MusicContext c=new MusicContext();MusicTrack night=new MusicTrack("night1","track","artist","Ambient","Peaceful","/a/night",70,240,"night dream"),day=new MusicTrack("day222","track","artist","Ambient","Peaceful","/a/day",70,240,"day morning");c.hour=23;assertSame(night,c.rank(Arrays.asList(day,night)).get(0));c.hour=9;assertSame(day,c.rank(Arrays.asList(night,day)).get(0));}
    @Test public void unknownWeatherCodeIsNotInventedAsRain(){assertEquals(MusicContext.Weather.UNKNOWN,MusicContext.weatherCode(58));}
}

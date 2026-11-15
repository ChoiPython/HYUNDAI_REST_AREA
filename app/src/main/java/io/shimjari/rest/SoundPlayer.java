package io.shimjari.rest;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.net.Uri;
import io.shimjari.rest.core.RestProfile;
import io.shimjari.rest.core.MusicTrack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class SoundPlayer {
    private final Context context;
    private final AudioManager manager;
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final MediaSession mediaSession;
    private final AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final List<MusicTrack> queue=new ArrayList<>();
    private Runnable listener=()->{};
    private Consumer<String> commands;
    private String key="",status="소리 없음",title="";
    private int current,index,generation;
    private int playback=PlaybackState.STATE_STOPPED;
    private boolean prepared,userPaused,transientPaused;
    private float fade=1,duck=1;
    public SoundPlayer(Context context) {
        this.context=context;manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        mediaSession=new MediaSession(context,"Shimjari rest music");
        mediaSession.setCallback(new MediaSession.Callback(){
            @Override public void onPause(){command("pause");}
            @Override public void onPlay(){command("play");}
            @Override public void onSkipToNext(){command("next");}
            @Override public void onSkipToPrevious(){command("previous");}
            @Override public void onStop(){command("stop");}
            @Override public void onPlayFromMediaId(String id,android.os.Bundle extras){command("track:"+id);}
            @Override public void onPlayFromSearch(String query,android.os.Bundle extras){
                if(query==null)return;String q=query.toLowerCase(java.util.Locale.ROOT);
                for(MusicTrack t:queue)if((t.title+" "+t.artist).toLowerCase(java.util.Locale.ROOT).contains(q)){command("track:"+t.id);return;}
            }
        },main);
    }
    public void listener(Runnable listener){this.listener=listener;}
    public void commands(Consumer<String> commands){this.commands=commands;}
    private void command(String action){if(commands!=null)commands.accept(action);}
    public MediaSession.Token token(){return mediaSession.getSessionToken();}
    public boolean ongoing(){return !userPaused&&(playback==PlaybackState.STATE_PLAYING||playback==PlaybackState.STATE_BUFFERING||transientPaused);}
    public boolean interrupted(){return transientPaused;}
    public void releasePaused(){if(!ongoing()){releasePlayer();abandonFocus();mediaSession.setActive(false);}}
    public String status(){return status;}
    public String title(){return title;}
    private void state(String value,int playback){
        boolean changed=!status.equals(value);status=value;this.playback=playback;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_STOP|PlaybackState.ACTION_PLAY_FROM_MEDIA_ID|PlaybackState.ACTION_PLAY_FROM_SEARCH).setState(playback,PlaybackState.PLAYBACK_POSITION_UNKNOWN,playback==PlaybackState.STATE_PLAYING?1:0).build());
        if(changed)main.post(listener);
    }
    public void play(RestProfile profile){
        if(profile.sound==0){stop();return;}
        StringBuilder wanted=new StringBuilder().append(profile.sound);
        for(MusicTrack t:profile.music)wanted.append('|').append(t.id);
        if(key.equals(wanted.toString()))return;
        stop();key=wanted.toString();current=profile.sound;queue.addAll(profile.music);index=0;fade=1;
        if(current==3 && queue.isEmpty()){state("추천 음악을 먼저 선택해 주세요.",PlaybackState.STATE_ERROR);return;}
        startSelection();
    }
    private boolean requestFocus(){
        if(focus!=null)return true;
        final int cycle=generation;
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes).setOnAudioFocusChangeListener(change->{if(cycle==generation&&focus!=null)focusChanged(change);},main).build();
        if(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return true;
        abandonFocus();state("다른 오디오 사용 중이에요. 준비되면 다시 재생해 주세요.",PlaybackState.STATE_PAUSED);return false;
    }
    private void startSelection(){
        userPaused=false;transientPaused=false;duck=1;
        if(!requestFocus())return;
        mediaSession.setActive(true);
        if(current==3){prepareTrack();return;}
        title=current==1?"잔잔한 선율":"빗소리";
        player=MediaPlayer.create(context,current==1?R.raw.melody:R.raw.rain,attributes,0);
        if(player==null){failure("소리를 재생하지 못했어요.");return;}
        player.setWakeMode(context,PowerManager.PARTIAL_WAKE_LOCK);
        prepared=true;player.setLooping(true);applyVolume();player.start();metadata(title,"쉼, 자리",0);state("재생 중",PlaybackState.STATE_PLAYING);
    }
    private void prepareTrack(){
        releasePlayer();if(queue.isEmpty())return;
        MusicTrack track=queue.get(index);title=track.title+" — "+track.artist;metadata(track.title,track.artist,track.seconds*1000L);
        MediaPlayer candidate=new MediaPlayer();player=candidate;candidate.setAudioAttributes(attributes);candidate.setWakeMode(context,PowerManager.PARTIAL_WAKE_LOCK);
        state("음악을 불러오고 있어요…",PlaybackState.STATE_BUFFERING);
        candidate.setOnPreparedListener(p->{
            if(player!=candidate)return;prepared=true;applyVolume();
            if(userPaused||transientPaused){state("일시정지",PlaybackState.STATE_PAUSED);return;}
            p.start();state("재생 중 · Audius",PlaybackState.STATE_PLAYING);
        });
        candidate.setOnCompletionListener(p->{if(player==candidate&&!userPaused){index=(index+1)%queue.size();prepareTrack();}});
        candidate.setOnErrorListener((p,what,extra)->{if(player==candidate)failure("음악 연결이 끊겼어요. 다시 재생하거나 다른 곡을 선택해 주세요.");return true;});
        try{
            candidate.setDataSource(context,Uri.parse(track.streamUrl()),Collections.singletonMap("User-Agent","Shimjari/1.2"));candidate.prepareAsync();
            main.postDelayed(()->{if(player==candidate&&!prepared)failure("음악 응답이 늦어지고 있어요. 다시 재생해 주세요.");},20000);
        }catch(Exception error){failure("이 곡을 재생할 수 없어요. 다른 곡을 선택해 주세요.");}
    }
    private void metadata(String name,String artist,long duration){mediaSession.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,name).putString(MediaMetadata.METADATA_KEY_ARTIST,artist).putString(MediaMetadata.METADATA_KEY_ALBUM,current==3?"Audius · 쉼, 자리 추천":"쉼, 자리").putLong(MediaMetadata.METADATA_KEY_DURATION,duration).build());}
    private void focusChanged(int change){
        if(change==AudioManager.AUDIOFOCUS_GAIN){duck=1;applyVolume();boolean resume=transientPaused&&!userPaused;transientPaused=false;if(resume&&player!=null&&prepared){player.start();state("재생 중",PlaybackState.STATE_PLAYING);}}
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){duck=.2f;applyVolume();}
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){transientPaused=true;if(player!=null&&prepared)player.pause();state("안내 또는 통화 중에는 잠시 멈춰요.",PlaybackState.STATE_PAUSED);}
        else if(change==AudioManager.AUDIOFOCUS_LOSS){transientPaused=false;releasePlayer();abandonFocus();mediaSession.setActive(false);state("다른 오디오로 전환했어요. 원하면 다시 재생해 주세요.",PlaybackState.STATE_PAUSED);}
    }
    private void releasePlayer(){if(player!=null){player.release();player=null;}prepared=false;}
    private void abandonFocus(){if(focus!=null){AudioFocusRequest old=focus;focus=null;manager.abandonAudioFocusRequest(old);}}
    private void failure(String message){releasePlayer();abandonFocus();state(message,PlaybackState.STATE_ERROR);}
    private void applyVolume(){if(player!=null&&prepared)player.setVolume(.12f*fade*duck,.12f*fade*duck);}
    public void remaining(long millis){fade=Math.max(0,Math.min(1,millis/30000f));applyVolume();}
    public void pause(){userPaused=true;transientPaused=false;if(player!=null&&prepared)player.pause();abandonFocus();state("일시정지 · 다시 재생하면 곡의 처음부터 시작해요.",PlaybackState.STATE_PAUSED);}
    public void restart(){if(key.isEmpty())return;releasePlayer();abandonFocus();generation++;startSelection();}
    public void resume(RestProfile profile){String previous=key;play(profile);if(!previous.isEmpty()&&previous.equals(key))restart();}
    public void next(){if(queue.isEmpty())return;userPaused=false;transientPaused=false;index=(index+1)%queue.size();if(requestFocus()){mediaSession.setActive(true);prepareTrack();}}
    public void previous(){if(queue.isEmpty())return;userPaused=false;transientPaused=false;index=(index+queue.size()-1)%queue.size();if(requestFocus()){mediaSession.setActive(true);prepareTrack();}}
    public void track(String id){for(int n=0;n<queue.size();n++)if(queue.get(n).id.equals(id)){index=n;userPaused=false;transientPaused=false;if(requestFocus()){mediaSession.setActive(true);prepareTrack();}return;}}
    public void stop(){
        boolean hadSound=!key.isEmpty();generation++;releasePlayer();abandonFocus();queue.clear();key="";current=0;index=0;userPaused=false;transientPaused=false;title="";mediaSession.setActive(false);
        if(hadSound)state("소리 없음",PlaybackState.STATE_STOPPED);
    }
}

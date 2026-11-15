package io.shimjari.rest;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;

public final class SoundPlayer {
    private final Context context;
    private final AudioManager manager;
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private int current;
    public SoundPlayer(Context context) { this.context = context; manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE); }
    public void play(int choice) {
        if (choice == 0) { stop(); return; }
        if (player != null && current == choice) return;
        stop();
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> { if (change < 0) stop(); }).build();
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
        int sound = choice == 1 ? R.raw.melody : R.raw.rain;
        player = MediaPlayer.create(context, sound, attributes, 0);
        if (player == null) { stop(); return; }
        current = choice; player.setLooping(true); player.setVolume(0.12f, 0.12f); player.start();
    }
    public void stop() {
        if (player != null) { player.release(); player = null; }
        if (focus != null) { manager.abandonAudioFocusRequest(focus); focus = null; }
        current = 0;
    }
}

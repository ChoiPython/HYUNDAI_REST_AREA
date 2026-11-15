package io.shimjari.rest.core;

import java.util.ArrayList;
import java.util.List;

public final class RestProfile {
    public final String id;
    public String name;
    public SeatId seat;
    public float temperature;
    public int fan;
    public int minutes;
    public int sound;
    public final List<MusicTrack> music=new ArrayList<>();

    public RestProfile(String id, String name, SeatId seat, float temperature, int fan, int minutes, int sound) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()) throw new IllegalArgumentException("프로필 이름이 필요합니다.");
        if (!Float.isFinite(temperature) || temperature < 16 || temperature > 30) throw new IllegalArgumentException("온도는 16~30°C에서 선택해 주세요.");
        if (fan < 1 || fan > 8 || minutes < 1 || minutes > 180 || sound < 0 || sound > 3) throw new IllegalArgumentException("설정 범위를 확인해 주세요.");
        this.id = id; this.name = name.trim(); this.seat = seat;
        this.temperature = temperature; this.fan = fan; this.minutes = minutes; this.sound = sound;
    }
    public RestProfile copy() { RestProfile copy=new RestProfile(id,name,seat,temperature,fan,minutes,sound);copy.music.addAll(music);return copy; }
    public String soundName() { return sound==3?"추천 음악 · Audius":sound == 1 ? "잔잔한 선율" : sound == 2 ? "빗소리" : "조용히"; }
}

package io.shimjari.rest.core;

import java.util.ArrayList;
import java.util.List;

public final class RestProfile {
    public final String id;
    public String name;
    public SeatId seat;
    public RestMode mode;
    public float temperature;
    public int fan;
    public int minutes;
    public int sound;
    public boolean arrivalEnabled;
    public long arrivalWall;
    public int wakeLeadMinutes=5;
    public final List<MusicTrack> music=new ArrayList<>();

    public RestProfile(String id, String name, SeatId seat, float temperature, int fan, int minutes, int sound) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()) throw new IllegalArgumentException("프로필 이름이 필요합니다.");
        if (!Float.isFinite(temperature) || temperature < 16 || temperature > 30) throw new IllegalArgumentException("온도는 16~30°C에서 선택해 주세요.");
        if (fan < 1 || fan > 8 || minutes < 1 || minutes > 180 || sound < 0 || sound > 3) throw new IllegalArgumentException("설정 범위를 확인해 주세요.");
        this.id = id; this.name = name.trim(); this.seat = seat;
        this.temperature = temperature; this.fan = fan; this.minutes = minutes; this.sound = sound;
        this.mode = seat == SeatId.DRIVER ? RestMode.DRIVER_PARKED : RestMode.PASSENGER_RIDE;
    }
    public RestProfile copy() { RestProfile copy=new RestProfile(id,name,seat,temperature,fan,minutes,sound);copy.mode=mode;copy.music.addAll(music);copy.arrivalEnabled=arrivalEnabled;copy.arrivalWall=arrivalWall;copy.wakeLeadMinutes=wakeLeadMinutes;return copy; }
    public void mode(RestMode next) { mode=next;if(!mode.accepts(seat))seat=mode.defaultSeat(); }
    public String soundName() { return sound==3?"추천 음악 · Audius":sound == 1 ? "잔잔한 선율" : sound == 2 ? "빗소리" : "조용히"; }
}

package io.shimjari.rest.core;

public final class RestProfile {
    public final String id;
    public String name;
    public SeatId seat;
    public float temperature;
    public int fan;
    public int minutes;
    public int sound;

    public RestProfile(String id, String name, SeatId seat, float temperature, int fan, int minutes, int sound) {
        if (id == null || id.isBlank() || name == null || name.trim().isEmpty()) throw new IllegalArgumentException("프로필 이름이 필요합니다.");
        if (!Float.isFinite(temperature) || temperature < 16 || temperature > 30) throw new IllegalArgumentException("온도는 16~30°C에서 선택해 주세요.");
        if (fan < 1 || fan > 8 || minutes < 1 || minutes > 180 || sound < 0 || sound > 2) throw new IllegalArgumentException("설정 범위를 확인해 주세요.");
        this.id = id; this.name = name.trim(); this.seat = seat;
        this.temperature = temperature; this.fan = fan; this.minutes = minutes; this.sound = sound;
    }
    public RestProfile copy() { return new RestProfile(id, name, seat, temperature, fan, minutes, sound); }
    public String soundName() { return sound == 1 ? "잔잔한 선율" : sound == 2 ? "빗소리" : "조용히"; }
}

package io.shimjari.rest.core;

/** The person resting determines the seat, display and audio behavior. */
public enum RestMode {
    PASSENGER_RIDE("동승자 휴식", "주행 중 함께 타는 사람의 쉼"),
    DRIVER_PARKED("운전자 휴식", "주차 후 운전자의 쉼");

    public final String label;
    public final String description;

    RestMode(String label, String description) { this.label = label; this.description = description; }

    public boolean accepts(SeatId seat) {
        return seat != null && (this == PASSENGER_RIDE ? seat != SeatId.DRIVER : seat == SeatId.DRIVER);
    }

    public SeatId defaultSeat() { return this == PASSENGER_RIDE ? SeatId.PASSENGER : SeatId.DRIVER; }
    public boolean backgroundAudio() { return this == PASSENGER_RIDE; }

    public static RestMode restore(String name, SeatId legacySeat) {
        try { return valueOf(name); }
        catch (Exception missing) { return legacySeat == SeatId.DRIVER ? DRIVER_PARKED : PASSENGER_RIDE; }
    }

    public boolean canStart(boolean parkKnown, boolean parked, boolean manuallyConfirmed) {
        return this == PASSENGER_RIDE || (parkKnown ? parked : manuallyConfirmed);
    }
}

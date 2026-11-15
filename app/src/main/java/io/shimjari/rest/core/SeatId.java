package io.shimjari.rest.core;

public enum SeatId {
    DRIVER("운전석"), PASSENGER("동승석"), REAR_LEFT("뒷좌석 왼쪽"), REAR_RIGHT("뒷좌석 오른쪽");
    public final String label;
    SeatId(String label) { this.label = label; }
}

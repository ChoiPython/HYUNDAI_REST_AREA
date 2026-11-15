package io.shimjari.rest.core;

public final class VehicleSnapshot {
    public Float temperature;
    public Integer fan;
    public Float minTemperature, maxTemperature;
    public Integer minFan, maxFan;
    public boolean temperatureWritable, fanWritable;
    public String temperatureNote = "조회 대기";
    public String fanNote = "조회 대기";
    public String source;

    public boolean acceptsTemperature(float value) {
        return temperatureWritable && temperature != null && Float.isFinite(temperature)
            && minTemperature != null && maxTemperature != null && Float.isFinite(minTemperature) && Float.isFinite(maxTemperature)
            && minTemperature <= maxTemperature && temperature >= minTemperature && temperature <= maxTemperature
            && Float.isFinite(value) && value >= minTemperature && value <= maxTemperature;
    }
    public boolean acceptsFan(int value) {
        return fanWritable && fan != null && minFan != null && maxFan != null && minFan <= maxFan
            && fan >= minFan && fan <= maxFan && value >= minFan && value <= maxFan;
    }
    public static boolean shouldRestoreTemperature(Float current, float applied) {
        return current != null && Float.isFinite(current) && Math.abs(current - applied) < 0.11f;
    }
    public static boolean shouldRestoreFan(Integer current, int applied) { return current != null && current == applied; }
}

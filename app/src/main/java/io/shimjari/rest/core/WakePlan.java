package io.shimjari.rest.core;

public final class WakePlan {
    public static final long MAX_REST=180*60_000L;
    private WakePlan() {}
    public static long deadline(long now,long arrival,int leadMinutes) {
        if(leadMinutes<1||leadMinutes>30)throw new IllegalArgumentException("기상 여유는 1~30분으로 정해 주세요.");
        long wake=arrival-leadMinutes*60_000L;
        if(arrival<=now||wake<=now)throw new IllegalArgumentException("깨울 시간이 이미 지났어요. 도착 시각이나 여유 시간을 바꿔 주세요.");
        if(wake-now>MAX_REST)throw new IllegalArgumentException("기상 예약은 지금부터 3시간 이내로 정해 주세요.");
        return wake;
    }
    public static long nextTime(long now,int hour,int minute,java.util.TimeZone zone){
        if(hour<0||hour>23||minute<0||minute>59)throw new IllegalArgumentException("도착 시각을 확인해 주세요.");
        java.util.Calendar date=java.util.Calendar.getInstance(zone);date.setTimeInMillis(now);
        date.set(java.util.Calendar.HOUR_OF_DAY,hour);date.set(java.util.Calendar.MINUTE,minute);date.set(java.util.Calendar.SECOND,0);date.set(java.util.Calendar.MILLISECOND,0);
        if(date.getTimeInMillis()<=now)date.add(java.util.Calendar.DAY_OF_MONTH,1);
        return date.getTimeInMillis();
    }
    public static long automaticDeadline(long now,long arrival,int leadMinutes){
        if(leadMinutes<1||leadMinutes>30||arrival<now)throw new IllegalArgumentException("도착 예상 시간을 확인해 주세요.");
        long wake=Math.max(now,arrival-leadMinutes*60_000L);
        if(wake-now>MAX_REST)throw new IllegalArgumentException("기상 예약은 지금부터 3시간 이내로 정해 주세요.");
        return wake;
    }
}

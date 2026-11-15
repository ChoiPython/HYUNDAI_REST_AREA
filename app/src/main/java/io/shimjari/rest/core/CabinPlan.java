package io.shimjari.rest.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A reviewable draft for shared controls, never an automatic vehicle command. */
public final class CabinPlan {
    public final int fan,sound;
    public final boolean musicConflict;
    public CabinPlan(List<RestProfile> profiles){
        if(profiles.isEmpty())throw new IllegalArgumentException("동승자 설정이 필요해요.");
        Set<SeatId> seats=new HashSet<>();int lowFan=8,firstSound=profiles.get(0).sound;String firstTrack=track(profiles.get(0));boolean conflict=false;
        for(RestProfile p:profiles){
            if(p.mode!=RestMode.PASSENGER_RIDE||!p.mode.accepts(p.seat)||!seats.add(p.seat))throw new IllegalArgumentException("동승자 좌석이 겹치거나 올바르지 않아요.");
            lowFan=Math.min(lowFan,p.fan);
            if(p.sound!=firstSound||!track(p).equals(firstTrack))conflict=true;
        }
        fan=lowFan;musicConflict=conflict;sound=conflict?0:firstSound;
    }
    private static String track(RestProfile p){return p.sound==3&&!p.music.isEmpty()?p.music.get(0).id:"";}
}

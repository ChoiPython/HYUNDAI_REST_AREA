package io.shimjari.rest.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import io.shimjari.rest.companion.LocalEndpoint;

public class TripFeaturesTest {
    private RestProfile profile(SeatId seat,int sound){return new RestProfile("p","Guest",seat,22,2,25,sound);}
    @Test public void wakeDeadlineUsesArrivalAndPersonalLead(){assertEquals(1_300_000L,WakePlan.deadline(1_000_000L,1_600_000L,5));}
    @Test public void shortenedNavigationEtaWakesImmediatelyInsteadOfMissingTheWake(){assertEquals(1_000_000L,WakePlan.automaticDeadline(1_000_000L,1_060_000L,5));}
    @Test(expected=IllegalArgumentException.class) public void alreadyPassedWakeIsRejected(){WakePlan.deadline(1_000_000L,1_060_000L,1);}
    @Test(expected=IllegalArgumentException.class) public void overThreeHoursIsRejected(){WakePlan.deadline(1_000_000L,1_000_000L+181*60000L+5*60000L,5);}
    @Test(expected=IllegalArgumentException.class) public void invalidLeadIsRejected(){WakePlan.deadline(1_000_000L,2_000_000L,0);}
    @Test public void midnightArrivalUsesFollowingDay(){Calendar d=Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));d.clear();d.set(2026,10,15,23,50);long next=WakePlan.nextTime(d.getTimeInMillis(),0,20,d.getTimeZone());assertEquals(30*60000L,next-d.getTimeInMillis());}
    @Test public void arrivalSessionCannotBeExtendedPastWakeByFiveMinuteButton(){RestProfile p=profile(SeatId.PASSENGER,0);p.arrivalEnabled=true;p.arrivalWall=1_600_000;p.wakeLeadMinutes=5;RestSession s=new RestSession("s",p,true);s.start(1_000_000,10000,1);s.extendFiveMinutes();assertEquals(1_300_000,s.endWall);assertEquals(310000,s.endElapsed);}
    @Test public void driverModeIgnoresPassengerArrivalPreference(){RestProfile p=profile(SeatId.DRIVER,0);p.arrivalEnabled=true;p.arrivalWall=1;RestSession s=new RestSession("s",p,true);s.start(1_000_000,10000,1);assertEquals(2_500_000,s.endWall);s.extendFiveMinutes();assertEquals(2_800_000,s.endWall);}
    @Test public void arrivalFieldsAreCopiedIntoSession(){RestProfile p=profile(SeatId.REAR_LEFT,0);p.arrivalEnabled=true;p.arrivalWall=1_600_000;p.wakeLeadMinutes=10;RestSession s=new RestSession("s",p,true);p.arrivalWall=0;p.wakeLeadMinutes=1;assertEquals(1_600_000,s.profile.arrivalWall);assertEquals(10,s.profile.wakeLeadMinutes);}
    @Test public void commonPreferencesPreferQuietOnConflictWithoutMutatingRequests(){RestProfile a=profile(SeatId.PASSENGER,1),b=profile(SeatId.REAR_LEFT,2);a.fan=4;b.fan=1;CabinPlan plan=new CabinPlan(Arrays.asList(a,b));assertEquals(1,plan.fan);assertEquals(0,plan.sound);assertTrue(plan.musicConflict);assertEquals(1,a.sound);assertEquals(2,b.sound);}
    @Test public void matchingSoundIsRetained(){CabinPlan plan=new CabinPlan(Arrays.asList(profile(SeatId.PASSENGER,2),profile(SeatId.REAR_RIGHT,2)));assertEquals(2,plan.sound);assertFalse(plan.musicConflict);}
    @Test(expected=IllegalArgumentException.class) public void duplicatePassengerSeatIsRejected(){new CabinPlan(Arrays.asList(profile(SeatId.PASSENGER,1),profile(SeatId.PASSENGER,1)));}
    @Test(expected=IllegalArgumentException.class) public void driverSeatIsNotACompanionRequest(){new CabinPlan(Collections.singletonList(profile(SeatId.DRIVER,1)));}
    @Test public void coldSuggestionIsBoundedAndDoesNotChangeCurrentPreferences(){RestProfile p=profile(SeatId.PASSENGER,1);p.temperature=30;FeedbackAdvice advice=FeedbackAdvice.from(p,"추웠어요");assertEquals(30,advice.profile.temperature,0);assertEquals(30,p.temperature,0);}
    @Test public void noisyFeedbackSuggestsQuietAndPreservesSource(){RestProfile p=profile(SeatId.PASSENGER,1);FeedbackAdvice advice=FeedbackAdvice.from(p,"소리가 거슬렸어요");assertEquals(0,advice.profile.sound);assertEquals(1,p.sound);}
    @Test public void happyFeedbackDoesNotInventAnAdjustment(){assertNull(FeedbackAdvice.from(profile(SeatId.PASSENGER,0),"편안했어요"));}
    @Test public void privateAddressesAndPortsAreAccepted(){assertEquals("192.168.1.10:44389",new LocalEndpoint("192.168.1.10:44389").toString());assertEquals("10.0.2.2",new LocalEndpoint("10.0.2.2:55560").host);assertEquals(44389,new LocalEndpoint("172.31.1.2:44389").port);}
    @Test(expected=IllegalArgumentException.class) public void publicAddressIsRejected(){new LocalEndpoint("8.8.8.8:44389");}
    @Test(expected=IllegalArgumentException.class) public void dnsAndUrlAreRejected(){new LocalEndpoint("https://example.com:44389");}
    @Test(expected=IllegalArgumentException.class) public void invalidIpv4IsRejected(){new LocalEndpoint("192.168.1.999:44389");}
    @Test(expected=IllegalArgumentException.class) public void invalidPortIsRejected(){new LocalEndpoint("10.0.2.2:65536");}
}

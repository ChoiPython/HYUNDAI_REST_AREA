package io.shimjari.rest.core;

/** Suggestions change saved preferences only after a person accepts them. */
public final class FeedbackAdvice {
    public final RestProfile profile;
    public final String reason;
    private FeedbackAdvice(RestProfile profile,String reason){this.profile=profile;this.reason=reason;}
    public static FeedbackAdvice from(RestProfile current,String feedback){
        RestProfile next=current.copy();String reason;
        switch(feedback){
            case "추웠어요":next.temperature=Math.min(30,current.temperature+.5f);reason="지난번에 추웠다고 하셨어요. 선호 온도를 0.5°C 높여 볼까요?";break;
            case "더웠어요":next.temperature=Math.max(16,current.temperature-.5f);reason="지난번에 더웠다고 하셨어요. 선호 온도를 0.5°C 낮춰 볼까요?";break;
            case "소리가 거슬렸어요":next.sound=0;reason="지난번에 소리가 거슬렸다고 하셨어요. 이번에는 조용히 쉬어 볼까요?";break;
            case "자세가 불편했어요":reason="지난번에 자세가 불편했다고 하셨어요. 출발 전 앉은 자세를 직접 확인해 주세요.";break;
            default:return null;
        }
        return new FeedbackAdvice(next,reason);
    }
}

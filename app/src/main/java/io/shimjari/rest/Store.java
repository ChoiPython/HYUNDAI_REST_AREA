package io.shimjari.rest;

import android.content.Context;
import android.content.SharedPreferences;
import io.shimjari.rest.core.RestProfile;
import io.shimjari.rest.core.RestSession;
import io.shimjari.rest.core.SeatId;
import io.shimjari.rest.core.MusicTrack;
import io.shimjari.rest.core.RestMode;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Private local profiles and sessions. No account or cloud backup. */
public final class Store {
    private final SharedPreferences prefs;
    public Store(Context context) { prefs = context.getSharedPreferences("shimjari", Context.MODE_PRIVATE); }
    public List<RestProfile> profiles() {
        List<RestProfile> values = new ArrayList<>();
        try {
            JSONArray list = new JSONArray(prefs.getString("profiles", "[]"));
            for (int i = 0; i < list.length(); i++) {
                try { values.add(profile(list.getJSONObject(i))); } catch (Exception ignored) { /* Skip only an invalid profile. */ }
            }
        } catch (JSONException ignored) { /* Safe initial configuration. */ }
        if (values.isEmpty()) values.add(new RestProfile("default", "나의 쉼", SeatId.PASSENGER, 22f, 1, 25, 0));
        return values;
    }
    public void saveProfiles(List<RestProfile> profiles) {
        JSONArray list = new JSONArray();
        for (RestProfile value : profiles) list.put(profileJson(value));
        prefs.edit().putString("profiles", list.toString()).apply();
    }
    public String selectedId() { return prefs.getString("selected", "default"); }
    public void selectedId(String value) { prefs.edit().putString("selected", value).apply(); }
    public boolean demo() { return prefs.getBoolean("demo", true); }
    public void demo(boolean value) { prefs.edit().putBoolean("demo", value).apply(); }
    public JSONObject profileJson(RestProfile value) {
        JSONObject j = new JSONObject();
        try {
            j.put("id", value.id).put("name", value.name).put("seat", value.seat.name())
                .put("temperature", value.temperature).put("fan", value.fan).put("minutes", value.minutes).put("sound", value.sound).put("mode",value.mode.name());
            JSONArray tracks=new JSONArray();
            for(MusicTrack t:value.music)tracks.put(new JSONObject().put("id",t.id).put("title",t.title).put("artist",t.artist).put("genre",t.genre).put("mood",t.mood).put("path",t.permalink).put("bpm",t.bpm).put("seconds",t.seconds).put("tags",t.tags));
            j.put("music",tracks);
            j.put("arrivalEnabled",value.arrivalEnabled).put("arrivalWall",value.arrivalWall).put("wakeLeadMinutes",value.wakeLeadMinutes);
        } catch (JSONException error) { throw new IllegalStateException(error); }
        return j;
    }
    public RestProfile profile(JSONObject j) throws JSONException {
        RestProfile p=new RestProfile(j.getString("id"), j.getString("name"), SeatId.valueOf(j.getString("seat")),
            (float) j.getDouble("temperature"), j.getInt("fan"), j.getInt("minutes"), j.getInt("sound"));
        p.mode(RestMode.restore(j.optString("mode"),p.seat));
        p.arrivalEnabled=j.optBoolean("arrivalEnabled");p.arrivalWall=j.optLong("arrivalWall");p.wakeLeadMinutes=Math.max(1,Math.min(30,j.optInt("wakeLeadMinutes",5)));
        JSONArray tracks=j.optJSONArray("music");
        if(tracks!=null)for(int i=0;i<Math.min(8,tracks.length());i++)try{
            JSONObject t=tracks.getJSONObject(i);p.music.add(new MusicTrack(t.getString("id"),t.optString("title"),t.optString("artist"),t.optString("genre"),t.optString("mood"),t.optString("path"),t.optDouble("bpm",0),t.getInt("seconds"),t.optString("tags")));
        }catch(Exception ignored){/* Ignore a removed or malformed catalogue item. */}
        if(p.sound==3 && p.music.isEmpty())p.sound=0;
        return p;
    }
    public RestSession session() {
        String raw = prefs.getString("session", null);
        if (raw == null) return null;
        try {
            JSONObject j = new JSONObject(raw);
            RestSession s = new RestSession(j.getString("id"), profile(j.getJSONObject("profile")), j.getBoolean("demo"));
            s.phase = RestSession.Phase.valueOf(j.getString("phase"));
            s.startWall = j.optLong("startWall"); s.startElapsed = j.optLong("startElapsed");
            s.endWall = j.optLong("endWall"); s.endElapsed = j.optLong("endElapsed"); s.bootCount = j.optInt("bootCount", -1);
            if (!j.isNull("originalTemperature")) s.originalTemperature = (float) j.getDouble("originalTemperature");
            if (!j.isNull("originalFan")) s.originalFan = j.getInt("originalFan");
            s.temperatureTouched = j.optBoolean("temperatureTouched"); s.fanTouched = j.optBoolean("fanTouched");
            s.report = j.optString("report"); s.finishedWall = j.optLong("finishedWall"); return s;
        } catch (Exception invalid) { return null; }
    }
    public void session(RestSession s) {
        if (s == null) { prefs.edit().remove("session").commit(); return; }
        JSONObject j = new JSONObject();
        try {
            j.put("id", s.id).put("profile", profileJson(s.profile)).put("demo", s.demo).put("phase", s.phase.name())
                .put("startWall", s.startWall).put("startElapsed", s.startElapsed).put("endWall", s.endWall).put("endElapsed", s.endElapsed)
                .put("bootCount", s.bootCount).put("originalTemperature", s.originalTemperature == null ? JSONObject.NULL : s.originalTemperature)
                .put("originalFan", s.originalFan == null ? JSONObject.NULL : s.originalFan)
                .put("temperatureTouched", s.temperatureTouched).put("fanTouched", s.fanTouched).put("report", s.report)
                .put("finishedWall", s.finishedWall);
        } catch (JSONException error) { throw new IllegalStateException(error); }
        // Commit before changing a vehicle setting, so recovery has the original values.
        if (!prefs.edit().putString("session", j.toString()).commit()) throw new IllegalStateException("휴식 상태를 저장하지 못했어요.");
    }
    public JSONArray history() {
        try { return new JSONArray(prefs.getString("history", "[]")); } catch (JSONException invalid) { return new JSONArray(); }
    }
    public void addHistory(RestSession s, String feedback) {
        JSONArray previous = history(), next = new JSONArray();
        try {
            JSONObject row = new JSONObject().put("id", s.id).put("profileId",s.profile.id).put("profile", s.profile.name).put("seat", s.profile.seat.label)
                .put("start", s.startWall).put("end", s.finishedWall).put("temperature", s.profile.temperature)
                .put("fan", s.profile.fan).put("feedback", feedback).put("demo", s.demo).put("report", s.report).put("mode",s.profile.mode.name());
            next.put(row);
            for (int i = 0; i < previous.length() && next.length() < 100; i++) {
                JSONObject old = previous.getJSONObject(i);
                if (!old.optString("id").equals(s.id)) next.put(old);
            }
            prefs.edit().putString("history", next.toString()).commit();
        } catch (JSONException error) { throw new IllegalStateException(error); }
    }
    public void clearHistory() { prefs.edit().remove("history").commit(); }
    public String lastFeedback(RestProfile profile){
        JSONArray rows=history();for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&(profile.id.equals(row.optString("profileId"))||(!row.has("profileId")&&profile.name.equals(row.optString("profile")))))return row.optString("feedback");}return "";
    }
}

package io.shimjari.rest.core;

/** Immutable metadata from the official Audius catalogue; contains no audio file. */
public final class MusicTrack {
    public final String id, title, artist, genre, mood, permalink, tags;
    public final double bpm;
    public final int seconds;
    public MusicTrack(String id, String title, String artist, String genre, String mood, String permalink, double bpm, int seconds) {
        this(id,title,artist,genre,mood,permalink,bpm,seconds,"");
    }
    public MusicTrack(String id, String title, String artist, String genre, String mood, String permalink, double bpm, int seconds,String tags) {
        if (id == null || !id.matches("[A-Za-z0-9]{2,64}") || seconds < 30 || seconds > 1800) throw new IllegalArgumentException("음악 정보를 확인해 주세요.");
        this.id=id;this.title=clean(title);this.artist=clean(artist);this.genre=clean(genre);this.mood=clean(mood);this.tags=clean(tags);
        this.permalink=permalink!=null && permalink.startsWith("/") && !permalink.startsWith("//") ? permalink : "";
        this.bpm=Double.isFinite(bpm) && bpm>0 && bpm<400 ? bpm : 0;this.seconds=seconds;
    }
    private static String clean(String value) { if(value==null)return "";String t=value.replace('\n',' ').trim();return t.substring(0,Math.min(180,t.length())); }
    public static boolean openStream(boolean streamable, boolean gated, boolean allowed, boolean hasConditions) {
        return streamable && !gated && allowed && !hasConditions;
    }
    public String streamUrl() { return "https://api.audius.co/v1/tracks/"+id+"/stream?app_name=Shimjari"; }
    public String pageUrl() { return "https://audius.co"+permalink; }
}

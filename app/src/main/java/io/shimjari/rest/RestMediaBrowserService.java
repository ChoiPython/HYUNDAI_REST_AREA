package io.shimjari.rest;

import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import io.shimjari.rest.core.MusicTrack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Standard vehicle media browser. Binding never starts a rest session or audio. */
public final class RestMediaBrowserService extends MediaBrowserService {
    private RestController controller;
    @Override public void onCreate(){super.onCreate();controller=((RestApp)getApplication()).controller();setSessionToken(controller.sounds.token());}
    @Override public BrowserRoot onGetRoot(String clientPackage,int clientUid,Bundle hints){
        String[] packages=getPackageManager().getPackagesForUid(clientUid);
        if(packages==null||!Arrays.asList(packages).contains(clientPackage))return null;
        return new BrowserRoot("root",null);
    }
    @Override public void onLoadChildren(String parent,Result<List<MediaBrowser.MediaItem>> result){
        List<MediaBrowser.MediaItem> items=new ArrayList<>();
        if("root".equals(parent)){
            items.add(new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId("music").setTitle("휴식 음악").setSubtitle("앱에서 동승자 휴식을 시작한 뒤 재생할 수 있어요").build(),MediaBrowser.MediaItem.FLAG_BROWSABLE));
        }else if("music".equals(parent)&&controller.passengerAudioSession()){
            for(MusicTrack t:controller.session.profile.music)items.add(new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId(t.id).setTitle(t.title).setSubtitle(t.artist+" · Audius").build(),MediaBrowser.MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(items);
    }
}

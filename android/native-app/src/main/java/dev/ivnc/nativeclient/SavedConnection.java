/* Reuses the working client's Android Keystore profile format in a separate native-app namespace. */
package dev.ivnc.nativeclient;

import android.content.Context;
import android.os.Handler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Serializes storage writes and Forget so an in-flight save cannot resurrect deleted credentials. */
final class SavedConnection implements AutoCloseable {
    private final ProfileStore store;
    private final android.content.SharedPreferences preferences;
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private int revision;
    private ConnectionProfile cached;
    private String error;
    SavedConnection(Context context,boolean synthetic){
        String suffix=synthetic?"-test":"";
        store=new ProfileStore(context,"native-profile"+suffix,"ivnc.native.profile.v1"+suffix);
        preferences=context.getSharedPreferences("native-session"+suffix,0);
        try{JSONObject loaded=new JSONObject(store.load());if(loaded.has("error"))error=loaded.getString("error");
            else if(!loaded.isNull("profile"))cached=ConnectionProfile.parse(loaded.getJSONObject("profile"));}
        catch(Exception failure){error="Saved connection could not be loaded";}
    }
    ConnectionProfile profile(){return cached;}
    String error(){return error;}
    boolean autoResume(){return preferences.getBoolean("resume",true);}
    void autoResume(boolean wanted){preferences.edit().putBoolean("resume",wanted).apply();}
    void save(ConnectionProfile profile,Consumer<Boolean> result){
        if(!profile.remember)return;
        final int token=++revision;cached=profile;error=null;
        storage.execute(()->{boolean success=false;try{success=store.save(profile.encryptedInput());}catch(Exception ignored){}
            final boolean saved=success;main.post(()->{if(token==revision){if(!saved)cached=null;result.accept(saved);}});});
    }
    void forget(Consumer<Boolean> result){
        revision++;cached=null;error=null;autoResume(false);
        storage.execute(()->{boolean forgotten=store.forget();main.post(()->result.accept(forgotten));});
    }
    @Override public void close(){storage.shutdown();}
}

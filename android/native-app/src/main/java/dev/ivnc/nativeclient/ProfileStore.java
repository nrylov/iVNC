/* Encrypts the single saved connection using an app-scoped Android Keystore key. */
package dev.ivnc.nativeclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Keeps connection credentials out of WebView storage, backups, diagnostics and source files. */
final class ProfileStore {
    private final SharedPreferences preferences;
    private final String alias;
    ProfileStore(Context context) { this(context, "native-profile", "ivnc.native.profile.v1"); }
    ProfileStore(Context context, String name, String alias) {
        preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        this.alias = alias;
    }
    synchronized String load() {
        JSONObject result = new JSONObject();
        try {
            String saved = preferences.getString("encrypted", null);
            if (saved == null) return "{\"profile\":null}";
            String[] pieces = saved.split("\\.", -1);
            if (pieces.length != 2) throw new IllegalStateException("Invalid saved format");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(pieces[0], Base64.DEFAULT)));
            byte[] decoded = cipher.doFinal(Base64.decode(pieces[1], Base64.DEFAULT));
            result.put("profile", new JSONObject(new String(decoded, StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            try { result.put("error", "Saved connection could not be unlocked. Forget it and re-enter the details."); }
            catch (Exception ignored) { }
        }
        return result.toString();
    }
    synchronized boolean save(String text) {
        if (text == null || text.length() > 8192) return false;
        try {
            JSONObject profile = new JSONObject(text);
            String host = profile.getString("host"), username = profile.getString("username");
            if (host.isEmpty() || host.length() > 253 || username.isEmpty() || username.length() > 256
                || profile.getString("password").length() > 2048
                || profile.getInt("port") < 1 || profile.getInt("port") > 65535) return false;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
            String data = Base64.encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            return preferences.edit().putString("encrypted", iv + "." + data).commit();
        } catch (Exception failure) { return false; }
    }
    synchronized boolean forget() {
        boolean cleared = preferences.edit().clear().commit();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(alias)) store.deleteEntry(alias);
            return cleared;
        } catch (Exception failure) { return false; }
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(alias)) return (SecretKey)store.getKey(alias, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUnlockedDeviceRequired(true).build());
        return generator.generateKey();
    }
}

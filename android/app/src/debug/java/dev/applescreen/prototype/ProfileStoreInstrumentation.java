/* Debug-only encrypted profile round-trip using a dedicated test key and preference file. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONObject;

/** Verifies persistence without inspecting or changing the owner's saved connection. */
public final class ProfileStoreInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        ProfileStore store = new ProfileStore(getTargetContext(), "ivnc-profile-test", "ivnc.profile.integration-test");
        try {
            String profile = new JSONObject().put("host", "test-server.invalid").put("port", 5900)
                .put("username", "synthetic-user").put("password", "not-a-real-password-ABC123")
                .put("mode", "highperf").toString();
            require(store.save(profile), "Could not encrypt profile");
            String saved = getTargetContext().getSharedPreferences("ivnc-profile-test", 0).getString("encrypted", "");
            require(!saved.contains("synthetic-user") && !saved.contains("not-a-real-password"), "Plaintext leaked to preferences");
            JSONObject loaded = new JSONObject(store.load()).getJSONObject("profile");
            require(loaded.getString("password").equals("not-a-real-password-ABC123"), "Password round-trip changed");
            require(loaded.getString("host").equals("test-server.invalid"), "Host round-trip changed");
            require(store.forget(), "Forget failed");
            require(new JSONObject(store.load()).isNull("profile"), "Forgotten profile was returned");
            result.putString("stream", "PASS: Android Keystore encryption, exact profile round-trip, and Forget. Owner profile untouched.");
            finish(Activity.RESULT_OK, result);
        } catch (Exception failure) {
            result.putString("stream", "PROFILE TEST FAILED: " + failure.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        } finally { store.forget(); }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}

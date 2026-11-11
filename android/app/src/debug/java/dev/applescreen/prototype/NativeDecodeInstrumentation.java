/* Debug-only entry point for unattended on-device decoder compatibility testing. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Runs only bundled synthetic clips and emits a report without reading user data. */
public final class NativeDecodeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            JSONObject report = NativeDecoderTest.run(getTargetContext());
            String json = report.toString(2);
            try (var output = getTargetContext().openFileOutput("native-decode-report.json", 0)) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
            }
            result.putString("stream", json);
            finish(Activity.RESULT_OK, result);
        } catch (Exception failure) {
            result.putString("stream", "Probe infrastructure failed: " + failure);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}

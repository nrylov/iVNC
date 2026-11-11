/* Android host for the bundled Apple Screen Sharing client and its native video layer.
 * Apple protocol source: peetinc/noVNC-ARD, MPL-2.0. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import org.json.JSONObject;

/** Hosts local UI, forwards typed events, and owns activity-scoped native resources. */
public final class MainActivity extends Activity {
    private WebView view;
    private SessionBridge bridge;
    private boolean destroyed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xff10151d);
        view = new WebView(this);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setAllowContentAccess(false);
        view.getSettings().setDomStorageEnabled(false);
        view.setWebViewClient(new AssetClient(getAssets()));
        frame.addView(view, new FrameLayout.LayoutParams(-1, -1));
        VideoController video = new VideoController(this, frame, (id, kind, value) -> emit("videoEvent", id, kind, value));
        bridge = new SessionBridge(this, video, this::emit);
        view.addJavascriptInterface(bridge, "Native");
        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        setContentView(frame);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().getDecorView().getWindowInsetsController().setSystemBarsAppearance(0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        view.loadUrl(AssetClient.ORIGIN + "/probe.html");
    }
    private void emit(String function, String id, String kind, String value) {
        runOnUiThread(() -> {
            if (destroyed) return;
            view.evaluateJavascript("window." + function + "?.(" + JSONObject.quote(id) + "," +
                JSONObject.quote(kind) + "," + JSONObject.quote(value) + ")", null);
        });
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (bridge != null) bridge.destroy();
        view.removeJavascriptInterface("Native");
        view.destroy();
        super.onDestroy();
    }
}

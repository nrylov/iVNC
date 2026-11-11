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
    private android.widget.Button menu;
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
        menu = new android.widget.Button(this);
        menu.setText("☰"); menu.setTextSize(19); menu.setTextColor(0xffffffff);
        menu.setPadding(0, 0, 0, 0); menu.setFocusable(false); menu.setAlpha(0.78f);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(0xed17283b); background.setCornerRadius(100);
        background.setStroke(2, 0xff7893b2); menu.setBackground(background);
        float density = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams menuPosition = new FrameLayout.LayoutParams((int)(44*density), (int)(44*density),
            android.view.Gravity.BOTTOM | android.view.Gravity.END);
        menuPosition.rightMargin = menuPosition.bottomMargin = (int)(12*density);
        frame.addView(menu, menuPosition);
        menu.setContentDescription("Show or hide viewer controls");
        menu.setOnClickListener(v -> view.evaluateJavascript("window.toggleViewerUI?.()", null));
        bridge = new SessionBridge(this, video, this::emit, this::viewerMode);
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
    private void viewerMode(boolean full) {
        WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (full) controller.hide(WindowInsets.Type.systemBars()); else controller.show(WindowInsets.Type.systemBars());
        menu.setContentDescription(full ? "Show viewer controls" : "Hide viewer controls");
    }
    @Override protected void onResume() {
        super.onResume();
        if (view != null) { view.onResume(); emit("appLifecycle", "foreground", "", ""); }
    }
    @Override protected void onPause() {
        if (view != null) {
            emit("appLifecycle", "background", "", "");
            if (bridge != null) bridge.suspend();
            view.onPause();
        }
        super.onPause();
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (bridge != null) bridge.destroy();
        view.removeJavascriptInterface("Native");
        view.destroy();
        super.onDestroy();
    }
}

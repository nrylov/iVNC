/* Debug-only full-screen host for controlled web/native replay with no Mac credentials. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import java.util.concurrent.CountDownLatch;
import org.json.JSONArray;
import org.json.JSONObject;

/** Uses one window and matched aspect-fit TextureViews so the route is the variable. */
public final class BenchmarkActivity extends Activity {
    final CountDownLatch pageReady = new CountDownLatch(1), fixtureReady = new CountDownLatch(1);
    final CountDownLatch nativeSurfaceReady = new CountDownLatch(1);
    volatile CountDownLatch trialDone;
    volatile String failure;
    volatile JSONObject webResult;
    private WebView web;
    private VideoController video;
    TextureView nativeTexture, webTexture;
    BenchmarkSurface nativeObserver, webObserver;
    Surface nativeSurface;
    private int warmupFrames, totalFrames, fps;
    private long startNs;
    private final JSONArray lateness = new JSONArray();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new AssetClient(getAssets()));
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        nativeTexture = new TextureView(this);
        nativeTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture value, int width, int height) {
                nativeSurface = new Surface(value); nativeSurfaceReady.countDown();
            }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture value) {
                if (nativeSurface != null) nativeSurface.release(); nativeSurface = null; return true;
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture value, int width, int height) { }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture value) { }
        });
        nativeObserver = new BenchmarkSurface(nativeTexture);
        root.addView(nativeTexture, new FrameLayout.LayoutParams(1, 1));
        video = new VideoController(this, root, this::videoEvent, false);
        FrameLayout clip = (FrameLayout)root.getChildAt(root.getChildCount() - 1);
        webTexture = (TextureView)clip.getChildAt(0);
        webObserver = new BenchmarkSurface(webTexture);
        BenchmarkBridge bridge = new BenchmarkBridge(this, video);
        web.addJavascriptInterface(bridge, "Native");
        web.addJavascriptInterface(bridge, "Bench");
        setContentView(root);
        root.post(() -> {
            int width = root.getWidth(), height = root.getHeight();
            double scale = Math.min(width / 1920.0, height / 1080.0);
            FrameLayout.LayoutParams fit = new FrameLayout.LayoutParams((int)Math.round(1920 * scale), (int)Math.round(1080 * scale));
            fit.leftMargin = (width - fit.width) / 2; fit.topMargin = (height - fit.height) / 2;
            nativeTexture.setLayoutParams(fit);
        });
        getWindow().getDecorView().getWindowInsetsController().hide(WindowInsets.Type.systemBars());
        web.loadUrl(AssetClient.ORIGIN + "/benchmark.html");
    }
    void load(JSONArray packets) { web.evaluateJavascript("benchmarkLoad(" + packets + ")", null); }
    void prepare(boolean nativeRoute, int rate, int warmup, int measured, long start) {
        fps = rate; warmupFrames = warmup; totalFrames = warmup + measured; startNs = start;
        failure = null; webResult = null; trialDone = new CountDownLatch(1);
        while (lateness.length() > 0) lateness.remove(lateness.length() - 1);
        nativeTexture.setAlpha(nativeRoute ? 1 : 0);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        // Keep panel preference fixed across source rates to reduce refresh-mode bias.
        attributes.preferredRefreshRate = 120;
        getWindow().setAttributes(attributes);
        (nativeRoute ? nativeObserver : webObserver).begin(start + warmup * (1000000000L / rate));
    }
    void runWeb(int measured) throws Exception {
        JSONObject config = new JSONObject().put("fps", fps).put("warmupFrames", warmupFrames)
            .put("measuredFrames", measured).put("startNs", startNs);
        web.evaluateJavascript("benchmarkRun(" + config + ")", null);
    }
    void stopWeb() { web.evaluateJavascript("benchmarkStop()", null); }
    void fail(String error) { failure = error; if (trialDone != null) trialDone.countDown(); }
    private void videoEvent(String id, String kind, String value) {
        if (kind.equals("error")) { fail(value); return; }
        if (kind.equals("checkpoint")) {
            try {
                JSONObject event = new JSONObject(value);
                int frame = Integer.parseInt(event.getString("token"));
                long end = event.getLong("endNs");
                if (frame > warmupFrames) lateness.put((end - startNs - (frame - 1L) * (1000000000L / fps)) / 1e6);
                if (frame == totalFrames) {
                    webResult = new JSONObject().put("decodedPictures", event.getLong("decoded") - warmupFrames)
                        .put("surfacePosts", event.getLong("presented") - warmupFrames)
                        .put("endNs", end).put("postLatenessMs", new JSONArray(lateness.toString()));
                    trialDone.countDown();
                }
            } catch (Exception error) { fail(error.toString()); }
            return;
        }
        runOnUiThread(() -> web.evaluateJavascript("window.videoEvent?.(" + JSONObject.quote(id) + "," +
            JSONObject.quote(kind) + "," + JSONObject.quote(value) + ")", null));
    }
    @Override protected void onDestroy() {
        video.destroy();
        web.removeJavascriptInterface("Native"); web.removeJavascriptInterface("Bench"); web.destroy();
        super.onDestroy();
    }
}

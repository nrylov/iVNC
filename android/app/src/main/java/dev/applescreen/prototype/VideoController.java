/* Native video worker and a clipped TextureView overlay aligned with the WebView canvas. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.util.Base64;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Serializes decoder lifetime and bounds compressed frames awaiting native decoding. */
final class VideoController implements TextureView.SurfaceTextureListener {
    interface Events { void emit(String id, String kind, String value); }
    private final Activity activity;
    private final Events events;
    private final FrameLayout clip;
    private final TextureView texture;
    private final PointerOverlay pointer;
    private int frameWidth = 1, frameHeight = 1;
    private float pointerX, pointerY;
    private boolean pointerShown;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0,
        TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private volatile String active = "";
    private final java.util.concurrent.atomic.AtomicInteger queuedFrames = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger queuedBytes = new java.util.concurrent.atomic.AtomicInteger();
    private long handle;
    private Surface surface;
    private long decoded, lastReport;
    private volatile long presented;
    private volatile boolean viewportVisible;

    VideoController(Activity activity, FrameLayout parent, Events events) {
        this.activity = activity;
        this.events = events;
        clip = new FrameLayout(activity);
        clip.setClipChildren(true);
        clip.setClickable(false);
        texture = new TextureView(activity);
        texture.setOpaque(true);
        texture.setSurfaceTextureListener(this);
        clip.addView(texture, new FrameLayout.LayoutParams(1, 1));
        pointer = new PointerOverlay(activity);
        clip.addView(pointer, new FrameLayout.LayoutParams(-1, -1));
        parent.addView(clip, new FrameLayout.LayoutParams(1, 1));
        clip.setAlpha(0);
        clip.setVisibility(View.INVISIBLE);
    }

    void start(String id, int width, int height, int stripes) {
        active = id;
        viewportVisible = false;
        frameWidth = width; frameHeight = height;
        pointerShown = false;
        activity.runOnUiThread(() -> { if (active.equals(id)) { clip.setAlpha(0); clip.setVisibility(View.VISIBLE); } });
        enqueue(id, () -> {
            if (handle != 0) NativeVideo.destroy(handle);
            handle = NativeVideo.AVAILABLE ? NativeVideo.create(width, height, stripes) : 0;
            decoded = presented = lastReport = 0;
            if (handle == 0) { events.emit(id, "error", "Unsupported video dimensions or decoder unavailable"); return; }
            if (surface != null) NativeVideo.surface(handle, surface);
        });
    }

    void packet(String id, String encoded, long timestamp, int stripe) {
        if (!active.equals(id)) return;
        if (encoded.length() > 5592408) { events.emit(id, "error", "Video packet exceeds 4 MiB"); return; }
        final byte[] bytes;
        try { bytes = Base64.decode(encoded, Base64.DEFAULT); }
        catch (IllegalArgumentException invalid) { events.emit(id, "error", "Invalid video packet"); return; }
        int framesQueued = queuedFrames.incrementAndGet();
        int bytesQueued = queuedBytes.addAndGet(bytes.length);
        if (framesQueued > 32 || bytesQueued > 8388608) {
            queuedFrames.decrementAndGet(); queuedBytes.addAndGet(-bytes.length);
            events.emit(id, "error", "Native video backlog exceeded"); return;
        }
        enqueue(null, () -> {
            try {
                if (!active.equals(id) || handle == 0) return;
                int result = NativeVideo.decode(handle, bytes, timestamp, stripe);
                if (result < 0) { android.util.Log.w("AppleVideo", "Native decode/presentation code " + result); events.emit(id, "error", "Native decode/presentation failed (" + result + ")"); return; }
                decoded += result & 0xffff;
                if ((result & 0x10000) != 0) {
                    presented++;
                    if (presented == 1) activity.runOnUiThread(() -> { if (active.equals(id) && viewportVisible) clip.setAlpha(1); });
                }
                long now = android.os.SystemClock.elapsedRealtime();
                if (presented == 1 || now - lastReport >= 50) {
                    lastReport = now;
                    events.emit(id, "progress", "{\"decoded\":" + decoded + ",\"presented\":" + presented + "}");
                }
            } finally { queuedFrames.decrementAndGet(); queuedBytes.addAndGet(-bytes.length); }
        });
    }

    void geometry(String id, String json) {
        if (!active.equals(id)) return;
        try {
            JSONObject rect = new JSONObject(json);
            double ratio = activity.getResources().getDisplayMetrics().density;
            int[] values = new int[8];
            String[] keys = {"x", "y", "width", "height", "imageX", "imageY", "imageWidth", "imageHeight"};
            for (int i = 0; i < keys.length; i++) {
                double value = rect.getDouble(keys[i]);
                if (!Double.isFinite(value) || Math.abs(value) > 32768) return;
                values[i] = (int)Math.round(value * ratio);
            }
            viewportVisible = rect.optBoolean("visible", true);
            activity.runOnUiThread(() -> {
                if (!active.equals(id)) return;
                clip.setAlpha(viewportVisible && presented > 0 ? 1 : 0);
                FrameLayout.LayoutParams outer = new FrameLayout.LayoutParams(Math.max(1, values[2]), Math.max(1, values[3]));
                outer.leftMargin = values[0]; outer.topMargin = values[1];
                clip.setLayoutParams(outer);
                FrameLayout.LayoutParams inner = new FrameLayout.LayoutParams(Math.max(1, values[6]), Math.max(1, values[7]));
                inner.leftMargin = values[4]; inner.topMargin = values[5];
                texture.setLayoutParams(inner);
                updatePointer();
            });
        } catch (Exception ignored) { events.emit(id, "error", "Invalid video viewport"); }
    }

    void cursor(String id, double x, double y, boolean shown) {
        if (!active.equals(id) || !Double.isFinite(x) || !Double.isFinite(y)) return;
        activity.runOnUiThread(() -> {
            if (!active.equals(id)) return;
            pointerX = (float)Math.max(0, Math.min(frameWidth - 1, x));
            pointerY = (float)Math.max(0, Math.min(frameHeight - 1, y));
            pointerShown = shown;
            updatePointer();
        });
    }
    private void updatePointer() {
        FrameLayout.LayoutParams image = (FrameLayout.LayoutParams)texture.getLayoutParams();
        pointer.position(image.leftMargin + pointerX * image.width / frameWidth,
            image.topMargin + pointerY * image.height / frameHeight, pointerShown);
    }

    void close(String id) {
        if (!active.equals(id)) return;
        active = "";
        activity.runOnUiThread(() -> { if (active.isEmpty()) { clip.setAlpha(0); clip.setVisibility(View.INVISIBLE); } });
        enqueue(null, () -> { if (handle != 0) NativeVideo.destroy(handle); handle = 0; });
    }
    void destroy() {
        close(active);
        enqueue(null, () -> { if (surface != null) surface.release(); surface = null; });
        worker.shutdown();
    }

    private void enqueue(String id, Runnable operation) {
        try { worker.execute(() -> { if (id == null || active.equals(id)) operation.run(); }); }
        catch (java.util.concurrent.RejectedExecutionException full) {
            if (id != null && active.equals(id)) events.emit(id, "error", "Native video queue is full");
        }
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture value, int width, int height) {
        Surface acquired = new Surface(value);
        enqueue(null, () -> {
            if (surface != null) surface.release();
            surface = acquired;
            if (handle != 0) NativeVideo.surface(handle, surface);
        });
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture value) {
        enqueue(null, () -> {
            if (handle != 0) NativeVideo.surface(handle, null);
            if (surface != null) surface.release();
            surface = null;
        });
        return true;
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture value, int width, int height) { }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture value) { }
}

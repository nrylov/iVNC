/* Observes TextureView latches without treating native Surface posts as displayed frames. */
package dev.applescreen.prototype;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import java.security.MessageDigest;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Wraps the existing listener, preserving production Surface ownership and lifecycle. */
final class BenchmarkSurface implements TextureView.SurfaceTextureListener {
    private final TextureView.SurfaceTextureListener delegate;
    private final ArrayList<Double> intervals = new ArrayList<>();
    private long startNs, endNs = Long.MAX_VALUE, firstNs, lastNs;
    private int updates;
    BenchmarkSurface(TextureView texture) {
        delegate = texture.getSurfaceTextureListener();
        texture.setSurfaceTextureListener(this);
    }
    synchronized void begin(long measurementStart) {
        startNs = measurementStart; endNs = Long.MAX_VALUE;
        firstNs = lastNs = 0; updates = 0; intervals.clear();
    }
    synchronized JSONObject finish(long lastPostNs) throws Exception {
        endNs = System.nanoTime();
        return new JSONObject().put("textureUpdates", updates)
            .put("textureUpdateFps", updates > 1 ? (updates - 1) * 1e9 / (lastNs - firstNs) : 0)
            .put("textureIntervalsMs", new JSONArray(intervals))
            .put("observationTailMs", (endNs - lastPostNs) / 1e6);
    }
    static String pixelHash(TextureView texture) throws Exception {
        Bitmap bitmap = texture.getBitmap(1920, 1080);
        if (bitmap == null) throw new IllegalStateException("No texture bitmap");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            int[] row = new int[1920];
            byte[] bytes = new byte[1920 * 4];
            for (int y = 0; y < 1080; y++) {
                bitmap.getPixels(row, 0, 1920, 0, y, 1920, 1);
                for (int x = 0; x < row.length; x++) {
                    int value = row[x];
                    for (int channel = 0; channel < 4; channel++) bytes[x * 4 + channel] = (byte)(value >>> (channel * 8));
                }
                hash.update(bytes);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : hash.digest()) hex.append(String.format("%02x", value & 255));
            return hex.toString();
        } finally { bitmap.recycle(); }
    }
    @Override public synchronized void onSurfaceTextureUpdated(SurfaceTexture value) {
        if (delegate != null) delegate.onSurfaceTextureUpdated(value);
        long now = System.nanoTime();
        if (startNs == 0 || now < startNs || now > endNs) return;
        if (lastNs != 0) intervals.add((now - lastNs) / 1e6);
        if (firstNs == 0) firstNs = now;
        lastNs = now; updates++;
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture value, int width, int height) {
        if (delegate != null) delegate.onSurfaceTextureAvailable(value, width, height);
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture value) {
        return delegate == null || delegate.onSurfaceTextureDestroyed(value);
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture value, int width, int height) {
        if (delegate != null) delegate.onSurfaceTextureSizeChanged(value, width, height);
    }
}

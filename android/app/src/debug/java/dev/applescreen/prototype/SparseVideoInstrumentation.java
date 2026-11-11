/* Sparse-stripe, deferred-presentation and worker regression tests using real JNI and Surfaces. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Proves unchanged screen regions never block later updates of one dirty stripe. */
public final class SparseVideoInstrumentation extends Instrumentation {
    private static final String EXPECTED = "b7e75cf57deca4515f1073d6630aa691d78d9391810fc3cccb0be22e34979a9c";
    private ArrayList<byte[]> packets;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle output = new Bundle();
        try {
            try (var input = getTargetContext().getAssets().open("samples/striped444.hevc")) {
                packets = VideoIntegrationInstrumentation.accessUnits(input.readAllBytes());
            }
            JSONObject result = new JSONObject().put("eager", render(false)).put("deferred", render(true))
                .put("worker", worker());
            output.putString("stream", result.toString(2));
            finish(Activity.RESULT_OK, output);
        } catch (Throwable failure) {
            output.putString("stream", "SPARSE VIDEO TEST FAILED: " + failure);
            finish(Activity.RESULT_CANCELED, output);
        }
    }
    private JSONObject render(boolean deferred) throws Exception {
        long decoder = NativeVideo.create(320, 360, 2);
        require(decoder != 0, "Decoder unavailable");
        int decoded = 0, posted = 0;
        String hash = null;
        try (ImageReader reader = ImageReader.newInstance(320, 360, PixelFormat.RGBA_8888, 3)) {
            NativeVideo.surface(decoder, reader.getSurface());
            for (int index = 0; index < packets.size(); index++) {
                int stripe = index == 1 ? 1 : 0; // Initialize both, then update ONLY the top.
                int result = deferred ? NativeVideo.decodeOnly(decoder, packets.get(index), index, stripe)
                    : NativeVideo.decode(decoder, packets.get(index), index, stripe);
                require(result >= 0, "Decode failed: " + result);
                decoded += result & 0xffff;
                if ((result & 0x10000) != 0) { posted++; hash = pixels(reader); }
            }
            if (deferred) {
                require(posted == 0, "Decode-only unexpectedly posted a Surface buffer");
                require(NativeVideo.present(decoder) == 0x10000, "Latest composition was not posted");
                posted++; hash = pixels(reader);
            }
            require(decoded == 12 && posted == (deferred ? 1 : 11), "Sparse updates stopped: " + decoded + "/" + posted);
            require(EXPECTED.equals(hash), "Sparse composition differs from Mac reference: " + hash);
            require(NativeVideo.present(decoder) == 0, "Unchanged composition posted twice");
            NativeVideo.surface(decoder, null);
            NativeVideo.surface(decoder, reader.getSurface());
            require(NativeVideo.present(decoder) == 0x10000, "Surface reattach did not restore current pixels");
            require(EXPECTED.equals(pixels(reader)), "Surface restoration changed pixels");
        } finally { NativeVideo.destroy(decoder); }
        return new JSONObject().put("decodedStripes", decoded).put("surfacePostsBeforeReattach", posted)
            .put("rgbaSha256", hash).put("matchesMacReference", true).put("surfaceRestored", true);
    }
    private JSONObject worker() throws Exception {
        VideoWorkerTestActivity activity = (VideoWorkerTestActivity)startActivitySync(
            new Intent(getTargetContext(), VideoWorkerTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            AtomicBoolean available = new AtomicBoolean();
            for (int attempt = 0; attempt < 100 && !available.get(); attempt++) {
                runOnMainSync(() -> available.set(activity.texture.isAvailable()));
                if (!available.get()) Thread.sleep(20);
            }
            require(available.get(), "Worker TextureView not ready");
            for (int index = 0; index < packets.size(); index++) activity.video.packet(VideoWorkerTestActivity.ID,
                Base64.encodeToString(packets.get(index), Base64.NO_WRAP), index, index == 1 ? 1 : 0);
            activity.video.checkpoint(VideoWorkerTestActivity.ID, "decode");
            require(activity.completed.await(10, TimeUnit.SECONDS), "Worker failed to drain");
            require(activity.error == null, "Worker error: " + activity.error);
            require(activity.result.getInt("decoded") == 12 && activity.result.getInt("presented") > 0, "Missing coalesced output");
            Thread.sleep(150);
            AtomicReference<String> hash = new AtomicReference<>();
            runOnMainSync(() -> { try { hash.set(BenchmarkSurface.pixelHashForTest(activity.texture, 320, 360)); }
                catch (Exception error) { activity.error = error.toString(); } });
            require(EXPECTED.equals(hash.get()), "Worker pixels incorrect: " + hash.get() + " " + activity.error);
            return new JSONObject().put("decodedStripes", 12).put("surfacePosts", activity.result.getInt("presented"))
                .put("rgbaSha256", hash.get()).put("matchesMacReference", true);
        } finally { runOnMainSync(activity::finish); }
    }
    private static String pixels(ImageReader reader) throws Exception {
        Image image = null;
        for (int attempt = 0; attempt < 100 && image == null; attempt++) {
            image = reader.acquireLatestImage(); if (image == null) Thread.sleep(2);
        }
        require(image != null, "Surface output missing");
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            byte[] pixels = new byte[320 * 360 * 4];
            for (int row = 0; row < 360; row++) {
                buffer.position(row * plane.getRowStride()); buffer.get(pixels, row * 320 * 4, 320 * 4);
            }
            return VideoIntegrationInstrumentation.hex(MessageDigest.getInstance("SHA-256").digest(pixels));
        } finally { image.close(); }
    }
    private static void require(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}

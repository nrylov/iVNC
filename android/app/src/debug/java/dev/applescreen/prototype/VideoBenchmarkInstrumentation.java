/* On-device, counterbalanced replay benchmark for the existing web bridge and direct native video. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.util.Base64;
import android.view.TextureView;
import android.webkit.WebView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs without a Mac connection; writes only synthetic workload metrics and pixel hashes. */
public final class VideoBenchmarkInstrumentation extends Instrumentation {
    private Bundle options;
    private BenchmarkActivity activity;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); options = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            int seconds = option("seconds", 15, 2, 60), warmup = option("warmup", 2, 1, 10);
            int repeats = option("repeats", 3, 1, 5);
            String rates = options.getString("rates", "60,120");
            byte[][] packets = BenchmarkFrames.read(getTargetContext().getAssets().open("benchmark/1080p444.ivb"));
            Intent intent = new Intent(getTargetContext(), BenchmarkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (BenchmarkActivity)startActivitySync(intent);
            require(activity.pageReady.await(20, TimeUnit.SECONDS), "Web benchmark page did not load");
            require(activity.nativeSurfaceReady.await(10, TimeUnit.SECONDS), "Native texture unavailable");
            JSONArray encoded = new JSONArray();
            for (byte[] packet : packets) encoded.put(Base64.encodeToString(packet, Base64.NO_WRAP));
            runOnMainSync(() -> activity.load(encoded));
            require(activity.fixtureReady.await(20, TimeUnit.SECONDS), "Fixture did not preload");
            JSONObject report = new JSONObject().put("schema", 1).put("scope", "HEVC video sink replay, not full VNC")
                .put("device", Build.MANUFACTURER + " " + Build.MODEL).put("android", Build.VERSION.RELEASE)
                .put("api", Build.VERSION.SDK_INT).put("webview", WebView.getCurrentWebViewPackage().versionName)
                .put("width", 1920).put("height", 1080).put("chroma", "4:4:4")
                .put("fixturePictures", packets.length).put("seconds", seconds).put("warmupSeconds", warmup)
                .put("webCheckpointBatchPictures", 4).put("webMaximumOutstandingPictures", 16)
                .put("preferredDisplayRefreshHz", 120)
                .put("textureUpdateScope", "Callbacks after scheduled warmup boundary through final latch; not exact dropped-frame counts")
                .put("build", "Debug APK; production native decoder built with -O3")
                .put("fixtureSha256", "0833e2bc2208a9e9d7a2744c21a6aea269191b74a2e381fd2be0b5d079022b67");
            JSONArray trials = new JSONArray();
            report.put("trials", trials);
            for (String value : rates.split(",")) {
                int fps = Integer.parseInt(value);
                require(fps >= 1 && fps <= 120, "Rate must be between 1 and 120");
                String expectedHash = null;
                for (int repeat = 0; repeat < repeats; repeat++) {
                    // Alternate AB/BA to reduce systematic warm-cache and heating bias.
                    for (int index = 0; index < 2; index++) {
                        boolean nativeRoute = (repeat + index) % 2 == 0;
                        JSONObject trial = trial(nativeRoute, fps, warmup, seconds).put("repeat", repeat + 1);
                        String hash = trial.getString("finalPixelSha256");
                        if (expectedHash == null) expectedHash = hash;
                        require(expectedHash.equals(hash), "Final pixels differ between routes/repeats at " + fps + " fps");
                        trials.put(trial);
                        Bundle progress = new Bundle();
                        progress.putString("stream", trial.getString("route") + " " + fps + " fps, repeat " +
                            (repeat + 1) + ": " + trial.getInt("surfacePosts") + " posts, " +
                            trial.getJSONObject("surface").getInt("textureUpdates") + " texture updates\n");
                        sendStatus(0, progress);
                    }
                }
            }
            File directory = new File(getTargetContext().getFilesDir(), "benchmark");
            require(directory.isDirectory() || directory.mkdirs(), "Cannot create report directory");
            Files.write(new File(directory, "latest.json").toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
            result.putString("stream", "PASS: routes rendered identical final pixels; report: files/benchmark/latest.json\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "BENCHMARK FAILED: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        } finally { if (activity != null) runOnMainSync(() -> activity.finish()); }
    }
    private JSONObject trial(boolean nativeRoute, int fps, int warmup, int seconds) throws Exception {
        int warmupFrames = fps * warmup, measured = fps * seconds;
        byte[][] packets = BenchmarkFrames.read(getTargetContext().getAssets().open("benchmark/1080p444.ivb"));
        PowerManager power = getTargetContext().getSystemService(PowerManager.class);
        int thermalBefore = power.getCurrentThermalStatus();
        long start = System.nanoTime() + 750000000L;
        runOnMainSync(() -> activity.prepare(nativeRoute, fps, warmupFrames, measured, start));
        long cpuStart = Process.getElapsedCpuTime();
        JSONObject output;
        if (nativeRoute) {
            output = new JSONObject(BenchmarkNative.replay(packets, activity.nativeSurface, fps, warmupFrames, measured, start));
            require(!output.has("error"), output.optString("error"));
        } else {
            AtomicReference<Exception> failure = new AtomicReference<>();
            runOnMainSync(() -> { try { activity.runWeb(measured); } catch (Exception error) { failure.set(error); } });
            if (failure.get() != null) throw failure.get();
            require(activity.trialDone.await((warmup + seconds) * 4L + 20, TimeUnit.SECONDS), "Web replay timeout");
            require(activity.failure == null, "Web replay error: " + activity.failure);
            output = activity.webResult;
        }
        long cpuMs = Process.getElapsedCpuTime() - cpuStart;
        require(output != null, "No trial output");
        require(output.getInt("decodedPictures") == measured, "Missing decoded pictures");
        require(output.getInt("surfacePosts") == measured, "Missing Surface posts");
        // Allow the last posted buffer to latch before the untimed pixel readback.
        Thread.sleep(300);
        waitForIdleSync();
        AtomicReference<String> hash = new AtomicReference<>();
        AtomicReference<Exception> readbackError = new AtomicReference<>();
        TextureView texture = nativeRoute ? activity.nativeTexture : activity.webTexture;
        runOnMainSync(() -> { try { hash.set(BenchmarkSurface.pixelHash(texture)); } catch (Exception error) { readbackError.set(error); } });
        if (readbackError.get() != null) throw readbackError.get();
        long end = output.getLong("endNs");
        JSONObject surface = (nativeRoute ? activity.nativeObserver : activity.webObserver).finish(end);
        require(surface.getInt("textureUpdates") > 0, "No texture updates observed");
        output.put("route", nativeRoute ? "native-direct" : "web-bridge").put("targetFps", fps)
            .put("measurementSeconds", (end - start - warmupFrames * (1000000000L / fps)) / 1e9)
            .put("surface", surface).put("finalPixelSha256", hash.get())
            .put("appProcessCpuMsIncludingWarmup", cpuMs)
            .put("cpuScope", "App process only; excludes WebView renderer process")
            .put("thermalBefore", thermalBefore).put("thermalAfter", power.getCurrentThermalStatus())
            .put("displayRefreshHzAtEnd", activity.getDisplay().getRefreshRate());
        if (!nativeRoute) { runOnMainSync(() -> activity.stopWeb()); Thread.sleep(100); }
        return output;
    }
    private int option(String name, int fallback, int minimum, int maximum) {
        int value = Integer.parseInt(options.getString(name, String.valueOf(fallback)));
        require(value >= minimum && value <= maximum, "Invalid " + name);
        return value;
    }
    private static void require(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}

/* Debug-only JavaScript interface mirroring the actual app's video bridge methods. */
package dev.applescreen.prototype;

import android.webkit.JavascriptInterface;

/** Forwards the production sink to the production controller; extras are measurement fences. */
final class BenchmarkBridge {
    private final BenchmarkActivity activity;
    private final VideoController video;
    BenchmarkBridge(BenchmarkActivity activity, VideoController video) { this.activity = activity; this.video = video; }
    @JavascriptInterface public void ready() { activity.pageReady.countDown(); }
    @JavascriptInterface public void loaded() { activity.fixtureReady.countDown(); }
    @JavascriptInterface public long nowNs() { return System.nanoTime(); }
    @JavascriptInterface public void failed(String error) { activity.fail(error); }
    @JavascriptInterface public void videoStart(String id, int width, int height, int stripes) { video.start(id, width, height, stripes); }
    @JavascriptInterface public void videoPacket(String id, String data, long timestamp, int stripe) { video.packet(id, data, timestamp, stripe); }
    @JavascriptInterface public void videoGeometry(String id, String json) { video.geometry(id, json); }
    @JavascriptInterface public void videoClose(String id) { video.close(id); }
    @JavascriptInterface public void checkpoint(String id, String token) { video.checkpoint(id, token); }
}

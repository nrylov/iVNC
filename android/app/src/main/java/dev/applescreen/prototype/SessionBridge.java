/* Trusted asset bridge for TCP, SRTP datagrams, native video and Android text clipboard. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.webkit.JavascriptInterface;
import java.net.InetAddress;

/** Allows only the operations the packaged client needs; no arbitrary URLs, files or commands. */
final class SessionBridge {
    interface Events { void emit(String function, String id, String kind, String value); }
    private final Activity activity;
    private final VideoController video;
    private final Events events;
    private final ProfileStore profiles;
    private final java.util.function.Consumer<Boolean> viewerMode;
    private TcpTransport transport;
    private MediaTransport media;
    private String activeId = "", mediaId = "";
    SessionBridge(Activity activity, VideoController video, Events events, java.util.function.Consumer<Boolean> viewerMode) {
        this.activity = activity; this.video = video; this.events = events;
        this.profiles = new ProfileStore(activity); this.viewerMode = viewerMode;
    }
    @JavascriptInterface public synchronized void connect(String id, String host, int port) {
        if (transport != null) transport.close("Reconnecting");
        if (host.trim().isEmpty() || host.length() > 253 || port < 1 || port > 65535) {
            events.emit("nativeEvent", id, "close", "Invalid host or port"); return;
        }
        activeId = id;
        transport = new TcpTransport((kind, value) -> events.emit("nativeEvent", id, kind, value));
        transport.connect(host, port);
    }
    @JavascriptInterface public synchronized void send(String id, String bytes) {
        if (activeId.equals(id) && transport != null) transport.send(bytes);
    }
    @JavascriptInterface public synchronized void consumed(String id) {
        if (activeId.equals(id) && transport != null) transport.consumed();
    }
    @JavascriptInterface public synchronized void disconnect(String id) {
        if (activeId.equals(id) && transport != null) transport.close("Disconnected");
    }
    @JavascriptInterface public synchronized void mediaStart(String id) {
        if (media != null) media.close("Replacing media session");
        InetAddress peer = transport == null ? null : transport.peer();
        if (peer == null) { events.emit("mediaEvent", id, "close", "TCP peer unavailable"); return; }
        mediaId = id;
        media = new MediaTransport(peer, (kind, value) -> events.emit("mediaEvent", id, kind, value));
        media.start();
    }
    @JavascriptInterface public synchronized void mediaSend(String id, String bytes) {
        if (mediaId.equals(id) && media != null) media.send(bytes);
    }
    @JavascriptInterface public synchronized void mediaConsumed(String id) {
        if (mediaId.equals(id) && media != null) media.consumed();
    }
    @JavascriptInterface public synchronized void mediaClose(String id) {
        if (mediaId.equals(id) && media != null) media.close("Media stopped");
    }
    @JavascriptInterface public boolean softwareVideoAvailable() { return NativeVideo.AVAILABLE; }
    @JavascriptInterface public void videoStart(String id, int width, int height, int stripes) { video.start(id, width, height, stripes); }
    @JavascriptInterface public void videoPacket(String id, String bytes, long timestamp, int stripe) { video.packet(id, bytes, timestamp, stripe); }
    @JavascriptInterface public void videoGeometry(String id, String rect) { video.geometry(id, rect); }
    @JavascriptInterface public void videoCursor(String id, double x, double y, boolean shown) { video.cursor(id, x, y, shown); }
    @JavascriptInterface public void videoClose(String id) { video.close(id); }
    @JavascriptInterface public String loadProfile() { return profiles.load(); }
    @JavascriptInterface public boolean saveProfile(String text) { return profiles.save(text); }
    @JavascriptInterface public boolean forgetProfile() { return profiles.forget(); }
    @JavascriptInterface public void viewerMode(boolean full) { activity.runOnUiThread(() -> viewerMode.accept(full)); }
    @JavascriptInterface public String decoderReport() { return DecoderProbe.report(); }
    @JavascriptInterface public void getClipboard(String id) {
        activity.runOnUiThread(() -> {
            ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
            ClipData clip = clipboard.getPrimaryClip();
            String text = "";
            if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null)
                text = clip.getItemAt(0).getText().toString();
            events.emit("nativeEvent", id, "clipboard", text);
        });
    }
    @JavascriptInterface public void setClipboard(String text) {
        if (text.length() > 1024 * 1024) return;
        activity.runOnUiThread(() -> activity.getSystemService(ClipboardManager.class)
            .setPrimaryClip(ClipData.newPlainText("Remote Mac", text)));
    }
    @JavascriptInterface public void shareReport(String text) {
        activity.runOnUiThread(() -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_TEXT, text);
            activity.startActivity(android.content.Intent.createChooser(intent, "Share diagnostic report"));
        });
    }
    synchronized void suspend() {
        if (media != null) media.close("App backgrounded");
        if (transport != null) transport.close("App backgrounded");
        video.suspend();
    }
    synchronized void destroy() {
        if (media != null) media.close("Activity closed");
        if (transport != null) transport.close("Activity closed");
        video.destroy();
    }
}

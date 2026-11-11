/* Serves bundled UI modules under a secure origin and blocks all remote navigation. */
package dev.applescreen.prototype;

import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.Map;

/** Keeps the privileged JavaScript bridge available only to packaged project assets. */
final class AssetClient extends WebViewClient {
    static final String ORIGIN = "https://appassets.androidplatform.net";
    private final AssetManager assets;
    AssetClient(AssetManager assets) { this.assets = assets; }
    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
    @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        String path = uri.getPath();
        if (!ORIGIN.equals(uri.getScheme() + "://" + uri.getAuthority()) || path == null || path.contains("..")) return denied();
        try {
            String mime = (path.endsWith(".js") || path.endsWith(".mjs")) ? "text/javascript"
                : path.endsWith(".wasm") ? "application/wasm"
                : path.endsWith(".css") ? "text/css" : "text/html";
            return new WebResourceResponse(mime, "UTF-8", 200, "OK", Map.of(
                "Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; connect-src 'none'; frame-src 'none'; object-src 'none'"),
                assets.open(path.substring(1)));
        } catch (Exception missing) { return denied(); }
    }
    private WebResourceResponse denied() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", Map.of(), new ByteArrayInputStream(new byte[0]));
    }
}

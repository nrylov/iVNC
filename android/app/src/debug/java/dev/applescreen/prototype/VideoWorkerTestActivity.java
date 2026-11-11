/* Debug-only activity exercising the real coalescing video worker and TextureView. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.view.TextureView;
import java.util.concurrent.CountDownLatch;
import org.json.JSONObject;

/** Exposes only synthetic video controls to same-package instrumentation. */
public final class VideoWorkerTestActivity extends Activity {
    static final String ID = "sparse-test";
    VideoController video;
    TextureView texture;
    final CountDownLatch completed = new CountDownLatch(1);
    volatile JSONObject result;
    volatile String error;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        video = new VideoController(this, root, (id, kind, value) -> {
            if (kind.equals("error")) { error = value; completed.countDown(); }
            if (!kind.equals("checkpoint")) return;
            try {
                JSONObject snapshot = new JSONObject(value);
                // The first fence can precede the coalesced render appended by a
                // decode. The second is queued after that render, on the same worker.
                if (snapshot.getString("token").equals("decode")) video.checkpoint(ID, "render");
                else { result = snapshot; completed.countDown(); }
            } catch (Exception failure) { error = failure.toString(); completed.countDown(); }
        });
        texture = (TextureView)((FrameLayout)root.getChildAt(0)).getChildAt(0);
        setContentView(root);
        video.start(ID, 320, 360, 2);
        // Match physical texture pixels to the reference: scaling up and reading
        // back down introduces GPU interpolation differences unrelated to decoding.
        double density = getResources().getDisplayMetrics().density;
        double width = 320 / density, height = 360 / density;
        video.geometry(ID, "{\"x\":0,\"y\":0,\"width\":" + width + ",\"height\":" + height +
            ",\"imageX\":0,\"imageY\":0,\"imageWidth\":" + width + ",\"imageHeight\":" + height + "}");
    }
    @Override protected void onDestroy() { video.destroy(); super.onDestroy(); }
}

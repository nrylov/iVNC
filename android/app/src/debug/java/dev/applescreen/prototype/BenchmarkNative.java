/* Debug-only JNI entry for direct compressed-video replay without JavaScript or per-frame JNI. */
package dev.applescreen.prototype;

import android.view.Surface;

/** Keeps the benchmark library out of release builds and shares the production decoder. */
final class BenchmarkNative {
    static { if (!NativeVideo.AVAILABLE) throw new IllegalStateException("Video decoder unavailable"); System.loadLibrary("ivncbenchmark"); }
    static native String replay(byte[][] packets, Surface surface, int fps, int warmupFrames,
        int measuredFrames, long startNs);
}

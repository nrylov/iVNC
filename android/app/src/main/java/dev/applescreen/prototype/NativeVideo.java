/* JNI entry points for replaceable LGPL FFmpeg decoding; all calls use one worker. */
package dev.applescreen.prototype;

import android.view.Surface;

/** Native stream lifetime and stripe presentation API, isolated from protocol code. */
final class NativeVideo {
    static final boolean AVAILABLE;
    static {
        boolean loaded = false;
        try {
        System.loadLibrary("avutil");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("applevideo");
        loaded = true;
        } catch (LinkageError unavailable) { /* Standard mode remains usable. */ }
        AVAILABLE = loaded;
    }
    static native long create(int width, int height, int stripes);
    static native void surface(long handle, Surface surface);
    static native int decode(long handle, byte[] data, long timestamp, int stripe);
    static native void destroy(long handle);
}

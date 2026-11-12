/* Minimal JNI boundary for the independent LibVNCClient experiment. */
package dev.ivnc.nativeclient;

import android.view.Surface;

/** Native socket/protocol/render lifetime, with queued input and UI-only callbacks. */
final class NativeRfb {
    interface Events { void event(String kind,String value); }
    static { System.loadLibrary("ivncrfb"); }
    static native long create(Events events);
    static native void run(long handle,String host,int port,String username,String password,Surface surface);
    static native void stop(long handle);
    static native void destroy(long handle);
    static native void pipeline(long handle,boolean enabled);
    static native void wheel(long handle,int x,int y,int mask);
    static native void pointer(long handle,int x,int y,int buttons);
}

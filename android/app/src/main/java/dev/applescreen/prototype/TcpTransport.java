/* Direct VNC byte transport for the compatibility prototype, with bounded queues.
 * Protocol parsing remains in the bundled MPL-2.0 noVNC-ARD implementation. */
package dev.applescreen.prototype;

import android.util.Base64;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Owns one socket and bounds pending native-to-WebView and outbound data. */
final class TcpTransport {
    interface Listener { void event(String kind, String value); }
    private final Listener listener;
    private final Semaphore pendingReads = new Semaphore(8);
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0,
        TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32));
    private volatile Socket socket;
    private volatile boolean closed;

    TcpTransport(Listener listener) { this.listener = listener; }

    /** Opens off the UI thread; callbacks retain the ordering of the TCP stream. */
    void connect(String host, int port) {
        new Thread(() -> {
            try {
                Socket connection = new Socket();
                socket = connection;
                if (closed) { connection.close(); return; }
                connection.connect(new InetSocketAddress(host, port), 10000);
                connection.setTcpNoDelay(true);
                listener.event("open", "");
                byte[] buffer = new byte[32768];
                while (!closed) {
                    int count = connection.getInputStream().read(buffer);
                    if (count < 0) break;
                    pendingReads.acquire();
                    if (closed) break;
                    listener.event("data", Base64.encodeToString(
                        Arrays.copyOf(buffer, count), Base64.NO_WRAP));
                }
                close("Server closed connection");
            } catch (Exception failure) {
                close("Connection ended: " + failure.getClass().getSimpleName());
            }
        }, "vnc-reader").start();
    }

    /** Queues a bounded write without doing network I/O on the JavaScript thread. */
    void send(String encoded) {
        if (closed) return;
        if (encoded.length() > 131072) { close("Outbound packet too large"); return; }
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            writer.execute(() -> {
                try { socket.getOutputStream().write(bytes); }
                catch (Exception failure) { close("Socket write failed"); }
            });
        } catch (Exception failure) { close("Outbound queue exhausted or invalid data"); }
    }

    /** Pins UDP media to the address of the established TCP connection. */
    java.net.InetAddress peer() {
        Socket current = socket;
        return current != null && current.isConnected() && !closed ? current.getInetAddress() : null;
    }

    /** Releases exactly one slot after the WebView consumes an incoming packet. */
    void consumed() { if (!closed && pendingReads.availablePermits() < 8) pendingReads.release(); }

    /** Closes this session, releasing blocked readers and discarding queued writes. */
    synchronized void close(String reason) {
        if (closed) return;
        closed = true;
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        writer.shutdownNow();
        pendingReads.release(8);
        listener.event("close", reason);
    }
}

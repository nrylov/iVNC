/* Bounded native UDP bridge for Apple's two SRTP channels; cryptography stays in HpSession. */
package dev.applescreen.prototype;

import android.util.Base64;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;

/** Binds only one media session and accepts packets solely from its TCP peer address. */
final class MediaTransport {
    interface Events { void emit(String kind, String value); }
    private final InetAddress peer;
    private final Events events;
    private final int[] localPorts, remotePorts;
    private final DatagramSocket[] sockets = new DatagramSocket[2];
    private final ArrayBlockingQueue<byte[]> incoming = new ArrayBlockingQueue<>(512);
    private final ArrayBlockingQueue<byte[]> outgoing = new ArrayBlockingQueue<>(256);
    private final ScheduledExecutorService pump = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean awaitingAck = new AtomicBoolean();
    private volatile boolean closed;

    MediaTransport(InetAddress peer, Events events) {
        this(peer, events, new int[] {5900, 5901}, new int[] {5900, 5901});
    }
    MediaTransport(InetAddress peer, Events events, int[] localPorts, int[] remotePorts) {
        this.peer = peer; this.events = events;
        this.localPorts = localPorts.clone(); this.remotePorts = remotePorts.clone();
    }
    int localPort(int channel) { return sockets[channel].getLocalPort(); }

    void start() {
        new Thread(() -> {
            try {
                for (int channel = 0; channel < 2; channel++) {
                    if (closed) return;
                    DatagramSocket socket = new DatagramSocket(null);
                    sockets[channel] = socket;
                    socket.setReceiveBufferSize(4 * 1024 * 1024);
                    socket.bind(new InetSocketAddress(localPorts[channel]));
                    if (closed) { socket.close(); return; }
                    final int selected = channel;
                    new Thread(() -> receive(selected), "srpt-udp-" + channel).start();
                }
                if (closed) return;
                events.emit("open", "");
                pump.scheduleAtFixedRate(this::flush, 4, 4, TimeUnit.MILLISECONDS);
            } catch (Exception failure) { close("UDP bind failed: " + failure.getClass().getSimpleName()); }
        }, "srtp-bind").start();
    }

    private void receive(int channel) {
        byte[] bytes = new byte[2049];
        while (!closed) {
            try {
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                sockets[channel].receive(packet);
                // Apple uses MTU-sized packets. Bound queued bytes and ignore unrelated senders.
                if (!peer.equals(packet.getAddress()) || packet.getLength() > 2048) continue;
                byte[] framed = new byte[packet.getLength() + 1];
                framed[0] = (byte)channel;
                System.arraycopy(bytes, 0, framed, 1, packet.getLength());
                if (!incoming.offer(framed)) close("UDP receive queue exhausted");
            } catch (Exception failure) { if (!closed) close("UDP receive failed"); }
        }
    }

    private void flush() {
        if (closed) return;
        try {
            byte[] packet;
            while ((packet = outgoing.poll()) != null) {
                int channel = packet[0];
                sockets[channel].send(new DatagramPacket(packet, 1, packet.length - 1, peer, remotePorts[channel]));
            }
            if (incoming.isEmpty() || !awaitingAck.compareAndSet(false, true)) return;
            JSONArray batch = new JSONArray();
            for (int count = 0; count < 32 && (packet = incoming.poll()) != null; count++)
                batch.put(Base64.encodeToString(packet, Base64.NO_WRAP));
            events.emit("data", batch.toString());
        } catch (Exception failure) { close("UDP send/pump failed"); }
    }

    void send(String encoded) {
        if (closed || encoded.length() > 4096) return;
        try {
            byte[] packet = Base64.decode(encoded, Base64.DEFAULT);
            if (packet.length < 2 || packet[0] < 0 || packet[0] > 1) return;
            if (!outgoing.offer(packet)) close("UDP feedback queue exhausted");
        } catch (IllegalArgumentException invalid) { close("Invalid UDP feedback"); }
    }
    void consumed() { awaitingAck.set(false); }
    synchronized void close(String reason) {
        if (closed) return;
        closed = true;
        for (DatagramSocket socket : sockets) if (socket != null) socket.close();
        pump.shutdownNow();
        incoming.clear(); outgoing.clear();
        events.emit("close", reason);
    }
}

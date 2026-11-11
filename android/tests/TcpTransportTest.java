/* Local JVM integration test for the actual native TCP transport against loopback. */
package dev.applescreen.prototype;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class TcpTransportTest {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            byte[] payload = "clipboard café 🔬\n".getBytes(StandardCharsets.UTF_8);
            Thread echo = new Thread(() -> {
                try (var socket = server.accept()) {
                    byte[] received = socket.getInputStream().readNBytes(payload.length);
                    if (!java.util.Arrays.equals(payload, received)) throw new AssertionError("Write changed bytes");
                    socket.getOutputStream().write(received);
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            echo.start();
            CountDownLatch opened = new CountDownLatch(1);
            CountDownLatch received = new CountDownLatch(1);
            CountDownLatch closed = new CountDownLatch(1);
            AtomicReference<byte[]> output = new AtomicReference<>();
            TcpTransport transport = new TcpTransport((kind, value) -> {
                switch (kind) {
                    case "open" -> opened.countDown();
                    case "data" -> {
                        output.set(java.util.Base64.getDecoder().decode(value));
                        received.countDown();
                    }
                    case "close" -> closed.countDown();
                }
            });
            transport.connect("127.0.0.1", server.getLocalPort());
            if (!opened.await(3, TimeUnit.SECONDS)) throw new AssertionError("No open callback");
            transport.send(java.util.Base64.getEncoder().encodeToString(payload));
            if (!received.await(3, TimeUnit.SECONDS)) throw new AssertionError("No data callback");
            transport.consumed();
            if (!java.util.Arrays.equals(payload, output.get())) throw new AssertionError("Read changed bytes");
            if (!closed.await(3, TimeUnit.SECONDS)) throw new AssertionError("No EOF callback");
            transport.close("idempotent close");
            echo.join(3000);
            System.out.println("TCP loopback: exact UTF-8 bytes, EOF and repeated close passed");
        }
    }
}

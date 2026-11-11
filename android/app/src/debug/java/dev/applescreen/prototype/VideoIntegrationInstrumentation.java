/* Debug-only end-to-end tests for JNI stripe decoding, Surface pixels, and the UDP bridge. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exercises the actual shared decoder/JNI/render path and real on-device datagram sockets. */
public final class VideoIntegrationInstrumentation extends Instrumentation {
    private static final String EXPECTED_RGBA_SHA256 = "fabd6760a0ad12a5e4354adad79df8cc5b46b35e72330aa8e6416293af5219d2";
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            JSONObject report = new JSONObject();
            report.put("nativeLibrariesLoaded", NativeVideo.AVAILABLE);
            report.put("stripedRendering", render());
            report.put("udpBridge", media());
            result.putString("stream", report.toString(2));
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "INTEGRATION TEST FAILED: " + failure);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private JSONObject render() throws Exception {
        require(NativeVideo.AVAILABLE, "Native libraries failed to load");
        require(NativeVideo.create(0, 180, 1) == 0, "Invalid video dimensions accepted");
        byte[] data;
        try (var input = getTargetContext().getAssets().open("samples/striped444.hevc")) { data = input.readAllBytes(); }
        ArrayList<byte[]> packets = accessUnits(data);
        require(packets.size() == 12, "Expected 12 encoded access units");
        long decoder = NativeVideo.create(320, 360, 2);
        require(decoder != 0, "Could not create native decoder");
        int decoded = 0, presented = 0;
        byte[] lastPixels = null;
        try (ImageReader reader = ImageReader.newInstance(320, 360, PixelFormat.RGBA_8888, 3)) {
            NativeVideo.surface(decoder, reader.getSurface());
            for (int index = 0; index < packets.size(); index++) {
                int code = NativeVideo.decode(decoder, packets.get(index), index, index % 2);
                require(code >= 0, "Decode failed with code " + code);
                decoded += code & 0xffff;
                if ((code & 0x10000) == 0) continue;
                presented++;
                Image image = null;
                for (int retry = 0; retry < 100 && image == null; retry++) {
                    image = reader.acquireLatestImage();
                    if (image == null) Thread.sleep(2);
                }
                require(image != null, "Surface did not produce a complete picture");
                try {
                    Image.Plane plane = image.getPlanes()[0];
                    require(plane.getPixelStride() == 4, "Unexpected RGBA pixel stride");
                    ByteBuffer pixels = plane.getBuffer();
                    lastPixels = new byte[320 * 360 * 4];
                    for (int row = 0; row < 360; row++) {
                        pixels.position(row * plane.getRowStride());
                        pixels.get(lastPixels, row * 320 * 4, 320 * 4);
                    }
                } finally { image.close(); }
            }
        } finally { NativeVideo.destroy(decoder); }
        require(decoded == 12 && presented == 11, "Incorrect decoded/presented count: " + decoded + "/" + presented);
        String hash = hex(MessageDigest.getInstance("SHA-256").digest(lastPixels));
        require(EXPECTED_RGBA_SHA256.equals(hash), "Rendered RGBA differs from Mac reference: " + hash);
        return new JSONObject().put("decodedStripes", decoded).put("presentedPictures", presented)
            .put("rgbaSha256", hash).put("matchesMacReference", true);
    }
    private JSONObject media() throws Exception {
        CountDownLatch opened = new CountDownLatch(1), arrived = new CountDownLatch(1);
        AtomicReference<String> batch = new AtomicReference<>();
        InetAddress localhost = InetAddress.getByName("127.0.0.1");
        try (DatagramSocket audio = new DatagramSocket(0); DatagramSocket video = new DatagramSocket(0)) {
            MediaTransport transport = new MediaTransport(localhost, (kind, value) -> {
                if (kind.equals("open")) opened.countDown();
                if (kind.equals("data")) { batch.set(value); arrived.countDown(); }
            }, new int[] {0, 0}, new int[] {audio.getLocalPort(), video.getLocalPort()});
            try {
                transport.start();
                require(opened.await(3, TimeUnit.SECONDS), "UDP bridge did not open");
                byte[] payload = {0, 127, (byte)255};
                video.send(new DatagramPacket(payload, payload.length, localhost, transport.localPort(1)));
                require(arrived.await(3, TimeUnit.SECONDS), "UDP receive batch missing");
                byte[] incoming = Base64.decode(new JSONArray(batch.get()).getString(0), Base64.DEFAULT);
                require(Arrays.equals(incoming, new byte[] {1, 0, 127, (byte)255}), "UDP channel framing changed");
                transport.consumed();
                transport.send(Base64.encodeToString(new byte[] {0, 5, 6}, Base64.NO_WRAP));
                audio.setSoTimeout(3000);
                DatagramPacket reply = new DatagramPacket(new byte[16], 16);
                audio.receive(reply);
                require(reply.getLength() == 2 && reply.getData()[0] == 5 && reply.getData()[1] == 6,
                    "UDP feedback changed bytes or channel");
            } finally { transport.close("Test finished"); }
        }
        return new JSONObject().put("receivedExactBytes", true).put("feedbackExactBytes", true);
    }
    static ArrayList<byte[]> accessUnits(byte[] data) {
        ArrayList<Integer> boundaries = new ArrayList<>();
        for (int offset = 0; offset + 5 < data.length;) {
            int prefix = data[offset] == 0 && data[offset + 1] == 0
                ? (data[offset + 2] == 1 ? 3 : data[offset + 2] == 0 && data[offset + 3] == 1 ? 4 : 0) : 0;
            if (prefix == 0) { offset++; continue; }
            if (((data[offset + prefix] & 0xff) >> 1 & 0x3f) == 35) boundaries.add(offset);
            offset += prefix + 2;
        }
        ArrayList<byte[]> units = new ArrayList<>();
        for (int index = 0; index < boundaries.size(); index++)
            units.add(Arrays.copyOfRange(data, index == 0 ? 0 : boundaries.get(index),
                index + 1 < boundaries.size() ? boundaries.get(index + 1) : data.length));
        return units;
    }
    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format("%02x", value & 0xff));
        return text.toString();
    }
    private static void require(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}

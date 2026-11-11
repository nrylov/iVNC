/* Local regression checks for malformed and bounded synthetic benchmark input. */
package dev.applescreen.prototype;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

/** Exercises the actual Android fixture parser using only Java standard-library APIs. */
public final class BenchmarkFramesTest {
    public static void main(String[] args) throws Exception {
        byte[] fixture = Files.readAllBytes(Path.of(args[0]));
        byte[][] packets = BenchmarkFrames.read(new ByteArrayInputStream(fixture));
        require(packets.length == 90, "Expected 90 generated pictures");
        String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixture));
        require(hash.equals("0833e2bc2208a9e9d7a2744c21a6aea269191b74a2e381fd2be0b5d079022b67"), "Fixture changed without updated provenance");
        reject(new byte[0]);
        reject(ByteBuffer.allocate(8).putInt(0).putInt(1).array());
        reject(ByteBuffer.allocate(8).putInt(0x49564e43).putInt(601).array());
        reject(ByteBuffer.allocate(12).putInt(0x49564e43).putInt(1).putInt(4194305).array());
        reject(Arrays.copyOf(fixture, fixture.length - 1));
        reject(Arrays.copyOf(fixture, fixture.length + 1));
        System.out.println("Benchmark fixture parser: valid 90-picture fixture + six malformed inputs passed");
    }
    private static void reject(byte[] bytes) throws Exception {
        try { BenchmarkFrames.read(new ByteArrayInputStream(bytes)); }
        catch (java.io.IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed fixture accepted");
    }
    private static void require(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}

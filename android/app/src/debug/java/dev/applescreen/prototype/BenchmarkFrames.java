/* Parses the bounded synthetic access-unit fixture; no private screen data is collected. */
package dev.applescreen.prototype;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;

/** Provides identical preloaded compressed pictures to both routes, outside timed work. */
final class BenchmarkFrames {
    static byte[][] read(InputStream source) throws Exception {
        try (DataInputStream input = new DataInputStream(source)) {
            if (input.readInt() != 0x49564e43) throw new IllegalArgumentException("Invalid iVNC fixture");
            int count = input.readInt();
            if (count < 1 || count > 600) throw new IllegalArgumentException("Invalid picture count");
            ArrayList<byte[]> packets = new ArrayList<>();
            int total = 0;
            for (int index = 0; index < count; index++) {
                int size = input.readInt();
                if (size < 1 || size > 4194304 || (total += size) > 33554432)
                    throw new IllegalArgumentException("Fixture exceeds benchmark bounds");
                byte[] bytes = input.readNBytes(size);
                if (bytes.length != size) throw new IllegalArgumentException("Truncated fixture");
                packets.add(bytes);
            }
            if (input.read() != -1) throw new IllegalArgumentException("Trailing fixture bytes");
            return packets.toArray(new byte[0][]);
        }
    }
}

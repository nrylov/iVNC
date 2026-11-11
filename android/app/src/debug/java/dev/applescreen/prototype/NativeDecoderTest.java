/* Direct Android decoder experiment using generated, non-private control clips.
 * These clips are x265 samples, not captures of the owner's Apple session. */
package dev.applescreen.prototype;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;

/** Attempts real decoding even when the advertised profile list is inconclusive. */
final class NativeDecoderTest {
    private static final long DECODE_TIMEOUT_MS = 3000;
    private static final int EXPECTED_FRAMES = 12;

    static JSONObject run(Context context) throws Exception {
        JSONObject report = new JSONObject();
        report.put("sampleOrigin", "Synthetic x265 test pattern; not an Apple session capture");
        report.put("sampleSize", "320x180, 12 frames");
        report.put("interpretation", "Output frames prove this sample was decoded, not real-time Apple stream compatibility or visual correctness.");
        JSONArray results = new JSONArray();
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder() || info.isAlias()) continue;
            boolean hevc = false;
            for (String type : info.getSupportedTypes()) if (type.equalsIgnoreCase("video/hevc")) hevc = true;
            if (!hevc) continue;
            for (String sample : new String[] { "hevc420.mp4", "hevc444.mp4" })
                results.put(decode(context, info, sample));
        }
        report.put("results", results);
        return report;
    }

    private static JSONObject decode(Context context, MediaCodecInfo info, String sample) throws Exception {
        JSONObject result = new JSONObject();
        result.put("decoder", info.getName());
        result.put("hardware", info.isHardwareAccelerated());
        result.put("sample", sample);
        result.put("chroma", sample.contains("444") ? "4:4:4 RExt high-tier level 5.1" : "4:2:0 Main control");
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        int inputs = 0, frames = 0;
        long outputBytes = 0;
        long started = SystemClock.elapsedRealtime();
        String stage = "extractor";
        boolean outputEos = false;
        try (AssetFileDescriptor asset = context.getAssets().openFd("samples/" + sample)) {
            extractor.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
            extractor.selectTrack(0);
            MediaFormat format = extractor.getTrackFormat(0);
            stage = "create";
            codec = MediaCodec.createByCodecName(info.getName());
            stage = "configure";
            codec.configure(format, null, null, 0);
            stage = "start";
            codec.start();
            stage = "decode";
            boolean inputEos = false;
            MediaCodec.BufferInfo buffer = new MediaCodec.BufferInfo();
            long deadline = SystemClock.elapsedRealtime() + DECODE_TIMEOUT_MS;
            while (!outputEos && SystemClock.elapsedRealtime() < deadline) {
                if (!inputEos) {
                    int index = codec.dequeueInputBuffer(1000);
                    if (index >= 0) {
                        ByteBuffer input = codec.getInputBuffer(index);
                        if (input == null) throw new IllegalStateException("Null input buffer");
                        input.clear();
                        int size = extractor.readSampleData(input, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEos = true;
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
                            inputs++;
                            extractor.advance();
                        }
                    }
                }
                int index = codec.dequeueOutputBuffer(buffer, 1000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
                    result.put("outputFormat", codec.getOutputFormat().toString());
                if (index >= 0) {
                    if (buffer.size > 0 && (buffer.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        frames++;
                        outputBytes += buffer.size;
                    }
                    outputEos = (buffer.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(index, false);
                }
            }
            result.put("outcome", frames == EXPECTED_FRAMES && outputEos ? "decoded-all-frames"
                : frames > 0 ? "partial-output" : "no-output");
        } catch (Exception failure) {
            result.put("outcome", "rejected-or-failed");
            result.put("failedStage", stage);
            result.put("errorClass", failure.getClass().getSimpleName());
            if (failure instanceof MediaCodec.CodecException error) result.put("diagnostic", error.getDiagnosticInfo());
            result.put("message", String.valueOf(failure.getMessage()));
        } finally {
            extractor.release();
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) { }
                codec.release();
            }
        }
        result.put("inputFrames", inputs);
        result.put("outputFrames", frames);
        result.put("outputBytes", outputBytes);
        result.put("endOfStream", outputEos);
        result.put("elapsedMs", SystemClock.elapsedRealtime() - started);
        return result;
    }
}

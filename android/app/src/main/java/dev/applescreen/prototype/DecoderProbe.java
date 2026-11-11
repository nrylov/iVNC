/* Native HEVC capability inventory; advertised capabilities are not a decode test. */
package dev.applescreen.prototype;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** Collects device and HEVC codec facts without inferring unsupported 4:4:4 support. */
final class DecoderProbe {
    static String report() {
        JSONObject report = new JSONObject();
        try {
            report.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            report.put("android", Build.VERSION.RELEASE);
            report.put("api", Build.VERSION.SDK_INT);
            JSONArray codecs = new JSONArray();
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (!type.equalsIgnoreCase("video/hevc")) continue;
                    JSONObject codec = new JSONObject();
                    codec.put("name", info.getName());
                    codec.put("hardware", info.isHardwareAccelerated());
                    JSONArray profiles = new JSONArray();
                    for (MediaCodecInfo.CodecProfileLevel level : info.getCapabilitiesForType(type).profileLevels) {
                        profiles.put(new JSONObject().put("profile", level.profile).put("level", level.level));
                    }
                    codec.put("profiles", profiles);
                    codecs.put(codec);
                }
            }
            report.put("hevcDecoders", codecs);
            report.put("conclusion", "Inventory only. Apple HEVC RExt 4:4:4 requires a live stream decode test.");
        } catch (Exception failure) {
            try { report.put("error", failure.getClass().getSimpleName()); } catch (Exception ignored) { }
        }
        return report.toString();
    }
}

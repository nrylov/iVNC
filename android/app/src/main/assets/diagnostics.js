/* User-shareable compatibility report; excludes addresses, credentials and clipboard contents. */
import { DEFAULT_CODEC } from './ard/hp/hevc.js';

/** Binds diagnostic actions and reports the actual native session state. */
export function attachDiagnostics(getState) {
    let text = '';
    async function refresh() {
        const report = JSON.parse(Native.decoderReport());
        report.appVersion = '0.5-saved-connections';
        report.webview = navigator.userAgent;
        report.appleCodec = DEFAULT_CODEC;
        report.nativeSoftwareAvailable = Native.softwareVideoAvailable();
        report.highPerformanceTransport = 'Direct Android UDP with upstream SRTP authentication';
        report.session = getState();
        report.liveDecodeTest = report.session.presentedFrames > 0
            ? 'Native renderer presented complete Apple session pictures' : 'Not yet verified in a live Apple session';
        try {
            report.webCodecsAdvertisedSupport = typeof VideoDecoder !== 'undefined'
                && (await VideoDecoder.isConfigSupported({ codec: DEFAULT_CODEC, optimizeForLatency: true,
                    hardwareAcceleration: 'prefer-hardware' })).supported;
        } catch { report.webCodecsAdvertisedSupport = false; }
        text = JSON.stringify(report, null, 2);
        document.getElementById('report').textContent = text;
    }
    document.getElementById('probe').onclick = refresh;
    document.getElementById('share').onclick = async () => { await refresh(); Native.shareReport(text); };
    refresh();
}

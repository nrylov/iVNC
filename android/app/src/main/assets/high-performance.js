/* Android sink/transport adaptation of noVNC-ARD's _ardHpStart (MPL-2.0).
 * Source: ard/ard-patch.js. The upstream negotiation/encryption implementation is retained. */
import RFB from './noVNC/core/rfb.js';
import { HpSession } from './ard/hp/session.js';
import { NativeMediaChannel } from './native-media.js';
import { NativeVideoSink } from './native-video.js';

RFB.prototype._ardHpStart = function () {
    if (this._ardHp) return;
    // Media keys must never be offered without full session encryption.
    if (!this._ardAuthKey || this._ardEncryptionMode !== 2) {
        this._ardHpOnState('failed', 'no-encryption');
        return;
    }
    this._ardHp = new HpSession({
        mediaUrl: 'native:udp',
        WebSocketImpl: NativeMediaChannel,
        holdOffer: this._ardEncryptionAwaited(),
        sendOffer: bytes => RFB.messages.ardMediaStreamNegotiation(this._sock, bytes),
        createSink: options => new NativeVideoSink({ ...options, target: this._canvas }),
        createAudio: null,
        onState: (state, reason) => this._ardHpOnState(state, reason),
        onResize: active => this._ardHpResizeVisual(active),
    });
    this._ardHp.start();
};

/** Applies the selected display strategy before the server's initialization is processed. */
export function configureVideo(rfb, mode) {
    const high = mode !== 'standard';
    rfb._ardQualityPreset = high ? 'highperf' : 'adaptive';
    rfb._ardHpAllowed = high;
    rfb._ardHpMediaUrl = high ? 'native:udp' : null;
    rfb._ardHpVirtualDisplay = true;
    rfb._ardHpDisplay = high ? { width: 1920, height: 1080, scale: 1 } : null;
    rfb._ardVirtualLogin = high ? 2 : 0;
    rfb._ardHpAudio = false;
}

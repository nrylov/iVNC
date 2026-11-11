/* WebSocket-shaped UDP adapter for HpSession. SRTP remains encrypted across the bridge. */
import { encodeBytes, decodeBytes } from './binary.js';
const channels = new Map();
window.mediaEvent = (id, kind, value) => channels.get(id)?.receive(kind, value);

/** Presents the project's native UDP ports as the media channel HpSession expects. */
export class NativeMediaChannel {
    constructor() {
        this.id = crypto.randomUUID();
        this.protocol = 'ard-media';
        this.binaryType = 'arraybuffer';
        this.readyState = 0;
        this.onopen = this.onmessage = this.onclose = this.onerror = () => {};
        channels.set(this.id, this);
        setTimeout(() => { if (this.readyState === 0) Native.mediaStart(this.id); }, 0);
    }
    receive(kind, value) {
        if (this.readyState === 3) return;
        if (kind === 'open') { this.readyState = 1; this.onopen(); }
        if (kind === 'data') {
            try {
                for (const packet of JSON.parse(value)) this.onmessage({ data: decodeBytes(packet).buffer });
            } finally { Native.mediaConsumed(this.id); }
        }
        if (kind === 'close') {
            this.readyState = 3;
            channels.delete(this.id);
            this.onclose({ reason: value });
        }
    }
    send(packet) {
        if (this.readyState === 1) Native.mediaSend(this.id, encodeBytes(packet));
    }
    close() {
        if (this.readyState === 3) return;
        this.readyState = 3;
        channels.delete(this.id);
        Native.mediaClose(this.id);
    }
}

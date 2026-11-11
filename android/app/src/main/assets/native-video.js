/* Native HEVC sink implementing the existing HpSession video-sink contract. */
import { encodeBytes } from './binary.js';
const sinks = new Map();
window.videoEvent = (id, kind, value) => sinks.get(id)?.receive(kind, value);

/** Sends compressed stripes to the native worker; complete pictures render into TextureView. */
export class NativeVideoSink {
    constructor(options) {
        this.id = crypto.randomUUID();
        this.options = options;
        this.closed = false;
        this.started = false;
        this.shown = false;
        this.stats = { submitted: 0, decoded: 0, presented: 0, dropped: 0 };
        sinks.set(this.id, this);
        Native.videoStart(this.id, options.width, options.height, options.stripes);
        this.align = () => {
            if (this.closed) return;
            const canvas = options.target.getBoundingClientRect();
            const screen = document.getElementById('screen').getBoundingClientRect();
            const x = Math.max(0, screen.left), y = Math.max(0, screen.top);
            const width = Math.max(0, Math.min(innerWidth, screen.right) - x);
            const height = Math.max(0, Math.min(innerHeight, screen.bottom) - y);
            const geometry = JSON.stringify({ x, y, width, height,
                imageX: canvas.left - x, imageY: canvas.top - y,
                imageWidth: canvas.width, imageHeight: canvas.height,
                visible: width > 0 && height > 0 && !document.querySelector('dialog[open]'),
            });
            if (geometry !== this.geometry) { this.geometry = geometry; Native.videoGeometry(this.id, geometry); }
            this.animation = requestAnimationFrame(this.align);
        };
        this.align();
    }
    push(picture) {
        if (this.closed) return;
        if (!this.started && !picture.key) { this.stats.dropped++; return; }
        this.started = true;
        this.stats.submitted++;
        Native.videoPacket(this.id, encodeBytes(picture.data), picture.don, picture.stripe);
    }
    receive(kind, value) {
        if (this.closed) return;
        if (kind === 'error') { this.options.onError?.(value); return; }
        if (kind !== 'progress') return;
        Object.assign(this.stats, JSON.parse(value));
        if (this.stats.presented > 0 && !this.shown) {
            this.shown = true;
            this.options.onFirstFrame?.();
            this.options.onFirstPicture?.();
            window.dispatchEvent(new Event('desktopgeometry'));
        }
    }
    get backlog() { return Math.max(0, this.stats.submitted - this.stats.decoded); }
    close() {
        if (this.closed) return;
        this.closed = true;
        cancelAnimationFrame(this.animation);
        sinks.delete(this.id);
        Native.videoClose(this.id);
    }
}

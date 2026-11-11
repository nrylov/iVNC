/* WebSocket-shaped adapter for the project-local native TCP bridge.
 * Uses noVNC's public raw-channel interface; buffers bytes until handlers attach. */
export class NativeChannel {
    constructor(bridge, host, port) {
        this.id = crypto.randomUUID();
        this.bridge = bridge;
        this.binaryType = 'arraybuffer';
        this.protocol = '';
        this.readyState = 'connecting';
        this.onopen = () => {};
        this.onmessage = () => {};
        this.onerror = () => {};
        this.onclose = () => {};
        // RFB installs event handlers asynchronously, before this next task.
        setTimeout(() => {
            if (this.readyState === 'connecting') bridge.connect(this.id, host, port);
        }, 0);
    }
    receive(kind, value) {
        if (this.readyState === 'closed') return;
        if (kind === 'open') { this.readyState = 'open'; this.onopen(); }
        if (kind === 'data') {
            try {
                const bytes = Uint8Array.from(atob(value), char => char.charCodeAt(0));
                this.onmessage({ data: bytes.buffer });
            } finally { this.bridge.consumed(this.id); }
        }
        if (kind === 'close') {
            this.readyState = 'closed';
            this.onclose({ code: 1000, reason: value });
        }
    }
    send(data) {
        if (this.readyState !== 'open') throw new Error('Transport is not open');
        const bytes = data instanceof ArrayBuffer ? new Uint8Array(data)
            : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
        let binary = '';
        for (let offset = 0; offset < bytes.length; offset += 8192)
            binary += String.fromCharCode(...bytes.subarray(offset, offset + 8192));
        this.bridge.send(this.id, btoa(binary));
    }
    close() {
        if (this.readyState === 'closed') return;
        this.readyState = 'closed';
        this.bridge.disconnect(this.id);
    }
}

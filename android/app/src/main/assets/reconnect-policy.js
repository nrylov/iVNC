/* Foreground reconnection state, separated from sockets so intentional disconnect stays authoritative. */
/** Retains only process-memory connection intent and never retries failed authentication. */
export class ReconnectPolicy {
    constructor({ connect, disconnect }) {
        this.connect = connect;
        this.disconnect = disconnect;
        this.desired = null;
        this.enabled = true;
        this.foreground = true;
        this.active = false;
        this.manual = false;
    }
    attempt(configuration) {
        this.desired = { ...configuration };
        this.active = true;
        this.manual = false;
    }
    ended() { this.active = false; }
    pause() {
        if (!this.foreground) return;
        this.foreground = false;
        if (!this.desired || this.manual) return;
        this.active = false;
        this.disconnect();
    }
    resume() {
        const returning = !this.foreground;
        this.foreground = true;
        if (!returning || this.active || this.manual || !this.enabled || !this.desired) return;
        this.active = true;
        this.connect({ ...this.desired });
    }
    stop() {
        this.desired = null; this.active = false; this.manual = true;
        this.disconnect();
    }
    authenticationFailed() { this.desired = null; this.active = false; this.manual = true; }
}

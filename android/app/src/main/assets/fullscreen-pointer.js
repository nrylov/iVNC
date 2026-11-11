/* Full-screen relative pointing delegates multi-touch to the independent zoom/pan layer. */
/** Prevents a pinch gesture from clicking or moving the remote cursor accidentally. */
export class FullscreenPointer {
    constructor(model) { this.model = model; this.contacts = new Set(); this.maximum = 0; }
    down(id, x, y, time) {
        if (!this.contacts.size) this.maximum = 0;
        this.contacts.add(id);
        this.maximum = Math.max(this.maximum, this.contacts.size);
        if (this.maximum === 1) this.model.down(id, x, y, time);
        else this.model.cancel();
    }
    move(id, x, y) {
        if (this.maximum === 1 && this.contacts.has(id)) this.model.move(id, x, y);
    }
    up(id, time) {
        if (this.maximum === 1) this.model.up(id, time);
        this.contacts.delete(id);
        if (!this.contacts.size) this.maximum = 0;
    }
    cancel() { this.contacts.clear(); this.maximum = 0; this.model.cancel(); }
}

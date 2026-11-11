/* Relative-pointer gesture state, independent of DOM and the pinned RFB adapter. */
const TAP_DISTANCE = 10;
const TAP_TIME_MS = 350;

/** Converts trackpad contacts into bounded cursor moves, clicks and scrolls. */
export class TrackpadModel {
    constructor({ send, scroll, bounds, sensitivity = () => 1 }) {
        this.send = send;
        this.scroll = scroll;
        this.bounds = bounds;
        this.sensitivity = sensitivity;
        this.points = new Map();
        this.x = null;
        this.y = null;
        this.dragging = false;
        this.distance = 0;
        this.maxFingers = 0;
    }
    clamp() {
        const { width, height } = this.bounds();
        this.x = Math.max(0, Math.min(width - 1, this.x ?? width / 2));
        this.y = Math.max(0, Math.min(height - 1, this.y ?? height / 2));
    }
    emit(mask = this.dragging ? 1 : 0) {
        this.clamp();
        this.send(this.x, this.y, mask);
    }
    down(id, x, y, time) {
        if (this.points.size === 0) {
            this.started = time;
            this.distance = 0;
            this.maxFingers = 0;
        }
        this.points.set(id, { x, y });
        this.maxFingers = Math.max(this.maxFingers, this.points.size);
    }
    move(id, x, y) {
        const previous = this.points.get(id);
        if (!previous) return;
        const dx = x - previous.x;
        const dy = y - previous.y;
        this.points.set(id, { x, y });
        this.distance += Math.hypot(dx, dy);
        if (this.points.size >= 2) {
            this.clamp();
            // Each contact supplies its half of the centroid's displacement.
            this.scroll(this.x, this.y, -dx / this.points.size, -dy / this.points.size);
        } else if (this.maxFingers === 1) {
            this.clamp();
            this.x += dx * this.sensitivity();
            this.y += dy * this.sensitivity();
            this.emit();
        }
    }
    up(id, time) {
        if (!this.points.delete(id) || this.points.size !== 0) return;
        if (this.maxFingers <= 2 && time - this.started <= TAP_TIME_MS && this.distance <= TAP_DISTANCE)
            this.click(this.maxFingers === 2 ? 4 : 1);
    }
    click(button) {
        const held = this.dragging ? 1 : 0;
        if (held & button) return;
        this.emit(held | button);
        this.emit(held);
    }
    setDragging(value) {
        this.dragging = value;
        this.emit();
    }
    cancel() {
        this.points.clear();
        this.maxFingers = 0;
        if (this.dragging) this.setDragging(false);
    }
}

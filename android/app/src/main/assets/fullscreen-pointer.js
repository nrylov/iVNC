/* Whole-desktop trackpad with independent two-finger scrolling and pinch recognition. */
/** Keeps multi-touch from clicking the Mac or moving its cursor accidentally. */
export class FullscreenPointer {
    constructor(model, pinch = () => {}) {
        this.model = model; this.pinch = pinch; this.points = new Map(); this.maximum = 0;
    }
    geometry() {
        const [a, b] = [...this.points.values()];
        return {x:(a.x+b.x)/2, y:(a.y+b.y)/2, distance:Math.max(1,Math.hypot(a.x-b.x,a.y-b.y))};
    }
    down(id, x, y, time) {
        if (!this.points.size) { this.maximum = 0; this.started = time; this.mode = 'pending'; }
        this.points.set(id, {x,y}); this.maximum = Math.max(this.maximum, this.points.size);
        if (this.maximum === 1) this.model.down(id,x,y,time);
        else {
            this.model.cancel();
            if (this.points.size === 2) { this.origin = this.previous = this.geometry(); }
            else this.mode = 'cancelled';
        }
    }
    move(id, x, y) {
        if (!this.points.has(id)) return;
        this.points.set(id,{x,y});
        if (this.maximum === 1) { this.model.move(id,x,y); return; }
        if (this.points.size !== 2) { this.mode = 'cancelled'; return; }
        if (this.mode === 'cancelled') return;
        const now = this.geometry();
        if (this.mode === 'pending') {
            if (Math.abs(now.distance-this.origin.distance) > 10) {
                this.mode = 'pinch'; this.pinch({...this.origin,phase:'start'});
            } else if (Math.hypot(now.x-this.origin.x,now.y-this.origin.y) > 8) this.mode = 'scroll';
        }
        if (this.mode === 'pinch') this.pinch({...now,phase:'move'});
        if (this.mode === 'scroll') {
            this.model.clamp(); this.model.scroll(this.model.x,this.model.y,this.previous.x-now.x,this.previous.y-now.y);
        }
        this.previous = now;
    }
    up(id, time) {
        if (!this.points.has(id)) return;
        if (this.maximum === 1) this.model.up(id,time);
        this.points.delete(id);
        if (!this.points.size) {
            if (this.maximum === 2 && this.mode === 'pending' && time-this.started <= 350) this.model.click(4);
            this.maximum = 0;
        }
    }
    cancel() { this.points.clear(); this.maximum = 0; this.mode = 'cancelled'; this.model.cancel(); }
}

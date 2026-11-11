/* Android trackpad UI adapter for pinned noVNC-ARD. No upstream files are changed.
 * Private RFB pointer/display APIs are confined here; ARD retains button mapping. */
import { TrackpadModel } from './trackpad-model.js';

/** Attaches relative input, separate click/drag controls, and a visible cursor. */
export function attachTrackpad({ getRfb, isConnected }) {
    const pad = document.getElementById('trackpad');
    const screen = document.getElementById('screen');
    const mode = document.getElementById('pointer-mode');
    const drag = document.getElementById('drag');
    const cursor = document.createElement('div');
    cursor.className = 'remote-pointer';
    cursor.setAttribute('aria-hidden', 'true');
    screen.append(cursor);
    const model = new TrackpadModel({
        bounds: () => {
            const display = getRfb()?._display;
            return { width: Math.max(1, display?.width || 1), height: Math.max(1, display?.height || 1) };
        },
        sensitivity: () => {
            const display = getRfb()?._display;
            return Number(document.getElementById('sensitivity').value) / (display?.scale || 1);
        },
        send: (x, y, buttons) => {
            if (!isConnected()) return;
            const rfb = getRfb();
            // _sendMouse accepts scaled canvas coordinates and applies ARD button mapping.
            rfb._sendMouse(x * rfb._display.scale, y * rfb._display.scale, buttons);
            paint();
        },
        scroll: (x, y, dx, dy) => {
            if (!isConnected()) return;
            const rfb = getRfb();
            const box = rfb._canvas.getBoundingClientRect();
            rfb._handleWheel(new WheelEvent('wheel', {
                clientX: box.left + x * rfb._display.scale,
                clientY: box.top + y * rfb._display.scale,
                deltaX: dx, deltaY: dy, deltaMode: 0,
            }));
        },
    });
    function paint() {
        const rfb = getRfb();
        const active = isConnected() && mode.value === 'trackpad';
        const sink = rfb?._ardHp?._sink;
        cursor.hidden = !active || !!sink?.shown;
        model.clamp();
        if (sink?.id) Native.videoCursor(sink.id, model.x, model.y, active);
        if (!active || !rfb?._canvas) return;
        const canvas = rfb._canvas.getBoundingClientRect();
        const frame = screen.getBoundingClientRect();
        cursor.style.left = `${canvas.left - frame.left + model.x * rfb._display.scale}px`;
        cursor.style.top = `${canvas.top - frame.top + model.y * rfb._display.scale}px`;
    }
    function update() {
        model.cancel();
        drag.setAttribute('aria-pressed', 'false');
        const active = mode.value === 'trackpad';
        document.getElementById('trackpad-controls').hidden = !active;
        screen.classList.toggle('relative-input', active);
        pad.setAttribute('aria-disabled', String(!isConnected()));
        for (const id of ['left-click', 'right-click', 'drag'])
            document.getElementById(id).disabled = !isConnected();
        paint();
    }
    mode.onchange = update;
    pad.addEventListener('pointerdown', event => {
        if (!isConnected()) return;
        event.preventDefault();
        pad.setPointerCapture(event.pointerId);
        model.down(event.pointerId, event.clientX, event.clientY, event.timeStamp);
    });
    pad.addEventListener('pointermove', event => {
        event.preventDefault();
        model.move(event.pointerId, event.clientX, event.clientY);
    });
    pad.addEventListener('pointerup', event => {
        model.up(event.pointerId, event.timeStamp);
        pad.releasePointerCapture(event.pointerId);
    });
    pad.addEventListener('pointercancel', () => { model.cancel(); drag.setAttribute('aria-pressed', 'false'); });
    pad.addEventListener('lostpointercapture', event => {
        // Normal pointerup already removed the contact; unexpected loss cancels safely.
        if (model.points.has(event.pointerId)) model.cancel();
        drag.setAttribute('aria-pressed', String(model.dragging));
    });
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) { model.cancel(); drag.setAttribute('aria-pressed', 'false'); }
    });
    document.getElementById('left-click').onclick = () => model.click(1);
    document.getElementById('right-click').onclick = () => model.click(4);
    drag.onclick = () => {
        model.setDragging(!model.dragging);
        drag.setAttribute('aria-pressed', String(model.dragging));
    };
    screen.addEventListener('scroll', paint, true);
    window.addEventListener('desktopgeometry', paint);
    new ResizeObserver(paint).observe(screen);
    update();
    return { update, reset: () => { model.cancel(); model.x = null; model.y = null; update(); } };
}

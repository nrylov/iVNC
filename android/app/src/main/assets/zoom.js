/* Local magnification and panning shared by Standard canvas and native video geometry. */
import { clampZoom, fittedScale } from './zoom-model.js';

/** Restores desktop pinch zoom in trackpad mode without sending zoom shortcuts to the Mac. */
export function attachZoom(getRfb) {
    const screen = document.getElementById('screen');
    let zoom = 1, gesture = null;
    function apply(value, anchor) {
        zoom = clampZoom(value);
        document.getElementById('zoom-value').textContent = `${Math.round(zoom * 100)}%`;
        const rfb = getRfb();
        if (!rfb?._display.width || !rfb._canvas.isConnected) return;
        const scale = fittedScale(screen.clientWidth, screen.clientHeight, rfb._display.width, rfb._display.height, zoom);
        if (Math.abs(rfb._display.scale - scale) > 0.0001) rfb._display.scale = scale;
        if (anchor) {
            const box = rfb._canvas.getBoundingClientRect();
            rfb._screen.scrollLeft += box.left + anchor.u * box.width - anchor.x;
            rfb._screen.scrollTop += box.top + anchor.v * box.height - anchor.y;
        }
        window.dispatchEvent(new Event('desktopgeometry'));
    }
    document.getElementById('zoom-in').onclick = () => apply(zoom * 1.4);
    document.getElementById('zoom-out').onclick = () => apply(zoom / 1.4);
    document.getElementById('zoom-fit').onclick = () => {
        apply(1);
        const rfb = getRfb();
        if (rfb) rfb._screen.scrollLeft = rfb._screen.scrollTop = 0;
    };
    let pinchGesture = null;
    function pinch(event) {
        const rfb = getRfb();
        if (!rfb) return;
        if (event.phase === 'start') {
            const box = rfb._canvas.getBoundingClientRect();
            pinchGesture = { zoom, distance: event.distance,
                u: (event.x - box.left) / Math.max(1, box.width),
                v: (event.y - box.top) / Math.max(1, box.height) };
        } else if (pinchGesture) {
            apply(pinchGesture.zoom * event.distance / pinchGesture.distance,
                { ...pinchGesture, x: event.x, y: event.y });
        }
    }
    // Upstream can reset scale on a framebuffer resize; reapply local zoom after it does.
    let last = '';
    function monitor() {
        const rfb = getRfb();
        const key = rfb ? [screen.clientWidth, screen.clientHeight, rfb._display.width, rfb._display.height, rfb._display.scale].join() : '';
        if (key !== last) { apply(zoom); last = rfb ? [screen.clientWidth, screen.clientHeight, rfb._display.width, rfb._display.height, rfb._display.scale].join() : ''; }
        requestAnimationFrame(monitor);
    }
    monitor();
    return { reset: () => apply(1), pinch };
}

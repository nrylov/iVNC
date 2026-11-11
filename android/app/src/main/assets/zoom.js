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
    const relative = () => document.getElementById('pointer-mode').value === 'trackpad';
    function points(event) { return [...event.touches].map(touch => ({ x: touch.clientX, y: touch.clientY })); }
    function begin(event) {
        if (!relative()) return;
        event.preventDefault(); event.stopPropagation();
        const touches = points(event), rfb = getRfb();
        if (!rfb || !touches.length) { gesture = null; return; }
        if (touches.length >= 2) {
            const [a, b] = touches;
            const x = (a.x + b.x) / 2, y = (a.y + b.y) / 2;
            const box = rfb._canvas.getBoundingClientRect();
            gesture = { type: 'pinch', zoom, distance: Math.max(1, Math.hypot(a.x - b.x, a.y - b.y)),
                u: (x - box.left) / Math.max(1, box.width), v: (y - box.top) / Math.max(1, box.height) };
        } else gesture = { type: 'pan', ...touches[0] };
    }
    screen.addEventListener('touchstart', begin, { capture: true, passive: false });
    screen.addEventListener('touchmove', event => {
        if (!relative()) return;
        event.preventDefault(); event.stopPropagation();
        const touches = points(event), rfb = getRfb();
        if (!gesture || !rfb) return;
        if (gesture.type === 'pinch' && touches.length >= 2) {
            const [a, b] = touches;
            apply(gesture.zoom * Math.hypot(a.x - b.x, a.y - b.y) / gesture.distance,
                { u: gesture.u, v: gesture.v, x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });
        } else if (touches.length === 1 && gesture.type === 'pan' && !document.body.classList.contains('viewer-fullscreen')) {
            rfb._screen.scrollLeft -= touches[0].x - gesture.x;
            rfb._screen.scrollTop -= touches[0].y - gesture.y;
            gesture = { type: 'pan', ...touches[0] };
            window.dispatchEvent(new Event('desktopgeometry'));
        }
    }, { capture: true, passive: false });
    screen.addEventListener('touchend', begin, { capture: true, passive: false });
    screen.addEventListener('touchcancel', () => { gesture = null; }, true);
    // Upstream can reset scale on a framebuffer resize; reapply local zoom after it does.
    let last = '';
    function monitor() {
        const rfb = getRfb();
        const key = rfb ? [screen.clientWidth, screen.clientHeight, rfb._display.width, rfb._display.height, rfb._display.scale].join() : '';
        if (key !== last) { apply(zoom); last = rfb ? [screen.clientWidth, screen.clientHeight, rfb._display.width, rfb._display.height, rfb._display.scale].join() : ''; }
        requestAnimationFrame(monitor);
    }
    monitor();
    return { reset: () => apply(1) };
}

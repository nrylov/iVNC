/* Local desktop zoom math, independent of remote input or rendering backend. */
/** Restricts zoom to fit through eight times fit, keeping touch control predictable. */
export function clampZoom(value) { return Math.max(1, Math.min(8, value)); }
/** Maps framebuffer dimensions into the desktop viewport before applying magnification. */
export function fittedScale(viewWidth, viewHeight, frameWidth, frameHeight, zoom) {
    if (Math.min(viewWidth, viewHeight, frameWidth, frameHeight) <= 0) return 1;
    return Math.min(viewWidth / frameWidth, viewHeight / frameHeight) * clampZoom(zoom);
}

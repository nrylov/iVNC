/* Full-screen viewer state; a native floating menu stays above both rendering backends. */
/** Keeps controls one tap away while the desktop uses all available phone space. */
export function attachViewer(keyboard) {
    let immersive = false;
    function set(value) {
        immersive = value;
        if (value) { keyboard.close(); document.getElementById('settings').close(); }
        document.body.classList.toggle('viewer-fullscreen', value);
        Native.viewerMode(value);
        window.dispatchEvent(new Event('viewerchange'));
        window.dispatchEvent(new Event('desktopgeometry'));
    }
    window.toggleViewerUI = () => set(!immersive);
    document.getElementById('full-view').onclick = () => set(true);
    return { set };
}

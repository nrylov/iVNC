/* Android clipboard access over the existing Apple pasteboard protocol; no clipboard logging. */
import AsyncClipboard from './noVNC/core/clipboard.js';

/** Preserves both clipboard directions while sockets and foreground sessions are replaced. */
export function attachClipboard({ getRfb, getChannel, isConnected, status }) {
    AsyncClipboard.prototype.grab = function () {};
    AsyncClipboard.prototype.ungrab = function () {};
    AsyncClipboard.prototype.writeClipboard = function (text) {
        Native.setClipboard(text); status(`Received Mac clipboard (${text.length} characters)`); return true;
    };
    window.nativeEvent = (id, kind, value) => {
        const channel = getChannel();
        if (id !== channel?.id) return;
        if (kind === 'clipboard') {
            if (!isConnected()) return;
            if (value.length > 1024 * 1024) { status('Clipboard exceeds the 1 MiB limit'); return; }
            getRfb().forceClipboardPaste(value); status(`Sent phone clipboard (${value.length} characters)`); return;
        }
        channel.receive(kind, value);
        if (kind === 'close') status(value);
    };
    document.getElementById('send').onclick = () => Native.getClipboard(getChannel().id);
    document.getElementById('get').onclick = () => { getRfb().requestRemoteClipboard(); status('Requested Mac clipboard'); };
    document.getElementById('sync').onchange = () => getRfb().enableClipboardSync(document.getElementById('sync').checked);
}

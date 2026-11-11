/* Android connection driver for pinned noVNC-ARD; credentials remain in process memory only. */
import './ard/ard-patch.js';
import RFB from './noVNC/core/rfb.js';
import AsyncClipboard from './noVNC/core/clipboard.js';
import { NativeChannel } from './native-channel.js';
import { attachTrackpad } from './trackpad.js';
import { attachZoom } from './zoom.js';
import { configureVideo } from './high-performance.js';
import { attachDiagnostics } from './diagnostics.js';
import { attachKeyboard } from './keyboard.js';
import { ReconnectPolicy } from './reconnect-policy.js';
import { restoreProfile, saveProfile, forgetProfile } from './profiles.js';

const element = id => document.getElementById(id);
let rfb = null, channel = null, connected = false, configuration = null;
let lastHighPerformance = null;
let videoState = 'disconnected', failureReason = null, lastFrames = 0, lastTime = performance.now();
function status(message) {
    element('status').textContent = message;
    element('events').textContent = (element('events').textContent + '\n' + new Date().toLocaleTimeString() + ' ' + message).slice(-8000);
}
function setConnected(value) {
    connected = value;
    for (const id of ['send', 'get', 'sync', 'cmdc', 'cmdv']) element(id).disabled = !value;
    element('connect').disabled = value;
    trackpad.update(); keyboard.update();
}
const zoom = attachZoom(() => rfb);
const trackpad = attachTrackpad({ getRfb: () => rfb, isConnected: () => connected, onPinch: event => zoom.pinch(event) });
const keyboard = attachKeyboard(() => rfb, () => connected);
const reconnect = new ReconnectPolicy({ connect, disconnect: () => stopSession('Paused; reconnecting when you return') });
window.appLifecycle = state => {
    reconnect.enabled = element('auto-reconnect').checked;
    if (state === 'background') { keyboard.release(); reconnect.pause(); } else reconnect.resume();
};
document.addEventListener('visibilitychange', () => window.appLifecycle(document.hidden ? 'background' : 'foreground'));
function stopSession(message) {
    keyboard.release(); configuration = null;
    const previous = rfb; rfb = null;
    previous?.disconnect(); channel?.close(); setConnected(false);
    videoState = 'disconnected'; status(message);
}
AsyncClipboard.prototype.grab = function () {};
AsyncClipboard.prototype.ungrab = function () {};
AsyncClipboard.prototype.writeClipboard = function (text) {
    Native.setClipboard(text);
    status(`Received Mac clipboard (${text.length} characters)`);
    return true;
};
window.nativeEvent = (id, kind, value) => {
    if (id !== channel?.id) return;
    if (kind === 'clipboard') {
        if (!connected) return;
        if (value.length > 1024 * 1024) { status('Clipboard exceeds the 1 MiB limit'); return; }
        rfb.forceClipboardPaste(value);
        status(`Sent phone clipboard (${value.length} characters)`);
        return;
    }
    channel.receive(kind, value);
    if (kind === 'close') status(value);
};
function fallback(current, config, reason) {
    if (rfb !== current || configuration !== config || config.authFailed || config.mode === 'standard' || config.fallingBack) return;
    config.fallingBack = true;
    failureReason = reason;
    status(`High Performance unavailable (${reason}); reconnecting in Standard mode…`);
    setTimeout(() => {
        if (rfb === current && configuration === config)
            connect({ ...config, mode: 'standard', fallingBack: false });
    }, 0);
}
function connect(config) {
    if (!reconnect.foreground) return;
    const previous = rfb;
    rfb = null;
    setConnected(false);
    previous?.disconnect();
    channel?.close();
    configuration = config;
    reconnect.attempt(config);
    videoState = 'connecting';
    lastFrames = 0; lastTime = performance.now();
    channel = new NativeChannel(Native, config.host, config.port);
    const current = new RFB(element('screen'), channel, {
        credentials: { username: config.username, password: config.password }, shared: true,
    });
    rfb = current;
    current.scaleViewport = true;
    current.resizeSession = false;
    let mode = config.mode;
    if (mode !== 'standard' && !Native.softwareVideoAvailable()) {
        mode = 'standard'; failureReason = 'Native decoder library unavailable';
    }
    configureVideo(current, mode);
    current.addEventListener('credentialsrequired', () => {
        if (rfb === current) current.sendCredentials({ username: config.username, password: config.password });
    });
    current.addEventListener('ardsessionselect', () => current.selectSessionType(mode === 'standard' ? 1 : 2));
    current.addEventListener('connect', () => {
        if (rfb !== current) return;
        setConnected(true); trackpad.reset(); zoom.reset(); saveProfile(config);
        current.enableClipboardSync(element('sync').checked);
        document.activeElement?.blur(); element('settings').close();
        videoState = 'standard';
        if (mode !== 'standard' && !current._ardHpOffered()) { fallback(current, config, 'Mac did not offer it'); return; }
        status(mode === 'standard' ? 'Connected in Standard mode' : 'Connected; negotiating High Performance…');
    });
    current.addEventListener('ardhpstate', event => {
        if (rfb !== current) return;
        videoState = event.detail.state;
        if (videoState === 'streaming') status('High Performance · native software HEVC · virtual display');
        if (videoState === 'failed') {
            lastHighPerformance = { ...event.detail };
            fallback(current, config, event.detail.failure || event.detail.reason || 'media failure');
        }
    });
    current.addEventListener('disconnect', () => {
        if (rfb !== current || config.fallingBack) return;
        setConnected(false); reconnect.ended(); videoState = 'disconnected';
        if (config.mode !== 'standard' && configuration === config) { fallback(current, config, 'connection ended'); return; }
        configuration = null;
        status(config.authFailed ? 'Authentication failed; check the Mac username and password' : 'Disconnected');
    });
    current.addEventListener('securityfailure', () => { config.authFailed = true; reconnect.authenticationFailed(); status('Authentication failed; check the Mac username and password'); });
    status('Connecting…');
}
element('connection').onsubmit = event => {
    event.preventDefault();
    failureReason = null; lastHighPerformance = null;
    const config = { host: element('host').value.trim(), port: Number(element('port').value),
        username: element('username').value, password: element('password').value,
        mode: element('video-mode').value, requestedMode: element('video-mode').value };
    if (!element('remember').checked) forgetProfile();
    element('password').value = '';
    connect(config);
};
element('disconnect').onclick = () => { reconnect.stop(); status('Disconnected'); element('settings').showModal(); };
element('settings-open').onclick = () => element('settings').showModal();
element('settings-close').onclick = () => element('settings').close();
element('send').onclick = () => Native.getClipboard(channel.id);
element('get').onclick = () => { rfb.requestRemoteClipboard(); status('Requested Mac clipboard'); };
element('sync').onchange = () => rfb.enableClipboardSync(element('sync').checked);
function commandKey(key) {
    rfb.sendKey(0xffeb, 'MetaLeft', true);
    rfb.sendKey(key, key === 99 ? 'KeyC' : 'KeyV');
    rfb.sendKey(0xffeb, 'MetaLeft', false);
}
element('cmdc').onclick = () => commandKey(99);
element('cmdv').onclick = () => commandKey(118);
setInterval(() => {
    const count = rfb?._ardHp?._sink?.stats.presented || 0;
    const now = performance.now();
    element('video-state').textContent = videoState === 'streaming'
        ? `HP ${Math.max(0, Math.round((count - lastFrames) * 1000 / (now - lastTime)))} fps`
        : connected ? 'Standard' : 'Offline';
    lastFrames = count; lastTime = now;
}, 1000);
attachDiagnostics(() => ({ state: videoState, fallbackReason: failureReason, lastHighPerformance,
    presentedFrames: rfb?._ardHp?._sink?.stats.presented || 0,
    media: rfb?._ardHp?.stats || null }));
element('forget-profile').onclick = () => {
    forgetProfile(); element('password').value = '';
    element('auto-reconnect').checked = false; reconnect.enabled = false;
};
element('remember').onchange = () => { if (!element('remember').checked) forgetProfile(); };
element('auto-reconnect').onchange = () => {
    reconnect.enabled = element('auto-reconnect').checked;
    if (configuration) saveProfile(configuration);
};
restoreProfile(); trackpad.update();
element('settings').showModal();

// Native popup stays above the native video Surface; DOM controls remain shared.
window.viewerAction = action => {
    if (action === 'connection') { keyboard.close(); element('settings').showModal(); }
    else if (action === 'about') element('about').showModal();
    else element(action)?.click();
};
element('about-close').onclick = () => element('about').close();
element('settings').insertBefore(element('status'), element('connection'));

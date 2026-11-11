/* Android connection driver for pinned noVNC-ARD; credentials remain in process memory only. */
import './ard/ard-patch.js';
import RFB from './noVNC/core/rfb.js';
import { NativeChannel } from './native-channel.js';
import { attachTrackpad } from './trackpad.js';
import { attachZoom } from './zoom.js';
import { configureVideo } from './high-performance.js';
import { attachDiagnostics } from './diagnostics.js';
import { attachClipboard } from './clipboard-control.js';
import { ReconnectPolicy } from './reconnect-policy.js';
import { restoreProfile, saveProfile, forgetProfile } from './profiles.js';
import { attachKeyboard } from './keyboard.js';
import { attachViewer } from './viewer.js';

const element = id => document.getElementById(id);
let rfb = null, channel = null, connected = false, configuration = null;
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
    if (!value) viewer.set(false);
}
const trackpad = attachTrackpad({ getRfb: () => rfb, isConnected: () => connected });
const zoom = attachZoom(() => rfb);
const keyboard = attachKeyboard(() => rfb, () => connected);
const viewer = attachViewer(keyboard);
const reconnect = new ReconnectPolicy({ connect, disconnect: () => stopSession('Suspended; reconnecting when you return') });
attachClipboard({ getRfb: () => rfb, getChannel: () => channel, isConnected: () => connected, status });
window.appLifecycle = state => {
    reconnect.enabled = element('auto-reconnect').checked;
    if (state === 'background') { keyboard.release(); reconnect.pause(); }
    else reconnect.resume();
};
document.addEventListener('visibilitychange', () => window.appLifecycle(document.hidden ? 'background' : 'foreground'));
function stopSession(message) {
    keyboard.release(); configuration = null;
    const previous = rfb; rfb = null;
    previous?.disconnect(); channel?.close(); setConnected(false);
    videoState = 'disconnected'; status(message);
}
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
        setConnected(true); trackpad.reset(); zoom.reset();
        current.enableClipboardSync(element('sync').checked);
        saveProfile(config);
        document.activeElement?.blur(); viewer.set(true);
        videoState = 'standard';
        if (mode !== 'standard' && !current._ardHpOffered()) { fallback(current, config, 'Mac did not offer it'); return; }
        status(mode === 'standard' ? 'Connected in Standard mode' : 'Connected; negotiating High Performance…');
    });
    current.addEventListener('ardhpstate', event => {
        if (rfb !== current) return;
        videoState = event.detail.state;
        if (videoState === 'streaming') status('High Performance · native software HEVC · virtual display');
        if (videoState === 'failed') fallback(current, config, event.detail.failure || event.detail.reason || 'media failure');
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
    failureReason = null;
    const config = { host: element('host').value.trim(), port: Number(element('port').value),
        username: element('username').value, password: element('password').value,
        mode: element('video-mode').value, requestedMode: element('video-mode').value };
    element('password').value = '';
    connect(config);
};
element('disconnect').onclick = () => { reconnect.stop(); status('Disconnected'); };
element('settings-open').onclick = () => element('settings').showModal();
element('settings-close').onclick = () => element('settings').close();
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
attachDiagnostics(() => ({ state: videoState, fallbackReason: failureReason,
    presentedFrames: rfb?._ardHp?._sink?.stats.presented || 0,
    media: rfb?._ardHp?.stats || null }));
element('forget-profile').onclick = () => {
    forgetProfile(); element('password').value = ''; element('auto-reconnect').checked = false; reconnect.enabled = false;
};
element('auto-reconnect').onchange = () => { reconnect.enabled = element('auto-reconnect').checked; if (configuration) saveProfile(configuration); };
const saved = restoreProfile();
trackpad.update();
if (saved && saved.autoReconnect !== false) {
    element('password').value = '';
    if (document.hidden) { reconnect.attempt(saved); reconnect.active = false; reconnect.foreground = false; }
    else connect(saved);
} else element('settings').showModal();

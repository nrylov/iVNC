/* Saved-profile UI; the native host performs authenticated encryption and owns the key. */
const element = id => document.getElementById(id);

/** Loads and populates one saved profile without logging its address, username or password. */
export function restoreProfile() {
    const result = JSON.parse(Native.loadProfile());
    if (result.error) { element('profile-state').textContent = result.error; return null; }
    const profile = result.profile;
    if (!profile) return null;
    for (const key of ['host', 'port', 'username', 'password']) element(key).value = profile[key] ?? '';
    element('video-mode').value = ['standard','auto','highperf'].includes(profile.mode) ? profile.mode : 'standard';
    element('pointer-mode').value = profile.pointerMode === 'touch' ? 'touch' : 'trackpad';
    element('sensitivity').value = ['0.6','1','1.7'].includes(profile.sensitivity) ? profile.sensitivity : '1';
    element('auto-reconnect').checked = profile.autoReconnect !== false;
    element('remember').checked = true;
    element('profile-state').textContent = 'Saved connection loaded from encrypted device storage.';
    return { ...profile, mode: element('video-mode').value, remember: true };
}
/** Persists only after successful authentication; failed credentials never replace the saved profile. */
export function saveProfile(configuration) {
    if (!element('remember').checked) return;
    const profile = { host: configuration.host, port: configuration.port,
        username: configuration.username, password: configuration.password,
        mode: configuration.requestedMode || configuration.mode,
        pointerMode: element('pointer-mode').value, sensitivity: element('sensitivity').value,
        autoReconnect: element('auto-reconnect').checked };
    const saved = Native.saveProfile(JSON.stringify(profile));
    element('profile-state').textContent = saved ? 'Connection saved with Android Keystore encryption.' : 'Could not save this connection on the device.';
}
/** Removes the encrypted blob and its key; current session credentials remain only in memory. */
export function forgetProfile() {
    const removed = Native.forgetProfile();
    element('remember').checked = false;
    element('profile-state').textContent = removed ? 'Saved connection forgotten.' : 'Could not remove the saved connection.';
}

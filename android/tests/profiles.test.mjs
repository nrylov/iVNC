/* Saved-profile UI regression checks use synthetic credentials and a fake native store. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
const elements = new Map();
const element = id => {
    if (!elements.has(id)) elements.set(id, { value: '', checked: false, textContent: '' });
    return elements.get(id);
};
globalThis.document = { getElementById: element };
let loaded = { profile: null }, saved = [], forgotten = 0;
globalThis.Native = {
    loadProfile: () => JSON.stringify(loaded),
    saveProfile: text => { saved.push(JSON.parse(text)); return true; },
    forgetProfile: () => { forgotten++; return true; },
};
const { restoreProfile, saveProfile, forgetProfile } = await import('../app/src/main/assets/profiles.js');
test('restores encrypted-profile fields and preferences through the native store', () => {
    loaded = { profile: { host: 'example.invalid', port: 5900, username: 'test-user', password: 'synthetic',
        mode: 'highperf', pointerMode: 'touch', sensitivity: '1.7', autoReconnect: false } };
    restoreProfile();
    assert.equal(element('password').value, 'synthetic');
    assert.equal(element('video-mode').value, 'highperf');
    assert.equal(element('remember').checked, true);
    assert.equal(element('auto-reconnect').checked, false);
});
test('does not persist a profile when Remember is unchecked', () => {
    saved = []; element('remember').checked = false;
    saveProfile({host:'example.invalid', port:5900, username:'test-user', password:'synthetic'});
    assert.equal(saved.length, 0);
});
test('persists auth and requested mode through the native store and forgets on demand', () => {
    element('remember').checked = true;
    saveProfile({host:'example.invalid',port:5900,username:'test-user',password:'synthetic',mode:'standard',requestedMode:'highperf'});
    assert.equal(saved.at(-1).password, 'synthetic');
    assert.equal(saved.at(-1).mode, 'highperf');
    forgetProfile();
    assert.equal(forgotten, 1); assert.equal(element('remember').checked, false);
});
test('an unlock failure is shown without filling credential fields', () => {
    element('password').value = ''; loaded = {error:'Saved connection could not be unlocked'};
    assert.equal(restoreProfile(), null);
    assert.equal(element('password').value, '');
    assert.match(element('profile-state').textContent, /unlocked/);
});

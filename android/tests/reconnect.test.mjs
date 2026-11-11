/* Reconnection tests cover lifecycle duplication, explicit stop and failed authentication. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ReconnectPolicy } from '../app/src/main/assets/reconnect-policy.js';
function fixture() {
    const calls = [];
    return { calls, policy: new ReconnectPolicy({connect: config => calls.push(['connect', config.host]), disconnect: () => calls.push(['disconnect'])}) };
}
test('background/foreground callbacks are idempotent and resume the desired session once', () => {
    const { policy, calls } = fixture();
    policy.attempt({host:'test-server'});
    policy.pause(); policy.pause(); policy.resume(); policy.resume();
    assert.deepEqual(calls, [['disconnect'], ['connect','test-server']]);
});
test('explicit disconnect prevents lifecycle reconnection until a new attempt', () => {
    const { policy, calls } = fixture();
    policy.attempt({host:'test-server'}); policy.stop(); policy.pause(); policy.resume();
    assert.deepEqual(calls, [['disconnect']]);
    policy.attempt({host:'other-server'}); policy.pause(); policy.resume();
    assert.deepEqual(calls.at(-1), ['connect','other-server']);
});
test('disabled automatic reconnection still suspends background networking', () => {
    const { policy, calls } = fixture();
    policy.attempt({host:'test-server'}); policy.enabled = false;
    policy.pause(); policy.resume();
    assert.deepEqual(calls, [['disconnect']]);
});
test('failed authentication is never retried just because the app returns', () => {
    const { policy, calls } = fixture();
    policy.attempt({host:'test-server'}); policy.authenticationFailed(); policy.pause(); policy.resume();
    assert.deepEqual(calls, []);
});
test('lost transport retains intent for a later foreground reconnect', () => {
    const { policy, calls } = fixture();
    policy.attempt({host:'test-server'}); policy.ended(); policy.pause(); policy.resume();
    assert.deepEqual(calls, [['disconnect'], ['connect','test-server']]);
});

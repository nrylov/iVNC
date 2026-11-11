/* Tests real native-media adapter framing, batching, acknowledgments and stale-session cleanup. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
const nativeCalls = [];
globalThis.window = {};
globalThis.Native = Object.fromEntries(['mediaStart','mediaSend','mediaConsumed','mediaClose'].map(name => [name, (...args) => nativeCalls.push([name, ...args])]));
const { NativeMediaChannel } = await import('../app/src/main/assets/native-media.js');
test('media adapter opens asynchronously, delivers batches in order, and acknowledges once', async () => {
    nativeCalls.length = 0;
    const channel = new NativeMediaChannel();
    const received = [];
    channel.onmessage = event => received.push([...new Uint8Array(event.data)]);
    await new Promise(resolve => setTimeout(resolve, 5));
    assert.deepEqual(nativeCalls[0], ['mediaStart', channel.id]);
    window.mediaEvent(channel.id, 'open', '');
    window.mediaEvent(channel.id, 'data', JSON.stringify(['AAEC', 'Af8=']));
    assert.deepEqual(received, [[0,1,2], [1,255]]);
    assert.equal(nativeCalls.filter(call => call[0] === 'mediaConsumed').length, 1);
    channel.send(new Uint8Array([0,9,8,7]).subarray(1));
    assert.equal(nativeCalls.at(-1)[2], 'CQgH');
    channel.close();
    window.mediaEvent(channel.id, 'data', '["AA=="]');
    assert.equal(received.length, 2);
});
test('closing before async open prevents native socket creation', async () => {
    nativeCalls.length = 0;
    const channel = new NativeMediaChannel();
    channel.close();
    await new Promise(resolve => setTimeout(resolve, 5));
    assert.deepEqual(nativeCalls, [['mediaClose', channel.id]]);
});

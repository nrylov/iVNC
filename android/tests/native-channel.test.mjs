/* Tests the actual Android transport adapter's byte handling and session lifecycle. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NativeChannel } from '../app/src/main/assets/native-channel.js';

function fixture() {
    const calls = [];
    const bridge = Object.fromEntries(['connect', 'send', 'consumed', 'disconnect']
        .map(name => [name, (...args) => calls.push({ name, args })]));
    return { calls, channel: new NativeChannel(bridge, 'mac.local', 5900) };
}

test('connects asynchronously so RFB can attach its handlers first', async () => {
    const { calls, channel } = fixture();
    assert.equal(calls.length, 0);
    await new Promise(resolve => setTimeout(resolve, 5));
    assert.deepEqual(calls[0], { name: 'connect', args: [channel.id, 'mac.local', 5900] });
});
test('sends exactly the bytes in a typed-array view, without adjacent data', () => {
    const { channel, calls } = fixture();
    channel.receive('open', '');
    channel.send(new Uint8Array([9, 0, 255, 8]).subarray(1, 3));
    assert.deepEqual([...Buffer.from(calls[0].args[1], 'base64')], [0, 255]);
});
test('delivers incoming binary data and acknowledges after consumption', () => {
    const { channel, calls } = fixture();
    channel.receive('open', '');
    channel.onmessage = event => {
        assert.equal(calls.length, 0);
        assert.deepEqual([...new Uint8Array(event.data)], [0, 128, 255]);
    };
    channel.receive('data', 'AID/');
    assert.deepEqual(calls[0], { name: 'consumed', args: [channel.id] });
});
test('acknowledges even when the protocol parser rejects input', () => {
    const { channel, calls } = fixture();
    channel.onmessage = () => { throw new Error('parser rejected input'); };
    assert.throws(() => channel.receive('data', 'AA=='), /parser rejected/);
    assert.equal(calls[0].name, 'consumed');
});
test('ignores delayed events after close and disconnects once', async () => {
    const { channel, calls } = fixture();
    channel.close();
    channel.close();
    channel.receive('open', '');
    channel.receive('data', 'AA==');
    await new Promise(resolve => setTimeout(resolve, 5));
    assert.equal(channel.readyState, 'closed');
    assert.deepEqual(calls.map(call => call.name), ['disconnect']);
    assert.throws(() => channel.send(new Uint8Array()), /not open/);
});

/* Sink contract tests: keyframe gating, actual native presentation and teardown. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
const calls = [];
globalThis.window = { dispatchEvent: () => {} };
globalThis.innerWidth = 360; globalThis.innerHeight = 600;
globalThis.requestAnimationFrame = () => 1;
globalThis.cancelAnimationFrame = () => {};
globalThis.document = { getElementById: () => ({ getBoundingClientRect: () => ({left:0,top:50,right:360,bottom:550}) }), querySelector: () => null };
globalThis.Native = Object.fromEntries(['videoStart','videoGeometry','videoPacket','videoClose'].map(name => [name, (...args) => calls.push([name, ...args])]));
const { NativeVideoSink } = await import('../app/src/main/assets/native-video.js');
test('waits for key picture and only signals streaming after native presentation', () => {
    let first = 0, whole = 0;
    const sink = new NativeVideoSink({ width:1920,height:1080,stripes:2,
        target: { getBoundingClientRect: () => ({left:0,top:100,width:360,height:203}) },
        onFirstFrame: () => first++, onFirstPicture: () => whole++ });
    sink.push({key:false,data:new Uint8Array([1]),stripe:0,don:0});
    assert.equal(sink.stats.dropped, 1);
    sink.push({key:true,data:new Uint8Array([0,1]),stripe:0,don:1});
    assert.equal(sink.backlog, 1);
    window.videoEvent(sink.id, 'progress', '{"decoded":1,"presented":0}');
    assert.equal(first, 0);
    window.videoEvent(sink.id, 'progress', '{"decoded":2,"presented":1}');
    window.videoEvent(sink.id, 'progress', '{"decoded":4,"presented":2}');
    assert.equal(first, 1); assert.equal(whole, 1);
    sink.close(); sink.close();
    assert.equal(calls.filter(call => call[0] === 'videoClose').length, 1);
});

/* Full-screen input regression tests keep zoom gestures separate from remote mouse actions. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FullscreenPointer } from '../app/src/main/assets/fullscreen-pointer.js';
function fixture() {
    const calls = [];
    const model = Object.fromEntries(['down','move','up','cancel'].map(name=>[name,(...args)=>calls.push([name,...args])]));
    return {calls, pointer:new FullscreenPointer(model)};
}
test('one finger can move and tap through the trackpad model in full view', () => {
    const {calls,pointer}=fixture();
    pointer.down(1,10,20,0);pointer.move(1,20,30);pointer.up(1,100);
    assert.deepEqual(calls,[['down',1,10,20,0],['move',1,20,30],['up',1,100]]);
});
test('a two-finger zoom cancels remote pointing until every finger lifts', () => {
    const {calls,pointer}=fixture();
    pointer.down(1,0,0,0);pointer.down(2,50,0,5);
    pointer.move(1,20,0);pointer.up(2,50);pointer.move(1,30,0);pointer.up(1,70);
    assert.deepEqual(calls,[['down',1,0,0,0],['cancel']]);
    pointer.down(3,0,0,100);pointer.up(3,200);
    assert.equal(calls.at(-1)[0],'up');
});

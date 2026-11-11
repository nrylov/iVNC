/* Gesture tests verify the relative pointer independently of device input timing. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TrackpadModel } from '../app/src/main/assets/trackpad-model.js';
function fixture() {
    const events = [];
    const model = new TrackpadModel({
        bounds: () => ({ width: 1000, height: 600 }),
        sensitivity: () => 2,
        send: (...args) => events.push(['pointer', ...args]),
        scroll: (...args) => events.push(['scroll', ...args]),
    });
    return { model, events };
}
test('relative movement persists across finger lifts and clamps at edges', () => {
    const { model, events } = fixture();
    model.down(1, 10, 10, 0); model.move(1, 30, 40); model.up(1, 100);
    model.down(2, 0, 0, 200); model.move(2, 10, 0); model.up(2, 300);
    assert.deepEqual(events[1], ['pointer', 560, 360, 0]);
    model.down(3, 0, 0, 400); model.move(3, 2000, -2000);
    assert.deepEqual(events.at(-1), ['pointer', 999, 0, 0]);
});
test('one-finger tap clicks once at current cursor, not the touch position', () => {
    const { model, events } = fixture();
    model.down(1, 2, 3, 0); model.up(1, 100);
    assert.deepEqual(events, [['pointer', 500, 300, 1], ['pointer', 500, 300, 0]]);
});
test('two-finger tap sends the VNC right button for the ARD adapter to remap', () => {
    const { model, events } = fixture();
    model.down(1, 0, 0, 0); model.down(2, 50, 0, 5);
    model.up(1, 50); model.up(2, 60);
    assert.deepEqual(events.map(event => event[3]), [4, 0]);
});
test('two-finger scrolling leaves cursor still and never ends with an accidental click', () => {
    const { model, events } = fixture();
    model.down(1, 0, 0, 0); model.down(2, 50, 0, 5);
    model.move(1, 0, 30); model.move(2, 50, 30);
    model.up(1, 60); model.move(2, 60, 40); model.up(2, 70);
    assert.deepEqual(events, [['scroll', 500, 300, -0, -15], ['scroll', 500, 300, -0, -15]]);
});
test('drag holds left button while moving and cancellation releases it', () => {
    const { model, events } = fixture();
    model.setDragging(true); model.down(1, 0, 0, 0); model.move(1, 20, 10);
    model.up(1, 400); model.cancel();
    assert.deepEqual(events.map(event => event[3]), [1, 1, 0]);
    assert.equal(model.dragging, false);
});
test('cancelled touch cannot produce a later tap', () => {
    const { model, events } = fixture();
    model.down(1, 0, 0, 0); model.cancel(); model.up(1, 10);
    assert.equal(events.length, 0);
});
test('long touch without motion does not click', () => {
    const { model, events } = fixture();
    model.down(1, 0, 0, 0); model.up(1, 900);
    assert.equal(events.length, 0);
});

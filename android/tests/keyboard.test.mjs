/* Keyboard contract tests verify Unicode and balanced remote modifier state. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RemoteKeys, textKeys } from '../app/src/main/assets/key-model.js';
test('encodes Unicode code points and named newline without splitting surrogate pairs', () => {
    assert.deepEqual(textKeys('c🔬\n'), [[99,'KeyC'],[0x0101f52c,'Unidentified'],[0xff0d,'Enter']]);
});
test('sticky Ctrl surrounds a shortcut and releases once when the dock closes', () => {
    const sent = [], keys = new RemoteKeys((...args) => sent.push(args));
    keys.toggle(0xffe3,'ControlLeft'); keys.text('c'); keys.release(); keys.release();
    assert.deepEqual(sent, [[0xffe3,'ControlLeft',true],[99,'KeyC'],[0xffe3,'ControlLeft',false]]);
});
test('toggling a modifier off prevents duplicate release on disconnect', () => {
    const sent = [], keys = new RemoteKeys((...args) => sent.push(args));
    keys.toggle(0xffe9,'AltLeft'); keys.toggle(0xffe9,'AltLeft'); keys.release();
    assert.equal(sent.length, 2);
    assert.equal(sent[1][2], false);
});

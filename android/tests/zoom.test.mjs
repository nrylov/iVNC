/* Local zoom tests protect fit and magnification bounds across framebuffer sizes. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { clampZoom, fittedScale } from '../app/src/main/assets/zoom-model.js';
test('fit preserves the full desktop aspect ratio in portrait and landscape', () => {
    assert.equal(fittedScale(360, 600, 1920, 1080, 1), 360 / 1920);
    assert.equal(fittedScale(800, 300, 1920, 1080, 1), 300 / 1080);
});
test('local zoom scales the fitted desktop and clamps extremes', () => {
    assert.equal(fittedScale(360, 600, 1920, 1080, 4), .75);
    assert.equal(clampZoom(.1), 1);
    assert.equal(clampZoom(20), 8);
    assert.equal(fittedScale(0, 0, 1920, 1080, 2), 1);
});

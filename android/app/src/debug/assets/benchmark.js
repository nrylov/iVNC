/* Debug replay through the production NativeVideoSink and Java/base64 video bridge. */
import { NativeVideoSink } from './native-video.js';
import { decodeBytes } from './binary.js';
let packets, sink, stopped = true;

window.benchmarkLoad = encoded => {
    packets = encoded.map(decodeBytes); // Preload outside the measured interval.
    Bench.loaded();
};
window.benchmarkStop = () => { stopped = true; sink?.close(); sink = null; };
window.benchmarkRun = config => {
    stopped = false;
    sink = new NativeVideoSink({ width: 1920, height: 1080, stripes: 1,
        target: document.getElementById('desktop'), onError: error => Bench.failed(String(error)) });
    const total = config.warmupFrames + config.measuredFrames;
    const periodNs = Math.floor(1e9 / config.fps);
    // Both native and Java timestamps use CLOCK_MONOTONIC. Calibrate JS once;
    // the bridge clock call is outside timing and never repeated per picture.
    const clockOffsetNs = Number(Bench.nowNs()) - performance.now() * 1e6;
    let frame = 0;
    const tick = () => {
        if (stopped) return;
        const nowNs = clockOffsetNs + performance.now() * 1e6;
        const deadline = config.startNs + frame * periodNs;
        if (nowNs < deadline) { setTimeout(tick, Math.max(0, (deadline - nowNs) / 1e6)); return; }
        // Bound the instrumented producer below the production queue limit.
        // Preserve predictive pictures: an overloaded route runs late, not corrupt.
        if (sink.backlog >= 16) { setTimeout(tick, 1); return; }
        sink.push({ key: true, data: packets[frame % packets.length], don: frame, stripe: 0 });
        frame++;
        if (frame % 4 === 0 || frame === config.warmupFrames || frame === total)
            Bench.checkpoint(sink.id, String(frame));
        if (frame < total) setTimeout(tick, 0);
    };
    tick();
};
Bench.ready();

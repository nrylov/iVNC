#!/usr/bin/env python3
"""Install and run the controlled synthetic native/web replay test on a paired Android device."""
import argparse
import json
import pathlib
import statistics
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def percentile(values, fraction):
    """Use a nearest-rank percentile for nonnegative frame timing observations."""
    import math
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)] if ordered else 0


def summarize(report):
    """Compare repeated trials without confusing Surface posts and texture updates."""
    print('\nHEVC sink replay only — excludes network, SRTP, depacketization and LibVNCClient.')
    print('route          source  texture FPS  post lateness p95  thermal before→after')
    for rate in sorted({trial['targetFps'] for trial in report['trials']}):
        for route in ('native-direct', 'web-bridge'):
            trials = [trial for trial in report['trials'] if trial['targetFps'] == rate and trial['route'] == route]
            fps = statistics.median(trial['surface']['textureUpdateFps'] for trial in trials)
            lateness = statistics.median(percentile(trial['postLatenessMs'], .95) for trial in trials)
            thermal = ','.join(f"{trial['thermalBefore']}→{trial['thermalAfter']}" for trial in trials)
            print(f'{route:14} {rate:3}fps  {fps:10.1f}  {lateness:14.2f}ms  {thermal}')
    print('Texture callbacks measure buffer latches, not physical screen latency.')
    print('CPU figures in JSON exclude the separate WebView renderer process.')


def main():
    """Fail before reading stale results if instrumentation does not complete successfully."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial')
    parser.add_argument('--seconds', type=int, default=15)
    parser.add_argument('--warmup', type=int, default=2)
    parser.add_argument('--repeats', type=int, default=3)
    parser.add_argument('--rates', default='60,120')
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--out', type=pathlib.Path, default=ROOT / '.dev/benchmark/latest.json')
    args = parser.parse_args()
    if not (2 <= args.seconds <= 60 and 1 <= args.warmup <= 10 and 1 <= args.repeats <= 5):
        parser.error('seconds: 2–60, warmup: 1–10, repeats: 1–5')
    try:
        rates = [int(value) for value in args.rates.split(',')]
        if not 1 <= len(rates) <= 4 or any(rate < 1 or rate > 120 for rate in rates):
            raise ValueError()
    except ValueError:
        parser.error('rates: one to four comma-separated values from 1 to 120')
    adb = [str(ROOT / 'scripts/device.sh')]
    if args.serial:
        adb += ['-s', args.serial]
    else:
        devices = subprocess.check_output(adb + ['devices'], text=True)
        available = [line.split()[0] for line in devices.splitlines()[1:] if line.split()[1:] == ['device']]
        if len(available) != 1:
            parser.error('Exactly one connected device required; otherwise specify --serial')
        adb += ['-s', available[0]]
    if not args.skip_build:
        subprocess.run([str(ROOT / 'scripts/build-benchmark.sh')], cwd=ROOT, check=True)
    subprocess.run(adb + ['install', '-r', str(ROOT / 'android/app/build/outputs/apk/debug/app-debug.apk')], check=True)
    command = adb + ['shell', 'am', 'instrument', '-w', '-r']
    for key, value in [('seconds', args.seconds), ('warmup', args.warmup), ('repeats', args.repeats), ('rates', args.rates)]:
        command += ['-e', key, str(value)]
    command += ['dev.applescreen.prototype/.VideoBenchmarkInstrumentation']
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    output = []
    for line in process.stdout:
        print(line, end='', flush=True)
        output.append(line)
    if process.wait() != 0 or 'PASS: routes rendered identical final pixels' not in ''.join(output):
        sys.exit('Benchmark failed; previous result was not read.')
    raw = subprocess.check_output(adb + ['exec-out', 'run-as', 'dev.applescreen.prototype', 'cat', 'files/benchmark/latest.json'])
    report = json.loads(raw)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(raw)
    summarize(report)
    print(f'Report: {args.out}')


if __name__ == '__main__':
    main()

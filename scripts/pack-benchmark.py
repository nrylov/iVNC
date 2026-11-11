#!/usr/bin/env python3
"""Pack a synthetic Annex-B HEVC stream with AUDs into the bounded replay fixture."""
import argparse
import hashlib
import json
import pathlib
import struct


def pack(data):
    """Preserve each compressed access unit exactly, including headers before the first AUD."""
    boundaries = []
    offset = 0
    while offset + 5 < len(data):
        prefix = 4 if data[offset:offset + 4] == b'\0\0\0\1' else 3 if data[offset:offset + 3] == b'\0\0\1' else 0
        if not prefix:
            offset += 1
            continue
        if (data[offset + prefix] >> 1) & 63 == 35:
            boundaries.append(offset)
        offset += prefix + 2
    if not 1 <= len(boundaries) <= 600:
        raise ValueError('Expected 1–600 AUD-delimited pictures')
    units = [data[0 if index == 0 else start:boundaries[index + 1] if index + 1 < len(boundaries) else len(data)]
             for index, start in enumerate(boundaries)]
    if any(not 1 <= len(unit) <= 4194304 for unit in units) or len(data) > 33554432:
        raise ValueError('Fixture exceeds replay bounds')
    return b'IVNC' + struct.pack('>I', len(units)) + b''.join(struct.pack('>I', len(unit)) + unit for unit in units)


def main():
    """Write only an explicitly selected generated input to the project's debug assets."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=pathlib.Path)
    parser.add_argument('output', type=pathlib.Path)
    args = parser.parse_args()
    output = pack(args.input.read_bytes())
    args.output.write_bytes(output)
    print(json.dumps({'bytes': len(output), 'sha256': hashlib.sha256(output).hexdigest()}))


if __name__ == '__main__':
    main()

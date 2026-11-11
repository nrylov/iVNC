#!/usr/bin/env python3
"""Check fixture packing preserves exact compressed bytes and rejects unusable input."""
import importlib.util
import pathlib
import struct
import unittest

ROOT = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('pack_benchmark', ROOT / 'pack-benchmark.py')
packer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packer)


class PackerTest(unittest.TestCase):
    """Catch prefix/header loss which would make the native and web workloads invalid."""
    def test_preserves_headers_and_both_prefix_lengths(self):
        header = b'\x00\x00\x00\x01\x40\x01header'
        first = b'\x00\x00\x01\x46\x01aud1\x00\x00\x01\x26\x01picture1'
        second = b'\x00\x00\x00\x01\x46\x01aud2\x00\x00\x01\x02\x01picture2'
        result = packer.pack(header + first + second)
        self.assertEqual(result[:8], b'IVNC' + struct.pack('>I', 2))
        first_size = struct.unpack_from('>I', result, 8)[0]
        self.assertEqual(result[12:12 + first_size], header + first)
        offset = 12 + first_size
        self.assertEqual(result[offset + 4:], second)

    def test_rejects_no_aud(self):
        with self.assertRaises(ValueError):
            packer.pack(b'not an HEVC stream')

    def test_rejects_excessive_picture_count(self):
        with self.assertRaises(ValueError):
            packer.pack(b'\x00\x00\x01\x46\x01\xff' * 601)


if __name__ == '__main__':
    unittest.main()

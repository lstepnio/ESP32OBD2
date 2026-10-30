"""Public gallery preserves native pixels while masking nonexistent panel corners."""

import struct
import sys
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from render_gauge_gallery import png


class GaugeGalleryTest(unittest.TestCase):
    def test_native_visible_pixels_and_circular_transparency(self):
        raw = bytes((23, 145, 233)) * (240 * 240)
        data = png(raw)
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
        chunks = {}
        offset = 8
        while offset < len(data):
            length = struct.unpack(">I", data[offset : offset + 4])[0]
            chunks[data[offset + 4 : offset + 8]] = data[offset + 8 : offset + 8 + length]
            offset += length + 12
        self.assertEqual(struct.unpack(">IIBBBBB", chunks[b"IHDR"]), (240, 240, 8, 6, 0, 0, 0))
        rows = zlib.decompress(chunks[b"IDAT"])
        stride = 1 + 240 * 4
        for x, y, alpha in (
            (0, 0, 0),
            (239, 239, 0),
            (120, 120, 255),
            (120, 0, 255),
            (0, 120, 255),
        ):
            at = y * stride + 1 + x * 4
            self.assertEqual(rows[at : at + 4], bytes((23, 145, 233, alpha)))

    def test_incorrect_native_frame_size_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "240x240"):
            png(b"short")

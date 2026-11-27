#!/usr/bin/env python3
"""
Pack the Mushaf line fingerprints into one file the app can import (Settings → import).

    python3 tools/mushaf/build_mushaf_pack.py     # → book-source/mushaf-fp/mushaf-warsh.bayyinah-pack

The pack is derived from the publisher's scans: it is built locally, stays git-ignored, and is
handed to the user directly — never committed or attached to a release.

Format (little-endian), deflate-compressed as a whole after the 8-byte magic+version:
  "BYMP" u16 version=1 u16 flags=0 | zlib(
    u8 rows u8 bits u16 n_tmpl  f32[n_tmpl] row template
    u16 first_pdf_page i16 printed_page_offset
    u32 n_lines, then per line:
      u16 pdf_page u8 line_no u8 n_spans  n_spans × (u8 surah u16 ayah u16 col0 u16 col1)
      u16 width   ceil(rows*width*bits/8) bytes: values row-major, packed MSB-first )
"""
import pickle, struct, sys, zlib
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
import mushaf_matcher as mm
import mushaf_pitch as mp

ROWS, BITS = 10, 2
OUT = Path("book-source/mushaf-fp/mushaf-warsh.bayyinah-pack")


def pack_bits(vals, bits):
    v = vals.astype(np.uint8).ravel()
    per = 8 // bits
    v = np.concatenate([v, np.zeros((-len(v)) % per, np.uint8)]).reshape(-1, per)
    out = np.zeros(len(v), np.uint8)
    for j in range(per): out |= v[:, j] << (8 - bits * (j + 1))
    return out.tobytes()


def main():
    lines = pickle.load(open("book-source/mushaf-fp/lines.pkl", "rb"))
    tmpl = mp.template([l['strip'] for l in lines[::7]]).astype(np.float32)
    lv = (1 << BITS) - 1
    body = bytearray()
    body += struct.pack("<BBH", ROWS, BITS, len(tmpl)) + tmpl.tobytes()
    body += struct.pack("<Hh", 9, -5)
    body += struct.pack("<I", len(lines))
    for l in lines:
        q = mm.quantize(l['strip'], ROWS, BITS, 1.0)
        body += struct.pack("<HBB", l['page'], l['line'], len(l['spans']))
        for su, a, u, v in l['spans']: body += struct.pack("<BHHH", su, a, u, v)
        body += struct.pack("<H", q.shape[1]) + pack_bits(np.round(q * lv), BITS)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(b"BYMP" + struct.pack("<HH", 1, 0) + zlib.compress(bytes(body), 9))
    print(f"{len(lines)} lines, raw {len(body) / 1e6:.2f} MB, file {OUT.stat().st_size / 1e6:.2f} MB → {OUT}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Build app/src/main/assets/book_refs.bin from the official PDF of «أقرأ وأتعلم – المستوى الثاني».

    python3 tools/book/build_book_refs.py book-source

Needs: poppler (pdftoppm), numpy, scipy, Pillow. The PDFs stay local (book-source/, git-ignored);
only the low-resolution line fingerprints (20 px tall) go into the app.

Steps: render the Quran pages at 300 dpi → find each cream «أسمع وأرتل» verse box → find the gold
ayah medallions (they end each ayah) → split the box into lines (bookfp) → for each line store
its fingerprint strip and which ayah covers which part of the line.
"""
import json, struct, subprocess, sys, tempfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageOps
from scipy import ndimage
sys.path.insert(0, str(Path(__file__).parent))
import bookfp as fp

# (book page, surah, first ayah, last ayah) — Warsh numbering, from the book's index (p. 224).
LESSONS = [
 (92, 2, 253, 254), (95, 2, 284, 285), (98, 3, 133, 134), (101, 3, 135, 136), (104, 3, 189, 191), (108, 3, 192, 194),
 (111, 7, 41, 42), (114, 8, 2, 4), (117, 9, 129, 130), (120, 16, 68, 69), (124, 17, 23, 25), (127, 17, 34, 36),
 (130, 17, 109, 110), (133, 18, 1, 4), (136, 18, 5, 8), (140, 18, 9, 10), (143, 24, 35, 35), (147, 36, 1, 8),
 (151, 45, 11, 12), (154, 46, 14, 14), (158, 49, 11, 12), (161, 59, 21, 24), (164, 62, 9, 10), (167, 67, 1, 5),
]
BOX_BG = np.array([253, 227, 196])
PARTS = [("91-135_compressed.pdf", 91), ("136-169.pdf", 136)]   # (file, book page of its first PDF page)


def render(src: Path, tmp: Path):
    pages = {}
    for name, first in PARTS:
        pre = tmp / name[:3]
        subprocess.run(["pdftoppm", "-r", "300", "-png", str(src / name), str(pre)], check=True)
        for f in sorted(tmp.glob(pre.name + "-*.png")):
            pages[first + int(f.stem.split("-")[-1]) - 1] = f
    return pages


def find_box(rgb):
    m = ndimage.binary_closing(np.abs(rgb - BOX_BG).max(2) <= 10, iterations=8)
    lab, n = ndimage.label(m); sizes = ndimage.sum(m, lab, range(1, n + 1)); k = int(np.argmax(sizes)) + 1
    ys, xs = np.where(lab == k)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


def medallions(b):
    R, G, B = b[..., 0], b[..., 1], b[..., 2]
    gold = (R > 150) & (G > 110) & (B < 110) & (R - B > 90) & ~(np.abs(b - BOX_BG).max(2) <= 25)
    gl, _ = ndimage.label(ndimage.binary_opening(gold, iterations=2))
    H, W = b.shape[:2]; out = []
    for i, sl in enumerate(ndimage.find_objects(gl), 1):
        h = sl[0].stop - sl[0].start; w = sl[1].stop - sl[1].start
        if 40 <= h <= 140 and 40 <= w <= 140 and 0.6 < h / w < 1.6 and (gl[sl] == i).sum() > 0.35 * h * w:
            cx, cy = (sl[1].start + sl[1].stop) // 2, (sl[0].start + sl[0].stop) // 2
            if 0.06 * W < cx < 0.94 * W and 0.06 * H < cy < 0.94 * H and not any(abs(cx - x) < 60 and abs(cy - y) < 60 for x, y in out):
                out.append((cx, cy))
    return out


def main(src: Path, out: Path):
    lines_out = []
    with tempfile.TemporaryDirectory() as td:
        pages = render(src, Path(td))
        for page, surah, a0, a1 in LESSONS:
            rgb = np.asarray(Image.open(pages[page]).convert("RGB")).astype(int)
            x0, y0, x1, y1 = find_box(rgb)
            b = rgb[y0:y1, x0:x1]; H, W = b.shape[:2]
            meds = medallions(b)
            assert len(meds) == a1 - a0 + 1, f"page {page}: {len(meds)} medallions for {a1 - a0 + 1} ayat"
            ix0, ix1, iy0, iy1 = int(0.075 * W), int(0.925 * W), int(0.05 * H), int(0.95 * H)
            lum = (0.299 * b[..., 0] + 0.587 * b[..., 1] + 0.114 * b[..., 2]).astype(int)
            chroma = b.max(2) - b.min(2)
            ink = fp.remove_rules((fp.binarize(lum) & (chroma < 70))[iy0:iy1, ix0:ix1])
            P = fp.pitch_of(ink, 60, 260)
            cur = a0
            for base in fp.lines(ink, P):
                ms = sorted([m for m in meds if abs((m[1] - iy0) - base) < 0.5 * P], key=lambda m: -m[0])
                spans, xr = [], ink.shape[1]
                for mx, _ in ms:
                    spans.append((cur, mx - ix0, xr)); cur += 1; xr = mx - ix0
                spans.append((min(cur, a1), 0, xr))
                st, cx0, s = fp.strip(ink, base, P)
                if st is None: continue
                sp = [(a, max(0, int(round((u - cx0) * s))), max(0, int(round((v - cx0) * s)))) for a, u, v in spans if v > u]
                lines_out.append((page, surah, sp, st))
            print(f"page {page}: surah {surah} {a0}-{a1}, pitch {P}, {sum(1 for l in lines_out if l[0] == page)} lines")
    with open(out, "wb") as f:
        f.write(b"BKF1"); f.write(struct.pack("<H", len(LESSONS)))
        for page, surah, a0, a1 in LESSONS: f.write(struct.pack("<HBHH", page, surah, a0, a1))
        f.write(struct.pack("<H", len(lines_out)))
        for page, surah, sp, st in lines_out:
            f.write(struct.pack("<HBB", page, surah, len(sp)))
            for a, u, v in sp: f.write(struct.pack("<HHH", a, u, v))
            h, w = st.shape; f.write(struct.pack("<BH", h, w))
            f.write(np.clip(np.round(st * 255), 0, 255).astype(np.uint8).tobytes())
    print(f"wrote {out} ({out.stat().st_size // 1024} KB, {len(lines_out)} lines)")


if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else "book-source"), Path("app/src/main/assets/book_refs.bin"))

#!/usr/bin/env python3
"""Synthetic camera crops of the book (positives = Quran lines, negatives = lesson text) for testing.
Output: book-source/testcrops/*.jpg + labels.json (kept local: they are images of the book)."""
import json, random, struct, subprocess, sys, tempfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageChops, ImageFilter
sys.path.insert(0, str(Path(__file__).parent))
import bookfp as fp
from build_book_refs import LESSONS, PARTS, render, find_box, medallions

def camera(img, rng):
    w, h = img.size; j = 0.04
    src = [(0, 0), (w, 0), (w, h), (0, h)]
    dst = [(x + rng.uniform(-j, j) * w, y + rng.uniform(-j, j) * h) for x, y in src]
    A, B = [], []
    for (x, y), (u, v) in zip(dst, src):
        A += [[x, y, 1, 0, 0, 0, -u * x, -u * y], [0, 0, 0, x, y, 1, -v * x, -v * y]]; B += [u, v]
    c = np.linalg.solve(np.array(A), np.array(B))
    im = img.transform((w, h), Image.PERSPECTIVE, tuple(c), Image.BICUBIC, fillcolor=(240, 225, 200))
    im = im.rotate(rng.uniform(-3, 3), Image.BICUBIC, fillcolor=(240, 225, 200))
    g = Image.linear_gradient('L').rotate(rng.choice([0, 90, 180, 270])).resize(im.size).point(lambda v: int(150 + v * 105 / 255)).convert('RGB')
    return ImageChops.multiply(im, g).filter(ImageFilter.GaussianBlur(rng.uniform(0.6, 1.6)))

def main(src, out, npos=60, nneg=40):
    rng = random.Random(11); labels = []
    with tempfile.TemporaryDirectory() as td:
        pages = render(src, Path(td))
        info = {}
        for page, surah, a0, a1 in LESSONS:
            im = Image.open(pages[page]).convert('RGB'); rgb = np.asarray(im).astype(int)
            x0, y0, x1, y1 = find_box(rgb); b = rgb[y0:y1, x0:x1]; H, W = b.shape[:2]
            ix0, iy0 = int(0.075 * W), int(0.05 * H)
            lum = (0.299 * b[..., 0] + 0.587 * b[..., 1] + 0.114 * b[..., 2]).astype(int)
            ink = fp.remove_rules((fp.binarize(lum) & ((b.max(2) - b.min(2)) < 70))[iy0:int(0.95 * H), ix0:int(0.925 * W)])
            P = fp.pitch_of(ink, 60, 260); bases = fp.lines(ink, P)
            info[page] = (im, (x0, y0, x1, y1), [y0 + iy0 + q for q in bases], P, surah, a0, a1)
        for k in range(npos):
            page = rng.choice(list(info)); im, (x0, y0, x1, y1), bases, P, surah, a0, a1 = info[page]
            li = rng.randrange(len(bases)); cy = bases[li] - 0.1 * P + rng.uniform(-0.3, 0.3) * P
            fw = (x1 - x0) * rng.uniform(0.6, 1.0); cx = (x0 + x1) / 2 + rng.uniform(-0.2, 0.2) * (x1 - x0 - fw)
            fh = fw / 2.0 if rng.random() < 0.5 else P * rng.uniform(1.3, 2.4)
            crop = im.crop((int(cx - fw / 2), int(cy - fh / 2), int(cx + fw / 2), int(cy + fh / 2)))
            sc = rng.uniform(0.35, 0.75); crop = crop.resize((int(crop.width * sc), int(crop.height * sc)), Image.BILINEAR)
            name = f"pos{k:03d}.jpg"; camera(crop, rng).save(out / name, quality=70)
            labels.append(dict(file=name, page=page, surah=surah, ayat=list(range(a0, a1 + 1))))
        box_pages = set(info)
        others = [p for p in pages if p not in box_pages and 91 <= p <= 169]
        for k in range(nneg):
            p = rng.choice(others); im = Image.open(pages[p]).convert('RGB'); W, H = im.size
            fw = W * rng.uniform(0.4, 0.7); fh = fw / 2
            x = rng.uniform(0.1 * W, 0.9 * W - fw); y = rng.uniform(0.15 * H, 0.85 * H - fh)
            crop = im.crop((int(x), int(y), int(x + fw), int(y + fh)))
            sc = rng.uniform(0.35, 0.75); crop = crop.resize((int(crop.width * sc), int(crop.height * sc)))
            name = f"neg{k:03d}.jpg"; camera(crop, rng).save(out / name, quality=70)
            labels.append(dict(file=name, page=0))
    json.dump(labels, open(out / "labels.json", "w"))
    print(f"wrote {npos} positives + {nneg} negatives to {out}")

if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else "book-source"), Path("book-source/testcrops"))

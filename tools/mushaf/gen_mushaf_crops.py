#!/usr/bin/env python3
"""Synthetic camera crops of the scanned Mushaf for testing the matcher (kept local, git-ignored).
Positives: random Quran pages (PDF 9-644). Negatives: the Mushaf's own non-Quran text pages
(front matter, the closing notes on the script and recitation — same Maghribi hand), plus blank-ish frame areas.

    python3 tools/mushaf/gen_mushaf_crops.py [npos] [nneg]   # → book-source/mushaf-crops/
"""
import json, pickle, random, sys
from pathlib import Path
from PIL import Image
sys.path.insert(0, str(Path(__file__).parent))
sys.path.insert(0, str(Path(__file__).parent.parent / "book"))
from gen_testcrops import camera
import mushaf_pages as m

OUT = Path("book-source/mushaf-crops")
NEG_PAGES = [2, 4, 6] + list(range(646, 659))


def main(npos=300, nneg=100):
    OUT.mkdir(parents=True, exist_ok=True)
    rng = random.Random(7)
    lines = pickle.load(open("book-source/mushaf-fp/lines.pkl", "rb"))
    by_page = {}
    for l in lines: by_page.setdefault(l['page'], []).append(l)
    labels = []
    for k in range(npos):
        p = rng.choice(list(by_page)); L = by_page[p]; im = Image.open(m.page_path(p)).convert('RGB')
        l = rng.choice(L); P = l['pitch']
        x0 = min(t['x0'] for t in L); x1 = max(t['x1'] for t in L)
        cy = l['base'] - 0.1 * P + rng.uniform(-0.3, 0.3) * P
        fw = (x1 - x0) * rng.uniform(0.45, 1.0); cx = (x0 + x1) / 2 + rng.uniform(-0.5, 0.5) * (x1 - x0 - fw)
        fh = P * rng.uniform(1.3, 2.4) if rng.random() < 0.5 else fw * rng.uniform(0.35, 0.7)
        box = (int(cx - fw / 2), int(cy - fh / 2), int(cx + fw / 2), int(cy + fh / 2))
        crop = im.crop(box)
        sc = rng.uniform(0.5, 1.0); crop = crop.resize((int(crop.width * sc), int(crop.height * sc)), Image.BILINEAR)
        name = f"pos{k:03d}.jpg"; camera(crop, rng).save(OUT / name, quality=70)
        inside = [t for t in L if box[1] + 0.3 * P < t['base'] < box[3] + 0.2 * P]
        ayat = sorted({(su, a) for t in inside for su, a, u, v in t['spans']})
        labels.append(dict(file=name, page=p, lines=[t['line'] for t in inside], ayat=ayat, pitch=P * sc))
    for k in range(nneg):
        p = rng.choice(NEG_PAGES); im = Image.open(m.page_path(p)).convert('RGB'); W, H = im.size
        fw = W * rng.uniform(0.35, 0.65); fh = fw * rng.uniform(0.25, 0.6)
        x = rng.uniform(0.12 * W, 0.88 * W - fw); y = rng.uniform(0.15 * H, 0.85 * H - fh)
        crop = im.crop((int(x), int(y), int(x + fw), int(y + fh)))
        sc = rng.uniform(0.5, 1.0); crop = crop.resize((int(crop.width * sc), int(crop.height * sc)))
        name = f"neg{k:03d}.jpg"; camera(crop, rng).save(OUT / name, quality=70)
        labels.append(dict(file=name, page=0))
    json.dump(labels, open(OUT / "labels.json", "w"))
    print(f"wrote {npos} positives + {nneg} negatives to {OUT}")


if __name__ == "__main__":
    main(*(int(a) for a in sys.argv[1:3]))

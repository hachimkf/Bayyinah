#!/usr/bin/env python3
"""
Page analysis for the scanned Mushaf Mohammadi (Warsh), 200 dpi JPEGs extracted with
`pdfimages -j mushaf-mohammadi-warsh.pdf book-source/mushaf/pg`.

For each page: find the white text area inside the ornamental frame, the red ayah medallions,
the decorated surah title bands, and the text ink (black, low colour) with medallions and
bands removed.

    python3 tools/mushaf/mushaf_pages.py probe 7 30     # print what is found on PDF pages 7..30
"""
import sqlite3, sys
from pathlib import Path
import numpy as np
from PIL import Image
from scipy import ndimage

SRC = Path("book-source/mushaf")
DB = Path("app/src/main/assets/quran_warsh.db")


def page_path(pdf_page: int) -> Path:
    return SRC / f"pg-{pdf_page - 1:03d}.jpg"


def load(pdf_page: int):
    return np.asarray(Image.open(page_path(pdf_page)).convert("RGB")).astype(np.int16)


def text_area(rgb):
    """Bounding box of the white page area inside the frame (largest near-white region)."""
    R, G, B = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    white = (R > 200) & (G > 200) & (B > 190) & ((rgb.max(2) - rgb.min(2)) < 40)
    # Erode so thin white gaps in the ornament can't connect the text area to the page margin.
    white = ndimage.binary_erosion(white, iterations=4)
    lab, n = ndimage.label(white)
    H, W = white.shape
    best, bb = 0, None
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        h = sl[0].stop - sl[0].start; w = sl[1].stop - sl[1].start
        # the text area is big and away from the outer page margin
        if w < 0.45 * W or h < 0.25 * H or sl[1].start < 0.03 * W or sl[1].stop > 0.97 * W:
            continue
        a = h * w
        if a > best:
            best, bb = a, (sl[1].start - 4, sl[0].start - 4, sl[1].stop + 4, sl[0].stop + 4)
    return bb


def medallions(rgb, bnds=(), area=None):
    """Centres (x, y) and radius of the red-brown ayah medallions."""
    R, G, B = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    red = (R > 130) & (R - G > 55) & (R - B > 55) & (G < 140)
    if area is not None:            # the frame's red flowers must not merge with a medallion at a line end
        x0, y0, x1, y1 = area
        keep = np.zeros_like(red); keep[max(0, y0 - 6):y1 + 6, max(0, x0 - 6):x1 + 6] = True; red &= keep
    for b0, b1 in bnds:              # keep band ornaments from fusing with a medallion just above/below
        red[max(0, b0 - 4):b1 + 4] = False
    out = []
    # 1) Solid medallions: close small gaps (a pen stroke through the red), fill the digits.
    solid = ndimage.binary_fill_holes(ndimage.binary_closing(red, iterations=6))
    lab, _ = ndimage.label(solid)
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        h = sl[0].stop - sl[0].start; w = sl[1].stop - sl[1].start
        # medallions are ~44 px; the small red dots (~23 px) are other marks
        if 34 <= h <= 80 and 34 <= w <= 80 and 0.6 < h / w < 1.66 and (lab[sl] == i).sum() > 0.4 * h * w:
            out.append(((sl[1].start + sl[1].stop) / 2, (sl[0].start + sl[0].stop) / 2, max(h, w) / 2))
    # 2) Faded medallions that break into several pieces: group nearby pieces, judge the group's box.
    grp, _ = ndimage.label(ndimage.binary_dilation(red, iterations=7))
    for i, sl in enumerate(ndimage.find_objects(grp), 1):
        ys, xs = np.nonzero(red[sl] & (grp[sl] == i))
        if len(ys) == 0: continue
        h = ys.max() - ys.min() + 1; w = xs.max() - xs.min() + 1
        cx = sl[1].start + (xs.min() + xs.max()) / 2; cy = sl[0].start + (ys.min() + ys.max()) / 2
        if (34 <= h <= 56 and 34 <= w <= 56 and 0.75 < h / w < 1.33 and len(ys) > 0.25 * h * w
                and not any(abs(cx - x) < 30 and abs(cy - y) < 30 for x, y, _ in out)):
            out.append((cx, cy, max(h, w) / 2))
    return out


def bands(rgb, area):
    """Vertical ranges (y0, y1) of decorated surah title bands inside the text area."""
    x0, y0, x1, y1 = area
    a = rgb[y0:y1, x0:x1]
    chroma = a.max(2) - a.min(2)
    col = (chroma > 35).mean(1)            # fraction of coloured pixels per row (text rows: ~0)
    on = col > 0.25
    on = ndimage.binary_closing(on, iterations=8)
    lab, n = ndimage.label(on)
    out = []
    for sl, in ndimage.find_objects(lab):
        if sl.stop - sl.start >= 60:
            out.append((y0 + sl.start, y0 + sl.stop))
    return out


def ink(rgb, area, meds, bnds):
    """Binary text ink inside the area (black/dark, low colour), medallions and bands blanked."""
    x0, y0, x1, y1 = area
    a = rgb[y0:y1, x0:x1]
    lum = 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]
    chroma = a.max(2) - a.min(2)
    m = (lum < 120) & (chroma < 70)
    for cx, cy, r in meds:
        r2 = int(r * 1.35)
        m[max(0, int(cy) - y0 - r2):int(cy) - y0 + r2, max(0, int(cx) - x0 - r2):int(cx) - x0 + r2] = False
    for b0, b1 in bnds:
        m[max(0, b0 - y0 - 6):b1 - y0 + 6, :] = False
    return m


def page_meds(rgb, area, bnds):
    """Medallions inside the text area, not inside a title band."""
    x0, y0, x1, y1 = area
    return [m for m in medallions(rgb, bnds, area) if x0 - 10 < m[0] < x1 + 10 and y0 - 10 < m[1] < y1 + 10
            and not any(b0 - 10 < m[1] < b1 + 10 for b0, b1 in bnds)]


def surah_counts():
    con = sqlite3.connect(DB)
    return [c for (c,) in con.execute("select ayah_count from surahs order by number")]


def probe(a, b):
    for p in range(a, b + 1):
        rgb = load(p)
        area = text_area(rgb)
        if area is None:
            print(p, "no text area"); continue
        x0, y0, x1, y1 = area
        meds = [m for m in medallions(rgb) if x0 - 10 < m[0] < x1 + 10 and y0 - 10 < m[1] < y1 + 10]
        bnds = bands(rgb, area)
        print(f"pdf {p} (printed {p - 5}): area {area} meds {len(meds)} bands {bnds}")


def walk(first=9, last=660, verbose=False):
    """Count medallions page by page from Al-Baqara 5 and check every title band lands on a surah start."""
    counts = surah_counts()
    surah, ayah = 2, 5            # next ayah expected to end (pdf 9 starts in Al-Baqara 5)
    bad = []
    for p in range(first, last + 1):
        rgb = load(p); area = text_area(rgb)
        if area is None:
            print(f"pdf {p}: no text area"); continue
        x0, y0, x1, y1 = area
        bnds = bands(rgb, area)
        meds = page_meds(rgb, area, bnds)
        events = [(my, 'm') for _, my, _ in meds] + [(b0, 'b') for b0, _ in bnds]
        for y, kind in sorted(events):
            if kind == 'b':
                if ayah != 1:
                    bad.append((p, surah, ayah)); print(f"pdf {p}: band but expected {surah}:{ayah}")
                    surah, ayah = surah + 1, 1
            else:
                ayah += 1
                if ayah > counts[surah - 1]:
                    surah, ayah = surah + 1, 1
                    if surah > 114:
                        print(f"pdf {p}: reached the end of the Quran"); return bad
        if verbose: print(f"pdf {p}: next {surah}:{ayah}")
    print(f"stopped at pdf {last}: next {surah}:{ayah}")
    return bad


if __name__ == "__main__":
    if sys.argv[1] == "probe":
        probe(int(sys.argv[2]), int(sys.argv[3]))
    elif sys.argv[1] == "walk":
        bad = walk(*(int(a) for a in sys.argv[2:4]))
        print(len(bad), "desyncs")

#!/usr/bin/env python3
"""
Line fingerprints for the whole scanned Mushaf (Warsh), same algorithm as the book (tools/book/bookfp.py).

    python3 tools/mushaf/build_mushaf_fp.py            # → book-source/mushaf-fp/lines.pkl

Output stays local (book-source/ is git-ignored): the scans and anything derived from them are the
publisher's content and must not go into the repository or the app's releases.

Per text line: PDF page, line number, pitch, the 20-row strip (bookfp.strip) and the ayah spans
[(surah, ayah, col0, col1)] in strip columns. Ayah numbers come from counting the red medallions
from Al-Baqara 5 (PDF 9), exactly like `mushaf_pages.py walk`.
"""
import pickle, sys
from multiprocessing import Pool
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).parent))
sys.path.insert(0, str(Path(__file__).parent.parent / "book"))
import bookfp as fp
import mushaf_pages as m

FIRST, LAST = 9, 644          # PDF 7-8 (Al-Fatiha, Al-Baqara 1-4) are decorated pages: skipped for now
OUT = Path("book-source/mushaf-fp/lines.pkl")


def page_ink(rgb, area, bnds):
    """Ink as the phone sees it (bookfp.binarize on luminance), minus coloured ornament; bands blanked."""
    x0, y0, x1, y1 = area
    a = rgb[y0:y1, x0:x1].astype(int)
    lum = (0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]).astype(int)
    ink = fp.binarize(lum) & ((a.max(2) - a.min(2)) < 70)
    for b0, b1 in bnds:
        ink[max(0, b0 - y0 - 6):max(0, b1 - y0 + 6)] = False
    return fp.remove_rules(ink)


def analyse(p):
    rgb = m.load(p); area = m.text_area(rgb)
    bnds = m.bands(rgb, area); meds = m.page_meds(rgb, area, bnds)
    ink = page_ink(rgb, area, bnds)
    P = fp.pitch_of(ink, 60, 160)
    lines = []
    for base in fp.lines(ink, P):
        st, cx0, s = fp.strip(ink, base, P)
        if st is not None: lines.append((base, st.astype(np.float16), cx0, s))
    x0, y0 = area[0], area[1]
    return p, P, area, lines, [(mx - x0, my - y0) for mx, my, _ in meds], [(b0 - y0, b1 - y0) for b0, b1 in bnds]


def main():
    counts = m.surah_counts()
    with Pool(8) as pool:
        pages = sorted(pool.map(analyse, range(FIRST, LAST + 1), chunksize=4))
    surah, ayah = 2, 5
    out, warn = [], 0
    for p, P, area, lines, meds, bnds in pages:
        bases = [l[0] for l in lines]
        by_line = {i: [] for i in range(len(lines))}
        for mx, my in meds:
            i = int(np.argmin([abs(my - b) for b in bases]))
            if abs(my - bases[i]) > 0.5 * P: warn += 1; print(f"pdf {p}: medallion at y{my:.0f} far from any line")
            by_line[i].append(mx)
        events = sorted([(b, 'l', i) for i, b in enumerate(bases)] + [(b0, 'b', 0) for b0, _ in bnds])
        n_text = 0
        for y, kind, i in events:
            if kind == 'b':
                if ayah != 1: surah, ayah = surah + 1, 1
                continue
            base, st, cx0, s = lines[i]
            spans, xr = [], 10 ** 6
            for mx in sorted(by_line[i], reverse=True):        # right to left
                spans.append((surah, ayah, mx, xr)); ayah += 1
                if ayah > counts[surah - 1]: surah, ayah = surah + 1, 1
                xr = mx
            spans.append((surah, ayah, -10 ** 6, xr))
            W = st.shape[1]
            sp = [(su, a, max(0, int(round((u - cx0) * s))), min(W, int(round((v - cx0) * s)))) for su, a, u, v in spans]
            sp = [t for t in sp if t[3] > t[2] and t[0] <= 114]
            n_text += 1
            out.append(dict(page=p, line=n_text, pitch=P, strip=st, spans=sp, base=area[1] + base, x0=area[0] + cx0, x1=area[0] + cx0 + st.shape[1] / s))
        if len(lines) + 2 * len(bnds) not in (14, 15, 16):
            print(f"pdf {p}: {len(lines)} lines, {len(bnds)} bands, pitch {P}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    pickle.dump(out, open(OUT, "wb"))
    cols = sum(l['strip'].shape[1] for l in out)
    print(f"{len(out)} lines from {len(pages)} pages, {cols} strip columns, {warn} medallion warnings, last ayah {surah}:{ayah}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Python twin of BookMatcher.kt: reads book_refs.bin, decides on book-source/testcrops."""
import json, struct, sys
from pathlib import Path
import numpy as np
from PIL import Image
sys.path.insert(0, str(Path(__file__).parent))
import bookfp as fp

MIN_SCORE, MIN_MARGIN, PAIR_BONUS = 0.42, 0.06, 0.08
DENSITY_MIN, DENSITY_MAX = 0.6, 2.3   # query ink / reference ink in the matched window

def load(path):
    d = open(path, 'rb').read(); o = 4
    (nl,) = struct.unpack_from('<H', d, o); o += 2
    lessons = []
    for _ in range(nl): lessons.append(struct.unpack_from('<HBHH', d, o)); o += 7
    (n,) = struct.unpack_from('<H', d, o); o += 2
    refs = []
    for _ in range(n):
        page, surah, ns = struct.unpack_from('<HBB', d, o); o += 4
        sp = [struct.unpack_from('<HHH', d, o + 6 * i) for i in range(ns)]; o += 6 * ns
        h, w = struct.unpack_from('<BH', d, o); o += 3
        st = np.frombuffer(d, np.uint8, h * w, o).reshape(h, w).astype(np.float32) / 255; o += h * w
        refs.append(dict(page=page, surah=surah, spans=sp, strip=st))
    return lessons, refs

def query_strips(lum):
    ink = fp.deskew(fp.remove_rules(fp.binarize(lum)))
    H = ink.shape[0]
    P = fp.pitch_of(ink, max(12, H // 8), int(H * 0.75))
    cands = [P] if P else []
    if not P:
        bh = fp.band_height(ink)
        if bh: cands = [int(bh * 0.9 * f) for f in (0.85, 1.0, 1.15)]
    out = []
    for Pc in cands:
        if Pc < 8: continue
        for base in fp.lines(ink, Pc):
            st, cx0, s = fp.strip(ink, base, Pc)
            if st is not None and st.shape[1] >= 20: out.append((base, Pc, st))
    return out

def decide(lum, refs):
    strips = query_strips(lum)
    if not strips: return None
    nxt = [i + 1 if i + 1 < len(refs) and refs[i + 1]['page'] == refs[i]['page'] else -1 for i in range(len(refs))]
    S = []
    global COARSE
    if 'COARSE' not in globals(): COARSE = [fp.pool2(r['strip']) for r in refs]
    for base, Pc, q in strips:
        sc, of = fp.match_line(q, [r['strip'] for r in refs], COARSE)
        S.append((base, Pc, q, sc, of))
    cands = [(S_[3][i], i, S_[4][i], S_[2].shape[1], S_[2]) for S_ in S for i in range(len(refs))]
    S.sort(key=lambda t: t[0])
    for a, b in zip(S, S[1:]):
        if a[1] != b[1] or not (0.6 * a[1] <= b[0] - a[0] <= 1.6 * a[1]): continue
        for i in range(len(refs)):
            j = nxt[i]
            if j >= 0: cands.append(((a[3][i] + b[3][j]) / 2 + PAIR_BONUS, i, a[4][i], a[2].shape[1], a[2]))
    cands.sort(key=lambda c: -c[0])
    v, i, off, qw, q = cands[0]; r = refs[i]
    a0 = max(0, off); a1 = min(r['strip'].shape[1], off + qw)
    density = float(q[:, a0 - off:a1 - off].mean() / max(1e-6, r['strip'][:, a0:a1].mean()))
    second = next((c[0] for c in cands[1:] if refs[c[1]]['page'] != r['page']), 0.0)
    ayah = max(r['spans'], key=lambda sp: max(0, min(sp[2], off + qw) - max(sp[1], off)))[0]
    return dict(score=float(v), margin=float(v - second), page=r['page'], surah=r['surah'], ayah=int(ayah), density=density)

if __name__ == "__main__":
    lessons, refs = load('app/src/main/assets/book_refs.bin')
    labels = json.load(open('book-source/testcrops/labels.json'))
    tp = fp_ = rej = 0; neg_fp = 0; ayah_ok = 0
    for L in labels:
        lum = np.asarray(Image.open('book-source/testcrops/' + L['file']).convert('L')).astype(int)
        d = decide(lum, refs)
        acc = d is not None and d['score'] >= MIN_SCORE and d['margin'] >= MIN_MARGIN and DENSITY_MIN <= d['density'] <= DENSITY_MAX
        if L['page'] == 0:
            neg_fp += acc
            if acc: print('NEG ACCEPTED', L['file'], d)
        elif not acc: rej += 1
        elif d['page'] == L['page']:
            tp += 1; ayah_ok += d['ayah'] in L['ayat']
        else: fp_ += 1; print('WRONG', L, d)
    npos = sum(1 for L in labels if L['page']); nneg = len(labels) - npos
    print(f"positives: {tp}/{npos} recognised (ayah inside lesson: {ayah_ok}), {fp_} wrong, {rej} rejected; negatives falsely accepted: {neg_fp}/{nneg}")

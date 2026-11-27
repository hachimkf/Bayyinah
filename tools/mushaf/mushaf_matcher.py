#!/usr/bin/env python3
"""
Whole-Mushaf line matcher (Python model of what the phone would run) + evaluation on the test crops.

    python3 tools/mushaf/mushaf_matcher.py [--rows 20] [--bits 8] [--colscale 1.0]

Stage 1 (coarse, all 9.3k lines at once): every reference strip pooled 4×4 and laid end to end in one
long multi-row signal; the query line is slid over it with an FFT normalized cross-correlation, and the
best score per reference line is kept. Stage 2 (fine): the top-K lines are re-scored at the stored
resolution around the coarse offset. Two query lines that match two consecutive reference lines get a bonus.
"""
import argparse, json, pickle, sys, time
from pathlib import Path
import numpy as np
from PIL import Image
sys.path.insert(0, str(Path(__file__).parent.parent / "book"))
sys.path.insert(0, str(Path(__file__).parent))
import bookfp as fp
import mushaf_pitch as mp

POOL, GAP, TOPK = 4, 16, 40
POOLX = int(__import__('os').environ.get('POOLX', '2'))   # extra column pooling in the coarse stage
PAIR_BONUS = 0.08


def quantize(st, rows, bits, colscale):
    """What the pack would store: `rows` rows, columns scaled, `bits` per value."""
    h, w = st.shape
    ow = max(4, int(round(w * colscale * rows / h)))
    a = fp.resize_box(st.astype(np.float32), rows, ow) if (rows, ow) != (h, w) else st.astype(np.float32)
    lv = (1 << bits) - 1
    return np.round(a * lv) / lv


def ncc_fft(q, R, Rf, S1, S2):
    """Zero-mean NCC of q (h×wq) at every column of R (h×N); Rf = cached rfft of R's rows."""
    h, wq = q.shape; N = R.shape[1]
    qz = q - q.mean(); qn = np.sqrt((qz * qz).sum()) + 1e-9
    L = 2 * (Rf.shape[1] - 1)
    acc = np.zeros(Rf.shape[1], complex)
    for r in range(h):
        acc += Rf[r] * np.conj(np.fft.rfft(qz[r], L))
    num = np.fft.irfft(acc, L)
    num = num[:N - wq + 1]
    s1 = S1[wq:] - S1[:-wq]; s2 = S2[wq:] - S2[:-wq]
    n = h * wq
    var = np.maximum(s2 - s1 * s1 / n, 1e-6)
    return num / (np.sqrt(var) * qn)


class Index:
    def __init__(self, lines, rows=20, bits=8, colscale=1.0):
        self.lines = lines
        self.tmpl = mp.template([l['strip'] for l in lines[::7]])
        self.refs = [quantize(l['strip'], rows, bits, colscale) for l in lines]
        self.rows = rows; self.colscale = colscale
        cr = [pool(r, POOL * rows // 20, POOLX) for r in self.refs]
        self.h = cr[0].shape[0]
        starts, pos = [], 0
        for c in cr: starts.append(pos); pos += c.shape[1] + GAP
        R = np.zeros((self.h, pos))
        for s, c in zip(starts, cr): R[:, s:s + c.shape[1]] = c
        self.R, self.starts = R, np.array(starts)
        L = 1 << int(np.ceil(np.log2(pos + 1024)))
        self.Rf = np.fft.rfft(R, L, axis=1)
        self.widths = np.array([c.shape[1] for c in cr])
        self.S1 = np.concatenate([[0], np.cumsum(R.sum(0))]); self.S2 = np.concatenate([[0], np.cumsum((R * R).sum(0))])
        self.nxt = np.array([i + 1 if i + 1 < len(lines) and lines[i + 1]['page'] == lines[i]['page'] else -1 for i in range(len(lines))])

    def coarse(self, q):
        """Best coarse NCC per reference line for one query strip (20 rows), plus what fine() needs."""
        qs = quantize(q, self.rows, 8, self.colscale)
        k = POOL * self.rows // 20
        qc = pool(qs, k, POOLX); wq = qc.shape[1]
        n = len(self.refs)
        if wq < 6: return None
        if wq > 1000: return None
        v = ncc_fft(qc, self.R, self.Rf, self.S1, self.S2)
        # window position t belongs to the ref containing its centre, if ≥70% of the query overlaps that ref
        c = np.arange(len(v)) + wq // 2
        ref = np.searchsorted(self.starts, c, side='right') - 1
        local = c - self.starts[ref]
        v = np.where((local >= int(0.2 * wq)) & (local <= self.widths[ref] - int(0.2 * wq)), v, -1.0)
        best = np.full(n, -1.0); np.maximum.at(best, ref, v)
        return dict(best=best, v=v, bounds=np.searchsorted(ref, np.arange(n + 1)), qs=qs, k=k)

    def fine(self, C, i, qs=None):
        """Fine NCC of a query strip against ref i around the coarse offset found for C's strip.
        qs: the same line at another scale (default: C's own strip)."""
        if i < 0 or C['best'][i] <= -1: return -1.0, 0
        b = C['bounds']; t = b[i] + int(np.argmax(C['v'][b[i]:b[i + 1]]))
        o = (t - self.starts[i]) * C['k'] * POOLX
        q = C['qs'] if qs is None else quantize(qs, self.rows, 8, self.colscale)
        d = C['k'] * POOLX + 2 + int(0.05 * q.shape[1])
        return fp.ncc_window(q, self.refs[i], o - d, o + d)

    def decide(self, lum):
        H = lum.shape[0]
        strips = query_strips(lum, self.tmpl)
        groups = {}
        for base, Pc, q in strips:
            groups.setdefault(Pc, []).append((base, q))
        if not groups: return None
        P1 = sorted(groups)[len(groups) // 2]          # the fitted pitch (middle of SCALES)

        def full_lines(Pc):
            G = groups[Pc]
            # drop lines the crop cuts through (their strip is mostly outside the image)
            f = [g for g in G if g[0] - 0.62 * Pc >= -0.25 * Pc and g[0] + 0.38 * Pc <= H + 0.25 * Pc] or G
            return sorted(f, key=lambda g: g[0])

        # coarse search (FFT over the whole Mushaf) once per line, at the fitted pitch only
        full = full_lines(P1)
        ks = [int(round((g[0] - full[0][0]) / P1)) for g in full]
        use = [(kk, C, g) for kk, g in zip(ks, full) for C in [self.coarse(g[1])] if C is not None]
        if not use: return None
        n = len(self.refs); A = np.zeros(n); shifted = []
        for kk, C, g in use:
            idx = np.arange(n)
            for _ in range(kk): idx = np.where(idx >= 0, self.nxt[np.maximum(idx, 0)], -1)
            shifted.append(idx)
            A += np.where(idx >= 0, C['best'][np.maximum(idx, 0)], -1.0)
        A /= len(use)
        anchors = np.argsort(-A)[:TOPK]
        # fine scoring of the anchors at each scale (same lines, matched by baseline)
        cands = []
        for Pc in groups:
            other = full_lines(Pc)
            qs = [min(other, key=lambda o: abs(o[0] - g[0]))[1] for kk, C, g in use] if Pc != P1 else [None] * len(use)
            for i in anchors:
                fs = [self.fine(C, idx[i], q) for (kk, C, g), idx, q in zip(use, shifted, qs)]
                widths = [(q if q is not None else g[1]).shape[1] for (kk, C, g), q in zip(use, qs)]
                cands.append((float(np.mean([f[0] for f in fs])), int(i), fs, [idx[i] for idx in shifted], widths))
        cands.sort(key=lambda c: -c[0])
        v, i, fs, refs_i, widths = cands[0]
        L0 = self.lines[i]
        second = next((c[0] for c in cands[1:] if abs(c[1] - i) > 2 or self.lines[c[1]]['page'] != L0['page']), 0.0)
        mid = len(use) // 2; r = refs_i[mid]
        if r < 0: r = i; mid = 0
        L = self.lines[r]; off = fs[mid][1]; qw = int(widths[mid] * self.rows / 20 * self.colscale)
        sp = max(L['spans'], key=lambda t: max(0, min(t[3], off + qw) - max(t[2], off)))
        return dict(score=v, margin=float(v - second), page=L['page'], line=L['line'], surah=sp[0], ayah=sp[1], nlines=len(use))


SCALES = (0.96, 1.0, 1.04)


def query_strips(lum, tmpl):
    """Lines of a camera crop → [(base, pitch, strip)] using the template-fitted pitch (× SCALES)."""
    ink = fp.deskew(fp.remove_rules(fp.binarize(lum)))
    H = ink.shape[0]
    r = mp.fit_pitch(ink, tmpl, 24, 200)
    if r is None or r[0] is None: return []
    P0, ph, _ = r
    prof = fp.smooth(ink.sum(1).astype(float), max(3, int(P0) // 4))
    out = []
    for f in SCALES:
        P = P0 * f
        y = ph
        while y < H + 0.5 * P:
            a, b = int(max(0, y - P / 8)), int(min(H, y + P / 8 + 1))
            base = a + int(np.argmax(prof[a:b])) if b > a else int(y)
            st, cx0, s = fp.strip(ink, base, P)
            if st is not None and st.shape[1] >= 20: out.append((base, round(P, 2), st))
            y += P
        # phase may start below the first line: also look one pitch above
    return out


def pool(a, k, kx=1):
    kx *= k
    h, w = a.shape[0] // k * k, a.shape[1] // kx * kx
    return a[:h, :w].reshape(h // k, k, w // kx, kx).mean((1, 3))


IX = None


def _init(args):
    global IX
    lines = pickle.load(open('book-source/mushaf-fp/lines.pkl', 'rb')); IX = Index(lines, *args)


def _one(L):
    lum = np.asarray(Image.open('book-source/mushaf-crops/' + L['file']).convert('L')).astype(int)
    t = time.time(); d = IX.decide(lum); return (L, d, time.time() - t)


def evaluate(args, labels):
    from multiprocessing import Pool
    with Pool(8, _init, (args,)) as pool:
        return pool.map(_one, labels, chunksize=2)


def summarize(res, min_score, min_margin):
    tp = wrong = rej = nfp = ayah_ok = 0
    for L, d, _ in res:
        acc = d is not None and d['score'] >= min_score and d['margin'] >= min_margin
        if L['page'] == 0: nfp += acc
        elif not acc: rej += 1
        elif d['page'] == L['page'] and abs(d['line'] - (L['lines'] or [0])[0]) <= len(L['lines']):
            tp += 1; ayah_ok += [d['surah'], d['ayah']] in L['ayat']
        else: wrong += 1
    npos = sum(1 for L, _, _ in res if L['page']); nneg = len(res) - npos
    return tp, ayah_ok, wrong, rej, nfp, npos, nneg


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument('--rows', type=int, default=20); ap.add_argument('--bits', type=int, default=8)
    ap.add_argument('--colscale', type=float, default=1.0); ap.add_argument('--n', type=int, default=0)
    a = ap.parse_args()
    labels = json.load(open('book-source/mushaf-crops/labels.json'))
    if a.n: labels = labels[:a.n] + [L for L in labels if L['page'] == 0][:a.n // 3]
    res = evaluate((a.rows, a.bits, a.colscale), labels)
    pickle.dump(res, open(f'book-source/mushaf-fp/res_r{a.rows}_b{a.bits}_c{a.colscale}.pkl', 'wb'))
    print(f"mean {np.mean([r[2] for r in res]):.2f}s per crop")
    pos = sorted(d['score'] for L, d, _ in res if L['page'] and d); neg = sorted(d['score'] for L, d, _ in res if not L['page'] and d)
    print("neg scores top:", [round(x, 3) for x in neg[-5:]])
    for ms in (0.40, 0.45, 0.50, 0.55, 0.60):
        for mm in (0.0, 0.03, 0.06):
            tp, ay, w, rj, nf, npos, nneg = summarize(res, ms, mm)
            print(f"score≥{ms:.2f} margin≥{mm:.2f}: recognised {tp}/{npos} (ayah ok {ay}), wrong {w}, rejected {rj}, negatives accepted {nf}/{nneg}")

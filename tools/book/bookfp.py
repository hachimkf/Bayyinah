"""
Book line fingerprints — the exact algorithm used on the phone (BookMatcher.kt). Keep in sync.

ink      : local-mean threshold (same as TextImagePrep.binarize: radius max(6, min(w,h)/4), delta 14)
rules    : drop long thin connected components (box borders, table rules)
deskew   : vertical shear y' = y - tan(a)·(x - w/2), a ∈ [-6°, 6°] maximizing Σ rowcount²
pitch    : line spacing from the autocorrelation of the row ink profile
lines    : evenly spaced baselines (best phase), refined ±pitch/6
strip    : rows [base-0.62·pitch, base+0.38·pitch) of the ink-x-extent, box-averaged to
           H=20 rows × round(width·20/pitch) columns (values 0..1)
"""
import numpy as np
from scipy import ndimage

H_STRIP = 20
UPP, DOWNP = 0.62, 0.38


def binarize(lum):
    g = lum.astype(np.int64); h, w = g.shape
    r = max(6, min(w, h) // 4)
    ii = np.zeros((h + 1, w + 1), np.int64); ii[1:, 1:] = g.cumsum(0).cumsum(1)
    ys = np.arange(h); xs = np.arange(w)
    y0 = np.clip(ys - r, 0, h); y1 = np.clip(ys + r + 1, 0, h)
    x0 = np.clip(xs - r, 0, w); x1 = np.clip(xs + r + 1, 0, w)
    s = ii[y1][:, x1] - ii[y0][:, x1] - ii[y1][:, x0] + ii[y0][:, x0]
    area = (y1 - y0)[:, None] * (x1 - x0)[None, :]
    mean = s // area
    return (mean - g) > 14


def remove_rules(ink):
    lab, n = ndimage.label(ink, structure=np.ones((3, 3)))
    if n == 0: return ink
    keep = np.ones(n + 1, bool); keep[0] = False
    H, W = ink.shape
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        h = sl[0].stop - sl[0].start; w = sl[1].stop - sl[1].start
        if (w > 0.35 * W and h < 0.06 * H + 6) or (h > 0.5 * H and w < 12): keep[i] = False
    return keep[lab]


def deskew(ink):
    """Returns sheared ink (same shape) — vertical shear by the best angle."""
    H, W = ink.shape
    ys, xs = np.nonzero(ink)
    if len(xs) < 50: return ink
    xc = xs - W / 2.0
    best, best_t = -1.0, 0.0
    for a10 in range(-60, 61, 5):
        t = np.tan(np.radians(a10 / 10.0))
        yy = np.floor(ys - t * xc + 0.5).astype(int)
        h = np.bincount(yy - yy.min()).astype(float)
        e = (h * h).sum()
        if e > best: best, best_t = e, t
    if abs(best_t) < np.tan(np.radians(0.25)): return ink
    out = np.zeros_like(ink)
    shift = np.floor(-best_t * (np.arange(W) - W / 2.0) + 0.5).astype(int)
    for x in range(W):
        s = shift[x]; col = ink[:, x]
        if s >= 0: out[s:, x] = col[:H - s] if s < H else 0
        else: out[:H + s, x] = col[-s:]
    return out


def pitch_of(ink, lo, hi):
    prof = ink.sum(1).astype(float)
    if prof.max() == 0: return None
    p = prof - prof.mean(); n = len(p); hi = min(hi, n - 1)
    if hi <= lo + 2: return None
    ac0 = (p * p).sum() + 1e-9
    prev2 = prev1 = None
    vals = [(p[:n - k] * p[k:]).sum() / ac0 for k in range(lo, hi + 1)]
    for i in range(1, len(vals) - 1):
        if vals[i] > vals[i - 1] and vals[i] >= vals[i + 1] and vals[i] > 0.15:
            return lo + i
    return None


def smooth(v, win):
    win = max(1, win); c = np.concatenate([[0.0], np.cumsum(v)])
    n = len(v); out = np.empty(n)
    for i in range(n):
        a = max(0, i - win // 2); b = min(n, i + win // 2 + 1)
        out[i] = (c[b] - c[a]) / (b - a)
    return out


def lines(ink, pitch):
    prof = smooth(ink.sum(1).astype(float), max(3, pitch // 4))
    if prof.max() == 0: return []
    P = int(pitch); best, phase = -1.0, 0
    for ph in range(P):
        v = prof[ph::P].sum()
        if v > best: best, phase = v, ph
    rows = []
    for y in range(phase, len(prof), P):
        a, b = max(0, y - P // 6), min(len(prof), y + P // 6 + 1)
        yy = a + int(np.argmax(prof[a:b]))
        if prof[yy] >= 0.08 * prof.max(): rows.append(yy)
    return rows


def band_height(ink):
    prof = ink.sum(1).astype(float)
    if prof.max() == 0: return None
    on = np.where(prof > 0.06 * prof.max())[0]
    return int(on[-1] - on[0] + 1) if len(on) else None


def resize_box(a, oh, ow):
    """Area-average resize of a 2-D float array to (oh, ow)."""
    h, w = a.shape
    ii = np.zeros((h + 1, w + 1)); ii[1:, 1:] = a.cumsum(0).cumsum(1)
    ys = np.linspace(0, h, oh + 1); xs = np.linspace(0, w, ow + 1)
    out = np.empty((oh, ow), np.float32)
    for i in range(oh):
        y0, y1 = int(ys[i]), max(int(ys[i]) + 1, int(np.ceil(ys[i + 1])))
        for j in range(ow):
            x0, x1 = int(xs[j]), max(int(xs[j]) + 1, int(np.ceil(xs[j + 1])))
            s = ii[y1, x1] - ii[y0, x1] - ii[y1, x0] + ii[y0, x0]
            out[i, j] = s / ((y1 - y0) * (x1 - x0))
    return out


def strip(ink, base, pitch):
    """→ (strip 20×W float, x of first ink column, scale px→strip col)"""
    top = int(round(base - UPP * pitch)); bot = int(round(base + DOWNP * pitch))
    H, W = ink.shape
    sub = np.zeros((bot - top, W), bool)
    a0, a1 = max(0, top), min(H, bot)
    if a1 > a0: sub[a0 - top:a1 - top] = ink[a0:a1]
    cols = np.where(sub.any(0))[0]
    if len(cols) == 0: return None, 0, 0
    cx0, cx1 = cols.min(), cols.max() + 1
    s = H_STRIP / float(bot - top)
    ow = max(4, int(round((cx1 - cx0) * s)))
    return resize_box(sub[:, cx0:cx1].astype(np.float32), H_STRIP, ow), int(cx0), s


def ncc_slide(q, r, min_overlap=0.7, step=1):
    hq, wq = q.shape; hr, wr = r.shape
    best, best_off = -1.0, 0
    for off in range(-int(wq * (1 - min_overlap)), wr - int(wq * min_overlap) + 1, step):
        a0 = max(0, off); a1 = min(wr, off + wq)
        if a1 - a0 < min_overlap * wq: continue
        rr = r[:, a0:a1]; qq = q[:, a0 - off:a1 - off]
        rz = rr - rr.mean(); qz = qq - qq.mean()
        den = np.sqrt((rz * rz).sum() * (qz * qz).sum())
        v = (rz * qz).sum() / den if den > 1e-9 else -1
        if v > best: best, best_off = v, off
    return best, best_off


def pool2(a):
    """2×2 average pooling (drops odd last row/col) — coarse search resolution."""
    h, w = a.shape[0] // 2 * 2, a.shape[1] // 2 * 2
    return a[:h, :w].reshape(h // 2, 2, w // 2, 2).mean((1, 3))


def ncc_window(q, r, lo_off, hi_off, min_overlap=0.7):
    """ncc_slide restricted to offsets [lo_off, hi_off]."""
    hq, wq = q.shape; wr = r.shape[1]
    best, best_off = -1.0, lo_off
    for off in range(lo_off, hi_off + 1):
        a0 = max(0, off); a1 = min(wr, off + wq)
        if a1 - a0 < min_overlap * wq: continue
        rr = r[:, a0:a1]; qq = q[:, a0 - off:a1 - off]
        rz = rr - rr.mean(); qz = qq - qq.mean()
        den = np.sqrt((rz * rz).sum() * (qz * qz).sum())
        v = (rz * qz).sum() / den if den > 1e-9 else -1
        if v > best: best, best_off = v, off
    return best, best_off


def match_line(q, refs, coarse_refs, top_k=6):
    """Coarse-to-fine: half-resolution scan of every reference, full-resolution refine of the top_k.
    Returns (scores[], offsets[]) — refined values for top_k, coarse for the rest (never winners)."""
    qc = pool2(q)
    n = len(refs); sc = np.full(n, -1.0); of = np.zeros(n, int)
    for i, rc in enumerate(coarse_refs):
        v, off = ncc_slide(qc, rc); sc[i] = v - 0.05; of[i] = off * 2   # coarse scores slightly penalized
    for i in np.argsort(-sc)[:top_k]:
        v, off = ncc_window(q, refs[i], of[i] - 3, of[i] + 3); sc[i] = v; of[i] = off
    return sc, of

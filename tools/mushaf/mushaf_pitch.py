"""
Line pitch of a camera crop of the Mushaf, by fitting the Mushaf's average one-line row-ink profile.

The generic autocorrelation pitch (bookfp.pitch_of) often locks onto the gap between the letter
bodies and the vowel marks above them in this script and returns ~0.75 of the real pitch. Here the
crop's row profile is compared with a periodic template (mean profile of one line, from the
reference strips) at every pitch and phase; the best normalized correlation wins.
"""
import numpy as np

NB = 20     # template bins per line (the strip rows: base-0.62P .. base+0.38P)


def template(strips):
    t = np.mean([s.astype(np.float32).mean(1) for s in strips], 0)
    return t / t.max()


def fit_pitch(ink, tmpl, lo, hi, step=1.0):
    """→ (pitch, phase of the first baseline, score). ink: deskewed binary crop."""
    prof = ink.sum(1).astype(np.float64)
    H = len(prof)
    if prof.max() == 0: return None
    pz = prof - prof.mean(); pn = np.sqrt((pz * pz).sum()) + 1e-9
    y = np.arange(H)
    best = (-2.0, None, None)
    tz = np.concatenate([tmpl, tmpl[:1]])
    for P in np.arange(lo, min(hi, 2.2 * H) + 1e-9, step):
        for ph in np.arange(0, P, max(1.0, P / 40)):
            u = ((y - ph) / P + 0.62) % 1.0 * NB       # position inside the line, 0 = top of the strip
            i0 = np.floor(u).astype(int); f = u - i0
            t = tz[i0] * (1 - f) + tz[i0 + 1] * f
            t = t - t.mean(); v = (t * pz).sum() / ((np.sqrt((t * t).sum()) + 1e-9) * pn)
            if v > best[0]: best = (v, P, ph)
    return best[1], best[2], best[0]

#!/usr/bin/env python3
"""Real-world negatives for the Mushaf matcher: camera-like crops of scenes that are NOT Mushaf text.

    python3 tools/mushaf/gen_nontext_negs.py      # adds nreal*.jpg to book-source/mushaf-crops/labels.json

Sources (all local): the Android emulator's virtual room textures (the scene that caused a false
accept), macOS wallpapers, pages of her book (Arabic lesson text, pictures), and synthetic scenes —
blank paper, stripes/blinds, screens, noise, skin-like blobs. Each gets phone-camera blur/noise/JPEG.
"""
import glob, json, random, subprocess, sys, tempfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, str(Path(__file__).parent.parent / "book"))
from gen_testcrops import camera

OUT = Path("book-source/mushaf-crops")
EMU = Path.home() / "Library/Android/sdk/emulator/resources"


def sources(tmp):
    srcs = []
    for f in ("Toren1BD_Main.png", "Toren1BD_Decor.png", "poster.png"):
        if (EMU / f).exists(): srcs.append(("room", Image.open(EMU / f).convert("RGB"), 25))
    for f in sorted(glob.glob("/System/Library/Desktop Pictures/*.heic"))[:6]:
        jpg = Path(tmp) / (Path(f).stem.replace(" ", "_") + ".jpg")
        subprocess.run(["sips", "-s", "format", "jpeg", "-Z", "1600", f, "--out", str(jpg)], capture_output=True)
        if jpg.exists(): srcs.append(("wallpaper", Image.open(jpg).convert("RGB"), 5))
    pdf = Path("book-source/91-135_compressed.pdf")
    if pdf.exists():
        subprocess.run(["pdftoppm", "-r", "110", "-jpeg", "-f", "2", "-l", "44", str(pdf), str(Path(tmp) / "bk")], capture_output=True)
        for f in sorted(Path(tmp).glob("bk-*.jpg"))[::3]:
            srcs.append(("book", Image.open(f).convert("RGB"), 4))
    return srcs


def synthetic(rng, k):
    W, H = 900, 450
    kind = ["paper", "stripes", "screen", "noise", "skin", "shelf"][k % 6]
    im = Image.new("RGB", (W, H), tuple(rng.randint(150, 250) for _ in range(3)))
    d = ImageDraw.Draw(im)
    if kind == "paper":
        im = Image.new("RGB", (W, H), (240, 238, 230))
        for i in range(rng.randint(0, 3)):
            d = ImageDraw.Draw(im); x = rng.randint(0, W); d.line((x, 0, x + rng.randint(-200, 200), H), fill=(200, 198, 190), width=rng.randint(1, 4))
    elif kind == "stripes":               # blinds, fabric, ruled lines: periodic rows
        p = rng.randint(20, 90); th = rng.randint(3, p // 2)
        for y in range(0, H, p): d.rectangle((0, y, W, y + th), fill=tuple(rng.randint(20, 120) for _ in range(3)))
    elif kind == "screen":
        d.rectangle((0, 0, W, H), fill=(30, 30, 35))
        for _ in range(rng.randint(6, 30)):
            x, y = rng.randint(0, W - 60), rng.randint(0, H - 40)
            d.rectangle((x, y, x + rng.randint(20, 160), y + rng.randint(8, 40)), fill=tuple(rng.randint(60, 255) for _ in range(3)))
    elif kind == "noise":
        a = np.clip(rng.uniform(80, 200) + np.random.default_rng(k).normal(0, rng.uniform(10, 60), (H, W, 3)), 0, 255).astype(np.uint8)
        im = Image.fromarray(a).filter(ImageFilter.GaussianBlur(rng.uniform(0, 3)))
    elif kind == "skin":                  # face / hand: soft blobs and a few dark features
        im = Image.new("RGB", (W, H), (rng.randint(150, 230), rng.randint(110, 170), rng.randint(90, 140))); d = ImageDraw.Draw(im)
        for _ in range(rng.randint(2, 6)):
            x, y, r = rng.randint(0, W), rng.randint(0, H), rng.randint(10, 60)
            d.ellipse((x - r, y - r // 2, x + r, y + r // 2), fill=(rng.randint(30, 90),) * 3)
        im = im.filter(ImageFilter.GaussianBlur(rng.uniform(2, 6)))
    else:                                 # shelf of books: vertical spines
        x = 0
        while x < W:
            w = rng.randint(15, 60); d.rectangle((x, 0, x + w, H), fill=tuple(rng.randint(20, 220) for _ in range(3))); x += w + rng.randint(0, 4)
    return kind, im


def main(n_real=150, n_syn=60, seed=23, prefix="nreal"):
    rng = random.Random(seed)
    labels = [L for L in json.load(open(OUT / "labels.json")) if not L['file'].startswith(prefix)]
    with tempfile.TemporaryDirectory() as td:
        srcs = sources(td)
        weights = [w for _, _, w in srcs]
        k = 0
        for _ in range(n_real):
            kind, im, _ = rng.choices(srcs, weights)[0]
            W, H = im.size
            fw = W * rng.uniform(0.15, 0.6); fh = fw * rng.uniform(0.3, 0.65)
            x = rng.uniform(0, W - fw); y = rng.uniform(0, max(1, H - fh))
            crop = im.crop((int(x), int(y), int(x + fw), int(y + fh)))
            s = min(1.0, rng.uniform(380, 900) / crop.width); crop = crop.resize((max(32, int(crop.width * s)), max(16, int(crop.height * s))))
            name = f"{prefix}{k:03d}.jpg"; camera(crop, rng).save(OUT / name, quality=rng.randint(55, 85)); k += 1
            labels.append(dict(file=name, page=0, kind=kind))
        for j in range(n_syn):
            kind, im = synthetic(rng, j + seed)
            s = rng.uniform(0.45, 1.0); im = im.resize((int(im.width * s), int(im.height * s)))
            name = f"{prefix}{k:03d}.jpg"; camera(im, rng).save(OUT / name, quality=rng.randint(55, 85)); k += 1
            labels.append(dict(file=name, page=0, kind=kind))
    json.dump(labels, open(OUT / "labels.json", "w"))
    from collections import Counter
    print(f"added {k} non-text negatives:", dict(Counter(L.get('kind') for L in labels if L['file'].startswith(prefix))))


if __name__ == "__main__":
    a = sys.argv[1:]
    main(int(a[0]), int(a[1]), int(a[2]), a[3]) if a else main()

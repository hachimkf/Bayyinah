#!/usr/bin/env python3
"""
Build the private Mushaf TEXT pack (Moroccan font + Mushaf Mohammadi display text) from the
handoff folder prepared from the Foundation's reader (font AALMAGHRIBI v4.9.6, 638 pages).

    python3 tools/mushaf/build_text_pack.py <muhammadi-handoff folder>
    → book-source/mushaf-text/mushaf-text.bayyinah-pack   (a zip; git-ignored, never released)

The font and text belong to the Mohammed VI Foundation; no redistribution licence is known, so the
pack is handed to her directly and imported in Settings, like the scan pack.

Contents of the zip:
  font.otf          the reader's font, unchanged
  ayat.tsv          surah \t ayah \t display words of the ayah (raw encoding, joined by spaces;
                    the basmala is left out: it is not an ayah in Warsh)
  pages.tsv         page \t line \t items, items joined by U+2016; item = kind:surah:ayah:text
                    kind w = word, m = ayah marker "(N)", t = surah title, o = hizb-eighth ornament
  manifest.json     counts, for checking at import
The display text uses the font's own encoding (e.g. ۱للَّهِ): it is kept byte for byte and is never
used for search or scanning.
"""
import json, sys, zipfile
from pathlib import Path

SEP = "‖"
OUT = Path("book-source/mushaf-text/mushaf-text.bayyinah-pack")


def main(src: Path):
    index = json.loads((src / "data/ayah-index.json").read_text(encoding="utf-8"))
    by_id = {e["ayaId"]: (e["suraNum"], e["ayaNum"]) for e in index}
    ayat = {}                       # (s, a) -> [words]
    page_lines = []                 # (page, line, [items])
    for f in sorted((src / "data/pages").glob("*.txt")):
        page = int(f.stem)
        line, items = 1, []
        for row in f.read_text(encoding="utf-8").split("\n"):
            if not row.strip(): continue
            parts = row.split(SEP)
            if len(parts) != 5: raise SystemExit(f"{f.name}: bad row {row!r}")
            wid, aid, sura, cls, text = parts
            classes = cls.split()
            if "classLineEnd" in classes:
                if items: page_lines.append((page, line, items)); line += 1
                items = []
                continue
            if not wid or not text.strip(): continue
            if "suraTitle" in classes:
                s = int(sura) if sura else 0
                items.append(f"t:{s}:0:{text.strip()}")
                continue
            if not aid: continue
            s, a = by_id[int(aid)]
            if "tomon" in classes:          # hizb-eighth ornament: on the page, not in the ayah text
                items.append(f"o:{s}:{a}:{text.strip()}")
            elif "aya" in classes:
                items.append(f"m:{s}:{a}:{text.strip()}")
            else:
                items.append(f"w:{s}:{a}:{text}")
                ayat.setdefault((s, a), []).append(text)
        if items: page_lines.append((page, line, items))

    # Leave the basmala out of ayah 1 (first four words starting with بِسْمِ).
    for (s, a), words in ayat.items():
        if a == 1 and words and words[0].startswith("بِسْمِ") and len(words) > 4:
            ayat[(s, a)] = words[4:]
    assert len(ayat) == 6214, len(ayat)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(src / "assets/AALMAGHRIBI_v4.9.6.otf", "font.otf")
        z.writestr("ayat.tsv", "".join(f"{s}\t{a}\t{' '.join(w)}\n" for (s, a), w in sorted(ayat.items())))
        z.writestr("pages.tsv", "".join(f"{p}\t{l}\t{SEP.join(it)}\n" for p, l, it in page_lines))
        z.writestr("manifest.json", json.dumps({"kind": "bayyinah-mushaf-text", "version": 1, "ayat": len(ayat),
                                                 "pages": len({p for p, _, _ in page_lines}), "lines": len(page_lines)}))
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB): {len(ayat)} ayat, {len(page_lines)} lines")


if __name__ == "__main__":
    main(Path(sys.argv[1]))

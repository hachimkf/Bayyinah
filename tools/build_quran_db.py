#!/usr/bin/env python3
"""Build app/src/main/assets/quran_warsh.db.

Full Warsh Mushaf (default, 6,214 ayat, Madani count, KFGQPC text via quran-ws/quran-text):
    python3 tools/build_quran_db.py tools/source/warsh.json app/src/main/assets/quran_warsh.db

Your own subset from a TSV (surah, ayah, surah_name, verse_text, audio_file; '#' lines ignored):
    python3 tools/build_quran_db.py my_verses.tsv app/src/main/assets/quran_warsh.db

`id` is surah*1000+ayah, so reading order (Previous/Next) is preserved even with gaps.
`normalized_text` mirrors ArabicNormalizer.normalize() in the app.
"""
import json
import sqlite3
import sys
from pathlib import Path

MAP = {}
for c in "إأآٱٲٳ": MAP[c] = "ا"
for c in "ىیېۑےࢆ": MAP[c] = "ي"
MAP.update({"ة": "ه", "ؤ": "و", "ئ": "ي", "ء": "", "ڢ": "ف", "ڡ": "ف",
            "ڧ": "ق", "ٯ": "ق", "ک": "ك", "ھ": "ه", "ـ": ""})
SPACES = set(" \t\n\r ‏‎‌‍")
TASHKEEL = set("ًٌٍَُِّْٕٓٔ")


def map_char(ch: str) -> str:
    if ch in MAP:
        return MAP[ch]
    if ch in SPACES:
        return " "
    o = ord(ch)
    if 0x0870 <= o <= 0x0882:          # KFGQPC Arabic Extended-B alef forms (wasl, etc.)
        return "ا"
    if (0x064B <= o <= 0x065F or o == 0x0670 or 0x0610 <= o <= 0x061A
            or 0x06D6 <= o <= 0x06ED or 0x0883 <= o <= 0x0885 or 0x0887 <= o <= 0x089F
            or 0x08CA <= o <= 0x08FF):
        return ""
    if 0x0621 <= o <= 0x064A:
        return ch
    return " "


def normalize(text: str) -> str:
    return " ".join("".join(map_char(c) for c in text).split())


def plain_name(name: str) -> str:
    return "".join(c for c in name if c not in TASHKEEL)


def from_warsh_json(path: Path):
    d = json.loads(path.read_text(encoding="utf-8"))
    words, starts = d["words"], d["ayah_starts"]
    verses, surahs = [], []
    for s in d["surahs"]:
        n, first, count = s["number"], s["first_ayah"], s["ayah_count"]
        name = plain_name(s["name_ar"])
        surahs.append((n, name, s["name_en"], s["revelation"], count))
        for a in range(count):
            i = first + a
            end = starts[i + 1] if i + 1 < len(starts) else len(words)
            text = " ".join(words[starts[i]:end])
            verses.append((n * 1000 + a + 1, n, a + 1, name, text, normalize(text), f"{n:03d}{a + 1:03d}.opus"))
    return verses, surahs


def from_tsv(path: Path):
    verses, names = [], {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        surah, ayah, name, text, audio = line.split("\t")
        s, a = int(surah), int(ayah)
        names.setdefault(s, [name.strip(), 0])[1] += 1
        verses.append((s * 1000 + a, s, a, name.strip(), text.strip(), normalize(text), audio.strip()))
    surahs = [(s, n, "", "", c) for s, (n, c) in sorted(names.items())]
    return verses, surahs


def main(src: str, dst: str) -> None:
    p = Path(src)
    verses, surahs = from_warsh_json(p) if p.suffix == ".json" else from_tsv(p)
    out = Path(dst)
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    con = sqlite3.connect(out)
    con.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE verses (
            id INTEGER PRIMARY KEY,
            surah INTEGER NOT NULL,
            ayah INTEGER NOT NULL,
            surah_name TEXT NOT NULL,
            verse_text TEXT NOT NULL,
            normalized_text TEXT,
            audio_file TEXT
        );
        CREATE TABLE surahs (
            number INTEGER PRIMARY KEY,
            name_ar TEXT NOT NULL,
            name_en TEXT NOT NULL,
            revelation TEXT NOT NULL,
            ayah_count INTEGER NOT NULL
        );
    """)
    con.executemany("INSERT INTO verses VALUES (?,?,?,?,?,?,?)", verses)
    con.executemany("INSERT INTO surahs VALUES (?,?,?,?,?)", surahs)
    con.commit()
    con.execute("VACUUM")
    con.close()
    print(f"Wrote {len(verses)} verses / {len(surahs)} surahs to {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])

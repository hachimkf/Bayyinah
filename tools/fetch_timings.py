#!/usr/bin/env python3
"""Download per-ayah timings (Warsh numbering) of mp3quran.net reciters into tools/source/timings/.

    python3 tools/fetch_timings.py            # all reciters in RECITERS
Output per reciter: {"read": id, "folder": url, "surahs": {"1": [[start_ms, end_ms], ...], ...}}
Only start/end are kept (the API also returns page polygons we don't need).
"""
import json, sys, time, urllib.request
from pathlib import Path

# mp3quran "read" ids of Warsh 'an Nafi' recitations that publish ayah timings.
RECITERS = {
    80:  ("عمر القزابري", "Omar Al-Qazabri"),
    120: ("محمود خليل الحصري", "Mahmoud Khalil Al-Husary"),
    16:  ("العيون الكوشي", "El Ayoun El Kouchi"),
    14:  ("ياسين الجزائري", "Yassin Al-Jazairi"),
}
API = "https://mp3quran.net/api/v3/ayat_timing"
OUT = Path(__file__).parent / "source" / "timings"
UA = {"User-Agent": "Bayyinah-builder/1.0 (+https://github.com/hachimkf/Bayyinah)"}


def get(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return json.load(r)
        except Exception as e:
            if attempt == 3: raise
            time.sleep(2 * (attempt + 1))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    reads = {r["id"]: r for r in get(f"{API}/reads")}
    for rid, (ar, en) in RECITERS.items():
        folder = reads[rid]["folder_url"]
        surahs = {}
        for s in range(1, 115):
            rows = get(f"{API}?surah={s}&read={rid}")
            surahs[str(s)] = [[int(x["start_time"]), int(x["end_time"])] for x in sorted(rows, key=lambda x: x["ayah"]) if x["ayah"] > 0]
            time.sleep(0.15)
        (OUT / f"{rid}.json").write_text(json.dumps({"read": rid, "name_ar": ar, "name_en": en, "folder": folder, "surahs": surahs}, ensure_ascii=False, separators=(",", ":")))
        print(f"{en}: {sum(len(v) for v in surahs.values())} ayat", flush=True)


if __name__ == "__main__":
    main()

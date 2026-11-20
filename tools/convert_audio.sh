#!/usr/bin/env bash
# Convert per-verse recitation files to the app's format: Opus, mono, low bitrate.
#
#   tools/convert_audio.sh <input_dir> <output_dir> [kbps]
#
# Input files must be named SSSAAA.<ext> (surah + ayah, 3 digits each, e.g. 018010.mp3) —
# the EveryAyah naming. Any format ffmpeg reads works (mp3, wav, m4a, flac, ogg…).
# IMPORTANT: numbers must follow the WARSH (Madani) count used by the app. Some sites number
# Warsh recitations with the Hafs count; check a few surahs (e.g. Ta-Ha) before converting all.
#
# kbps: 16 (default, ~7 MB per hour — smallest that stays clear for tajweed), 12 (~5 MB/h,
# noticeably duller), 20–24 (~9–11 MB/h, closer to the original).
set -euo pipefail
IN="${1:?input dir}"; OUT="${2:?output dir}"; KBPS="${3:-16}"
command -v ffmpeg >/dev/null || { echo "Install ffmpeg first (brew install ffmpeg)" >&2; exit 1; }
mkdir -p "$OUT"
n=0
for f in "$IN"/*; do
  base=$(basename "$f"); name="${base%.*}"
  [[ "$name" =~ ^[0-9]{6}$ ]] || continue
  # voip mode + 48 kHz mono: Opus' speech tuning; -vbr on keeps quiet passages tiny.
  ffmpeg -nostdin -hide_banner -loglevel error -y -i "$f" \
    -ac 1 -ar 48000 -c:a libopus -b:a "${KBPS}k" -vbr on -application voip -map_metadata -1 \
    "$OUT/$name.opus"
  n=$((n + 1))
done
echo "Converted $n files → $OUT ($(du -sh "$OUT" | cut -f1))"

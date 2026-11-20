#!/usr/bin/env bash
# Copy converted .opus files to the phone over USB (USB debugging on). Works with release builds.
#   tools/push_audio.sh <dir_with_opus_files>
set -euo pipefail
DIR="${1:?dir}"
DEST=/sdcard/Android/data/com.bayyinah.app/files/audio
adb shell mkdir -p "$DEST"
adb push "$DIR"/. "$DEST/"
echo "Done. Open a verse in Bayyinah: it plays as soon as its file is there."

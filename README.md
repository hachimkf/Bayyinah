# Bayyinah · بيّنة

Offline Android app for a beginner reciting Quranic verses from a physical book:
**frame the verse → scan → match it against the full Warsh Mushaf → listen and repeat**,
or pick any surah / ayah from the **index** or **search**.

Kotlin · Jetpack Compose · Material 3 Expressive (`material3:1.5.0`) · CameraX · Media3 ExoPlayer ·
Tesseract (on-device Arabic OCR). Works fully offline; internet is used only to check for app updates.

## Install on the phone

Download the APK from the latest **[GitHub Release](https://github.com/hachimkf/Bayyinah/releases/latest)**:

| Phone | File |
|---|---|
| Almost every phone since ~2017 (64-bit) | `Bayyinah-<version>-arm64-v8a-release.apk` (~7 MB) |
| Older / Android Go 32-bit phones | `Bayyinah-<version>-armeabi-v7a-release.apk` |

Open it on the phone (allow "Install unknown apps" for the browser/file manager), or:

```bash
adb install -r Bayyinah-1.1.0-arm64-v8a-release.apk
```

> If a test build from before 1.1.0 is installed, uninstall it once first. It was signed with a
> different (debug) key, so Android won't accept 1.1.0 as an update over it.

## In-app updates

On launch (at most every 12 h), or when she taps the **بيّنة · Bayyinah** logo, the app checks
`releases/latest` of this repo. If a newer version exists, a green banner offers **تحديث · Mettre à jour**.
The app downloads the APK for her phone's CPU, checks its SHA-256 against GitHub's digest, then opens
Android's installer. The first time, Android asks to allow installs from Bayyinah. Without internet, the
check silently does nothing: everything else works offline.

### Publishing an update

1. Bump `appVersionCode` and `appVersionName` at the top of `app/build.gradle.kts`.
2. Commit and push, then run:
   ```bash
   ./tools/release.sh "What changed"
   ```
   This runs the tests, builds both signed APKs, tags `v<version>`, and creates the GitHub release.

### Signing key — back it up

Updates only install over an app signed with the **same key**. The key is in `keystore/bayyinah-release.jks`
and its passwords are in `keystore.properties`. Both are git-ignored and exist only on this Mac.
**Copy both to a safe place** (password manager or encrypted drive). If they're lost, installed phones can't
receive updates and the app must be uninstalled and reinstalled.

## What's inside

```
app/src/main/
├─ assets/quran_warsh.db          full Warsh text: 6,214 ayat (Madani count) + 114 surahs
├─ assets/tessdata/ara.traineddata Tesseract Arabic model (tessdata_fast)
├─ assets/audio/                  bundled recitations (empty – see "Audio" below)
├─ res/font/warsh_uthmanic.ttf    KFGQPC Warsh Uthmanic Script (required to draw the Warsh text)
└─ java/com/bayyinah/app/
   ├─ data/   DatabaseHelper, VerseRepository (surahs, verses, prev/next, search)
   ├─ match/  ArabicNormalizer (tashkeel, alef forms, rasm skeleton), VerseMatcher, MatchDecider
   ├─ ocr/    OcrAnalyzer (CameraX, idle until a scan), TextImagePrep, ArabicTextRecognizer
   ├─ audio/  RecitationPlayer (ExoPlayer: loop-one, 0.75× / 1.0×, missing-file handling)
   └─ ui/     BayyinahApp, ReaderScreen (camera), IndexScreen (index + search), VerseSheet, theme
tools/build_quran_db.py           rebuilds quran_warsh.db from tools/source/warsh.json or your own TSV
```

### Text source

The Warsh text and font come from the King Fahd Glorious Qur'an Printing Complex (KFGQPC),
via the open dataset [quran-ws/quran-text](https://github.com/quran-ws/quran-text) (CC BY 4.0; KFGQPC
terms apply to the text). The font file's SHA-256 matches the dataset's provenance record. Verse numbers
follow the Madani count used in Warsh Mushafs, so some differ from Hafs: for example, «وقل رب زدني علما» is Ta-Ha **113**.

## How recognition works (and why it failed before)

I tested the OCR by rendering 120 verses (KFGQPC Warsh font + a Naskh font), degrading them like phone
photos (scale, blur, tilt, shadows, JPEG), and running the app's real Tesseract model on them. Two causes
explained the failures:

1. **Tashkeel.** Tesseract usually returns *nothing* for fully vowelled Quranic print. The marks break its
   line finding. `TextImagePrep` now removes small ink blobs (harakat, shadda, sukun, waqf signs) before OCR.
2. **Size.** Tesseract only reads lines inside a narrow height range. Each crop is now rescaled to 3
   calibrated line heights, with one OCR pass per height.

Some letter dots are removed along with the marks, so matching compares **letter shapes** (the rasm:
ب/ت/ث/ن/ي, ج/ح/خ, ف/ق … merged) against all 6,214 verses. `MatchDecider` then combines the passes.

| Per single camera frame (degraded test images) | Before | Now |
|---|---|---|
| Verses identified | ~0–5% | **118 / 120** |
| Wrong verse | — | 1 (near-identical wording, 85:19 vs 84:22) |
| Non-Quran Arabic text falsely matched | — | **0 / 10** |
| Maghribi-script page crops (Nuh 71:1–4) | — | **8 / 11** (1 wrong) |

On the emulator, using the app's own on-device pipeline, 12 of 12 test verses were recognised in 0.3–1.5 s.
Each tap tries up to 3 frames. Some verses share identical wording (26:1 = 28:1, 69:40 = 81:19 …).
In those cases the app picks the one nearest the verse she was just reading.

**Maghribi script:** on a Maghribi-script Warsh page (Nuh 71:1–4), **8 of 11 crops** are recognised,
including every full verse line except one that straddles two ayat. One half-line crop gave a wrong verse.
These are on-screen renders and one page image, not photos of her actual book. If scanning struggles with
her book, the index and search are always there. `OcrRegressionTest` makes it easy to measure any change.

## Index & search

- **الفهرس · Sommaire** lists all 114 surahs. Tap one to see its ayat, then tap an ayah to open the player.
- **بحث · Recherche** finds:
  - words without tashkeel, e.g. `رب العلمين` (any alef / ya / ta-marbuta form works)
  - surah names in Arabic or transliteration, e.g. `الكهف` or `kahf`
  - references, e.g. `18:10`, `18 10` or `18`

## Audio

Use one file per verse, named like the `audio_file` column: `SSSAAA.m4a` (e.g. `018010.m4a`), in **Warsh
numbering**.

- Bundle files in `app/src/main/assets/audio/`, **or** side-load them to keep the APK small (files there
  take priority):
  ```bash
  adb shell run-as com.bayyinah.app mkdir -p files/audio
  ```
  ```bash
  for f in audio/*.m4a; do adb push "$f" /data/local/tmp/ && adb shell run-as com.bayyinah.app cp /data/local/tmp/$(basename $f) files/audio/; done
  ```
- Use mono, 24–32 kbps. `.m4a` (AAC) plays on every Android version. `.opus` only plays on Android 10+.
  ```bash
  ffmpeg -i in.mp3 -ac 1 -c:a aac -b:a 32k 018010.m4a
  ```
- If a verse's file is missing, the sheet shows "Audio indisponible" and disables Play.

## Build

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:assembleRelease
```

The build needs JDK 17+ and Android SDK Platform **37.1** (Compose BOM 2026.11 / material3 1.5.0 require it).

**Size:** the APK is split per CPU type, and native libraries and the database are stored compressed.
R8 shrinks the code. That took it from 25 MB to about 7 MB, including the full Quran and the font.

Debug builds can OCR an image file instead of the camera, to test recognition without a book:

```bash
adb shell am start -n com.bayyinah.app/.MainActivity --es ocr_test /data/data/com.bayyinah.app/files/ocrtest/verse.png
```

```bash
adb logcat -s BayyinahOcr
```

Every time you ship a new `quran_warsh.db`, bump `ASSET_DB_VERSION` in `DatabaseHelper.kt`.

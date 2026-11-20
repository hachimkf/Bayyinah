# Bayyinah · بيّنة

A light, offline Android app for a learner reciting the Quran in **Warsh 'an Nafi'**:
**choose a surah, type the ayah number, listen and repeat.**

Kotlin · Jetpack Compose · Material 3 Expressive (`material3:1.5.0`) · Media3 ExoPlayer.
APK ≈ **2.8 MB**. Works fully offline; internet is only used to check for app updates.

## Using it

- **اذهب إلى آية · Quick jump** (top card):
  1. Tap the surah field and pick from the list (filter by number `67` or name `ملك` / `mulk`).
  2. Type the ayah number on the number pad.
  3. Tap **اقرأ واسمع · Lire**. An out-of-range number shows "choisir 1–31" instead of opening anything.
- **متابعة · Reprendre** reopens the last verse she listened to.
- **Search** finds words without tashkeel (`رب العلمين`), surah names (`الكهف`, `kahf`), or references
  (`18:10`, `18 10`).
- **Surah page:** tap a surah to see all its ayat. The "go to ayah" field there scrolls to the ayah and plays it.
- **Player:** Play/Pause, Repeat (loops the verse), Previous/Next, Slow 0.75× / Normal.

## Install

Download `Bayyinah-<version>-universal-release.apk` from the latest
**[GitHub Release](https://github.com/hachimkf/Bayyinah/releases/latest)** and open it on the phone. One APK
works on every phone (Android 7+).

## Audio (recitation)

### Format: Opus, mono, 16 kbps

Measured on a 3½-minute Arabic speech sample, extrapolated to a ~28 h murattal recitation of the whole Quran:

| Format | Size per hour | Whole Quran (~28 h) |
|---|---|---|
| **Opus 12 kbps** | 5.4 MB | ~150 MB (noticeably duller) |
| **Opus 16 kbps (recommended)** | 7.4 MB | **~206 MB** |
| Opus 20 kbps | 9.0 MB | ~252 MB |
| Opus 24 kbps | 10.7 MB | ~299 MB |
| HE-AAC 24 kbps (.m4a) | 10.9 MB | ~304 MB |
| MP3 32 kbps | 13.7 MB | ~385 MB |
| MP3 64 kbps (typical download) | 27.5 MB | ~769 MB |

Opus is the smallest format that still sounds clear for tajweed. ExoPlayer plays it on every Android
version the app supports. A short verse at 16 kbps is about 10–20 KB. Listen to the samples in
`build/audio-samples/` (made locally, not committed) before choosing.

### Getting files into the app

1. Name one file per verse `SSSAAA` (surah and ayah, 3 digits each, e.g. `018010.mp3`). This is the EveryAyah
   naming, and it must use **Warsh numbering**: some sites number Warsh audio with the Hafs count, so check a
   few verses in Ta-Ha first.
2. Convert:
   ```bash
   tools/convert_audio.sh ~/Downloads/recitation ~/Downloads/recitation-opus 16
   ```
3. Copy to the phone (USB debugging on). This works with the normal release app:
   ```bash
   tools/push_audio.sh ~/Downloads/recitation-opus
   ```

The app looks for `SSSAAA.opus`, then `.ogg`, `.m4a`, `.mp3`, in `Android/data/com.bayyinah.app/files/audio/`,
app-private storage, or `assets/audio/` (bundled). A verse without a file shows "Audio indisponible".

## Text source

The Warsh text (6,214 ayat, Madani count) and the KFGQPC Warsh Uthmanic font come from the King Fahd Glorious
Qur'an Printing Complex, via [quran-ws/quran-text](https://github.com/quran-ws/quran-text) (CC BY 4.0; KFGQPC
terms apply to the text). Ayah numbers follow the Warsh count: for example, «وقل رب زدني علما» is Ta-Ha **113**.
To rebuild the database:

```bash
python3 tools/build_quran_db.py tools/source/warsh.json app/src/main/assets/quran_warsh.db
```

Bump `ASSET_DB_VERSION` in `DatabaseHelper.kt` whenever the database changes.

## In-app updates

On launch (at most every 12 h), or when she taps the **بيّنة** logo, the app checks this repo's latest
release. If there's a newer version, a banner offers **تحديث · Mettre à jour**. The app downloads the APK,
checks its SHA-256 against GitHub, then opens Android's installer. The first time, Android asks to allow
installs from Bayyinah.

### Publishing an update

1. Bump `appVersionCode` and `appVersionName` at the top of `app/build.gradle.kts`.
2. Commit, push, and run:
   ```bash
   ./tools/release.sh "What changed"
   ```

### Signing key — back it up

Updates only install over an app signed with the **same key**. The key is in `keystore/bayyinah-release.jks`
and its passwords are in `keystore.properties`. Both are git-ignored and exist only on this Mac. Copy them
somewhere safe: if they're lost, installed phones can't be updated.

## Build

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The build needs JDK 17+ and Android SDK Platform 37.1.

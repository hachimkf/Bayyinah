# Bayyinah · بيّنة

A light Android app for a learner reciting the Quran in **Warsh 'an Nafi'**:
**choose a surah and type the ayah number — or scan the verse in her book — then listen and repeat.**

Kotlin · Jetpack Compose · Material 3 Expressive (`material3:1.5.0`) · Media3 ExoPlayer.
APK ≈ **7.5 MB** (scanner OCR included). Text, search and navigation work offline; recitation streams ayah by ayah (internet needed).

## Using it

- **Home is the scan:** a Mushaf page fills the screen; a smooth white fade leaves only the framed surah (Al-Falaq) clear, and a scan line sweeps. The real scanned page is shown only if the file `mushaf_home.jpg` is in the app's files on the phone (it is never put in the APK or in this repo, the Mushaf scans being the Foundation's content); otherwise a drawn page is shown. One huge «امسح الآية» button opens the camera (same frame, back and torch buttons).
- **Top circles (no text):** the app logo opens the whole Mushaf (index: quick jump, search, surah list; the heart there opens Saved), and the gear opens Settings.
- The screen stays on only while an ayah plays or the player is open. Scanning never starts an ayah by itself when idle: scanning pauses after 40 s without a match («توقف المسح», tap the frame to continue), and an OCR guess must be seen in two frames before it plays (book / Mushaf-pack line matches are trusted at once).
- The interface is **Arabic only**, laid out right-to-left, with Latin digits (1, 2, 3) as used in Morocco.
- **اذهب إلى آية** (quick jump, top card):
  1. Tap the surah field and pick from the list (filter by number `67` or name `ملك` / `mulk`).
  2. Type the ayah number on the number pad.
  3. Tap **اقرأ واسمع**. An out-of-range number shows «رقم غير صحيح» instead of opening anything.
- **امسح من الكتاب** (next to اقرأ واسمع) opens the camera. Frame the verse in the box: scanning is
  **automatic** (no button). When the ayah is recognised, the camera closes and the ayah plays in a
  **full-screen player**. «امسح آية أخرى» or Back returns to the camera. On-device OCR (Tesseract) identifies the ayah among all 6,214 and opens it in the player. The camera
  permission is only asked the first time. Measured: 118/120 degraded test verses, 8/11 crops of a
  Maghribi-script page, 0 false matches on non-Quran text (`OcrRegressionTest`).
- **⚙ الإعدادات:** app version, **Vérifier** (check for updates now), automatic update check on/off,
  and reciter.
- **♥ المحفوظات** (heart in the index): ayat she marked with the heart in the player, and the last 30 she listened to.
- **السور** list is folded by default; tap it to show the 114 surahs.
- On the home screen, Back once shows «اسحب مرة أخرى للخروج»; a second Back within 2 s closes the app.
- **Search** finds words without tashkeel (`رب العلمين`), surah names (`الكهف`, `kahf`), or references
  (`18:10`, `18 10`).
- **Surah page:** tap a surah to see all its ayat. The "go to ayah" field there scrolls to the ayah and plays it.
- **Player:** Play/Pause, Repeat ∞ (loops the verse), Previous/Next, Slow 0.75× / Normal, heart. The screen behind it is blurred and dimmed; tap it to close.

## Install

Download from the latest **[GitHub Release](https://github.com/hachimkf/Bayyinah/releases/latest)**:
`…-arm64-v8a-release.apk` for almost every phone, or `…-armeabi-v7a-release.apk` for older 32-bit phones.
Android 7+.

## Scanning her book (no OCR needed)

The scanner first compares the camera image with **the book's own printed lines** before trying OCR. This
uses `book/BookMatcher.kt` and `assets/book_refs.bin` (~230 KB in the APK). How it works:

1. `tools/book/build_book_refs.py` renders the official PDF's Quran pages. It finds each «أسمع وأرتل» verse
   box (24 boxes, ~75 ayat, Maghribi script, Warsh numbering) and the gold medallions that end each ayah,
   then splits every box into printed lines. Each line is stored as a 20-px-tall "ink strip" plus which ayah
   covers which part of the line.
2. On the phone, the camera crop goes through the **same** steps: ink, then straightening, then line spacing,
   then strips. Each strip is compared with all 143 book lines (normalized correlation, coarse-to-fine).
   Two consecutive lines must agree, the winner must clearly beat the runner-up, and the ink density must be
   plausible.
3. If the book match isn't confident, the OCR scanner takes over.

Results on 60 synthetic camera shots of the book's Quran lines and 40 of its other text
(`tools/book/gen_testcrops.py`, `BookMatcherTest`): **41 recognised from a single frame, 0 wrong, 0 false
matches**. It takes 0.15–0.45 s per frame on the emulator, and the scanner tries up to 3 frames.

**Rebuilding** (the PDFs stay local in `book-source/`, which is git-ignored):

```bash
python3 tools/book/build_book_refs.py book-source
```

```bash
python3 tools/book/gen_testcrops.py book-source
```

The PDFs come from the Ministry's Level 2 page, archived by the Wayback Machine. habous.gov.ma is in
maintenance mode as of Nov 2026. If her printed copy is a different edition and recognition is poor, photos
of its Quran pages can replace the PDF renders.

## Audio (recitation) — streamed, nothing stored

Each ayah plays online as a short clip of a whole-surah Warsh recording from
[mp3quran.net](https://mp3quran.net), using its published per-ayah timings (Warsh numbering). The timings
ship inside the app (`timings` table, ~170 KB). Only the audio itself comes from the network.

| Reciter | Boundaries inside the reciter's pause* |
|---|---|
| Omar Al-Qazabri · عمر القزابري (default, Moroccan) | 91% |
| Mahmoud Khalil Al-Husary · محمود خليل الحصري (slow, very clear) | 98% |
| El Ayoun El Kouchi · العيون الكوشي (Moroccan) | 80% |
| Yassin Al-Jazairi · ياسين الجزائري | 90% |

\* Measured on 12 surahs by detecting the pauses in each recording. The rest are within about a second.
Muhammad Saayed was left out because his timings use Hafs numbering.

- **How it streams:** the recordings are constant-bitrate MP3s, so ExoPlayer jumps straight to the ayah's byte
  offset with an HTTP range request. Playback starts after about 0.4 s of buffer, which is roughly 0.25 s of
  network time from Morocco/Europe on the Cloudflare CDN.
- **Data use:** about 1 MB per minute (128 kbps). Nothing is cached on the phone.
- **Offline:** text, search and navigation work. Audio shows «تعذّر تشغيل التلاوة، تحقق من الإنترنت» with
  a Retry button.
- **She chooses the reciter** from the small circle beside the heart in the player (tap: it shows the name; tap again: the list). The choice is remembered.

To refresh or add reciters with timings (`read` ids listed at `https://mp3quran.net/api/v3/ayat_timing/reads`):

```bash
python3 tools/fetch_timings.py
```

```bash
python3 tools/build_quran_db.py tools/source/warsh.json app/src/main/assets/quran_warsh.db
```

Recordings are © their reciters/producers. The app streams them from their original host and doesn't
redistribute them.

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

On launch (at most every 12 h, can be switched off in الإعدادات), or from
**الإعدادات → ابحث عن تحديث**, the app checks this repo's latest release. If there's a newer version, a banner offers **تحديث**. The app downloads the APK,
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

# Bayyinah · بيّنة

A light, offline Android app for a learner reciting the Quran in **Warsh 'an Nafi'**:
**choose a surah, type the ayah number, listen and repeat.**

Kotlin · Jetpack Compose · Material 3 Expressive (`material3:1.5.0`) · Media3 ExoPlayer.
APK ≈ **3 MB**. Text, search and navigation work offline; recitation streams ayah by ayah (internet needed).

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
- **Offline:** text, search and navigation work. Audio shows «تعذّر تشغيل التلاوة · Vérifiez la connexion» with
  a Retry button.
- **She chooses the reciter** from the chip in the player. The choice is remembered.

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

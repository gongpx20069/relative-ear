# Relative Ear

**English** | [简体中文](README.zh-CN.md)

[![Android checks](https://github.com/gongpx20069/relative-ear/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/gongpx20069/relative-ear/actions/workflows/ci.yml)

**Hear Do / Re / Mi, and sing in tune.** An Android app for music enthusiasts with fixed-note ear training, singing practice, live single-note detection, and a standalone mini piano. No interval-theory vocabulary is needed to get started.

Android **8.0+**. The app supports **English and Simplified Chinese**, following the system by default. Chinese button names are also included below. No account is required. Practice and audio processing work offline.

> **0.0.10 early preview.** Real-phone microphone accuracy, latency, and manufacturer compatibility still need validation. This is not a professional musical-ability assessment or reliable song transcription tool.

## Install

Download an APK from [GitHub Releases](https://github.com/gongpx20069/relative-ear/releases), not a source-code archive.

**0.0.10:** [ARM64 APK](https://github.com/gongpx20069/relative-ear/releases/download/v0.0.10/relative-ear-0.0.10-arm64-v8a.apk) · [Universal APK](https://github.com/gongpx20069/relative-ear/releases/download/v0.0.10/relative-ear-0.0.10-universal.apk) · [All downloads and checksums](https://github.com/gongpx20069/relative-ear/releases/tag/v0.0.10)

| APK suffix | Choose it for |
|---|---|
| `arm64-v8a` | Most modern phones running 64-bit Android |
| `armeabi-v7a` | Older phones or 32-bit Android |
| `x86_64` | 64-bit Intel devices or matching emulators |
| `x86` | 32-bit Intel devices or matching emulators |
| `universal` | Unsure of your device architecture; includes all supported ABIs |

Each APK is independently installable: download **one**, not every architecture. Choose the architecture supported by your Android OS, not just the CPU. `SHA256SUMS.txt` contains checksums for all five APKs.

1. Download the APK to your phone and open it.
2. If prompted, allow your browser or file manager to install unknown apps.
3. Open Relative Ear. It starts on **Ear training (练耳)**. Only singing and live detection request microphone permission.

Install new official releases over the existing app to keep your history and settings. Official versions share one signing key. Debug builds may not replace official builds; uninstalling deletes local data.

## Ear training

English navigation labels are **Train / Sing / Detect / Piano / History / Settings**.

1. Open **练耳**. The default range is **C4 / D4 / E4 = Do / Re / Mi**.
2. Tap **听唱名示范** to hear the selected notes with synchronized labels. For free note previews, use the separate **钢琴** tab.
3. Tap **开始练习**, listen to a short note, and choose your answer. The pre-answer illustration does not reveal the pitch.
4. Check the feedback, optionally tap **听正确答案**, then **下一题**. Each round has 10 questions. You can replay the question; only the first submitted answer counts.
5. Under **本轮练习 → 调整**, choose three, five, eight, or a custom selection of C4–C5 natural notes. Choose solfege (**唱名 Do / Re**) or pitch-name (**音名 C4 / D4**) answers. Configuration is locked during a round.

The mapping is fixed, not movable Do:

`C4=Do · D4=Re · E4=Mi · F4=Fa · G4=Sol · A4=La · B4=Si · C5=Do↑`

Do↑ means the higher-octave Do. Default tuning is A4=440 Hz; C4 is approximately 261.63 Hz. Start with three notes at 80 BPM, then expand the range.

Playback uses eighth-note slots at **60 / 80 / 100 / 120 BPM**. At 80 BPM, each slot is 375 ms including a short silent articulation gap. Tempo affects playback only, not your answer deadline or rhythm score. There is no separate exam mode yet.

## Standalone piano and fullscreen

1. Open **Piano (钢琴)** to access eight white keys from C4 to C5.
2. Tap a key to hear a short synthesized note. Quickly tapping another key switches the preview. On narrow screens, scroll the keyboard horizontally.
3. Tap **Expand fullscreen (展开全屏)** for a landscape keyboard without the app navigation or system bars.
4. Tap **Exit fullscreen (退出全屏)** or use Android Back to return to the piano page and restore the previous orientation setting.

Use **音色** to choose **Piano (钢琴音色)**, **Flute (笛子音色)**, or **Pure tone (纯音)** in either layout. Your choice is saved and applies to the next key press, not the note already playing. Piano uses decaying harmonics; flute has a softer attack and a sustained tone. These are offline synthesized approximations, not recorded instrument samples. Ear-training demonstrations and detected-note replay keep their existing pure tone.

Rapid taps replace the current note, not queue every intermediate key. Playback waits for the previous player's resources to be released, so overlapping taps are not themselves an audio error. Genuine device or audio-focus failures are still reported.

The piano is **not embedded in ear training, singing, or detection pages**. It does not need the microphone, change the assessment range, or save a practice attempt. Rotation or backgrounding may stop the current preview; tap a key to play again. It is a note-learning keyboard, not a sustained-note or multitouch chord instrument.

## Singing practice

1. Open **唱唱名**, choose your note range, and listen to the demonstration.
2. Tap **开始回唱**. Listen to C4 first, then sing the displayed target when prompted.
3. Hold a stable pitch for at least about half a second. The app shows the detected note and pitch error; positive cents mean sharp, negative cents mean flat.
4. Review the result, hear the correct note if needed, and continue the 10-question round.

The default tolerance is **±35 cents**; settings also offer ±20 or ±50. New users must match the octave by default. **回唱忽略八度** permits an octave above or below when the training range does not fit your voice; this does not merge C4 and C5 in listening questions.

Use wired headphones when possible. Otherwise, wait until playback ends before singing so the microphone does not pick up the app's own sound. No stable note within 8 seconds counts as a timeout.

## Live detection and synthesized replay

1. Open **识音** and tap **开始监听**.
2. Hum, or play a single note near the microphone.
3. Watch the detected pitch, frequency, pitch deviation, and recent **60-second note timeline**.
4. Tap **停止**. The final sustained note is added to the recognized sequence.
5. Tap **回放识别音符** to replay the detected pitches as synthesized tones. A red cursor moves with actual audio output. Note lengths and pauses, including leading and trailing silence, are preserved.
6. Tap **停止回放** to stop. Replaying again starts at the beginning of the retained clip.

The text list shows the latest 30 notes; the timeline and replay retain the latest **60 seconds**. Listening itself can continue longer. Replay preserves recognized octaves and chromatic notes rather than forcing everything into C4–C5.

**Replay is not an original recording and does not speak the words “Do / Re / Mi.”** It synthesizes tones from detected note events. Analysis and segmentation introduce some delay; incorrectly detected notes will also replay incorrectly.

Leave short pauses between repeated notes, or they may merge into one sustained event. The temporary clip is cleared when you start a new capture, leave the detection page, or close the app. Capture and replay cannot run simultaneously. Backgrounding or losing audio focus stops playback without automatically resuming.

Detection is intended for a single voice or instrument melody. It does **not** identify songs or reliably transcribe chords, accompaniment, or full recordings.

## History and settings

- **记录** shows **one card per practice**, with time, answered questions, accuracy, and the configuration. Tap a card for chronological per-note answers, timeouts, response times, replays, and singing errors.
- The list shows the latest 100 practices; overall accuracy is weighted by answered questions across all saved practices. Different ranges and legacy modes are not directly comparable difficulty levels.
- Leaving a round early preserves its answered portion as a partial practice. Unanswered questions and piano previews do not create scores. Existing records remain available after upgrades.
- **设置** controls A4 tuning (415–466 Hz), singing tolerance, and octave matching. **删除全部训练记录** deletes scores after confirmation but keeps settings.

## App language

Open **Settings → Language / 语言** and choose **Follow system**, **简体中文**, or **English**. Follow system is the default: Chinese systems use Simplified Chinese; unsupported languages fall back to English. An explicit choice overrides the system language and is saved across restarts. Select Follow system again to remove the override.

On Android 13+, the same choice integrates with Android app-language settings. Older supported versions save it through AndroidX AppCompat. Switching language refreshes the interface and stops current audio without deleting history, tuning preferences, or the piano voice.

## Updates

Open **设置 → 版本与更新 → 检查更新**. Published `0.0.x` previews are included. When an update is available, tap **前往下载**; the app chooses your supported ABI, with universal as a fallback.

A browser or another external app handles the download. Install the APK manually over your existing official version. The app does not automatically check, download, or install updates. Network errors, rate limits, and incomplete release assets are reported explicitly. Versions `0.0.4` and earlier need a manual upgrade before this update entry is available.

## Privacy, troubleshooting, and project checks

Raw microphone audio is processed locally, not saved or uploaded. Scores and settings stay on your phone, with system cloud backup disabled. Synthesized replay is generated in memory. Only a manual update check contacts GitHub; it sends no recordings or scores. See [Privacy](docs/PRIVACY.md).

If no stable pitch appears, check microphone permission, stop other recording apps, reduce background noise, and try a clear sustained note. The detector covers roughly **65–1000 Hz**. For octave errors, try changing microphone distance or reducing accompaniment. If permission was permanently denied, enable it in Android's app settings.

The [main-branch check badge](https://github.com/gongpx20069/relative-ear/actions/workflows/ci.yml) shows current checks; historical failed runs remain visible. CI runs the complete instrumentation suite **once**, collecting screenshots and a JUnit report from the same session. Failures, skips, empty reports, and mismatched results do not pass. Emulator checks do not replace real-phone audio validation.

## Documentation

[Chinese user guide](README.zh-CN.md) · [Document index](agent.md) · [Design](docs/DESIGN.md) · [Development](docs/DEVELOPMENT.md) · [Testing](docs/TESTING.md) · [Release process](docs/RELEASE.md)

Engineering documents are currently in Chinese. For local builds and signing, follow the development and release guides rather than installing a debug APK over an official release.

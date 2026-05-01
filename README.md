# birdscope

Android app for recording and observing bird songs.

**Phase 1 (MVP):** record audio from default mic to WAV. No analysis yet.

## Build

CI-only via GitHub Actions. Every push builds an APK; pushes to `main` publish a release tagged `build-N` (where N = CI run number) with the APK attached.

## Install

1. First time: download the latest APK from the [Releases page](https://github.com/iamweasel89/birdscope/releases/latest), allow install from unknown sources, install.
2. After that: tap **Check for update** in the app.

## Phases

- [x] Phase 1: record + save WAV
- [ ] Phase 2: live spectrogram, dominant frequency
- [ ] Phase 3: sound event detection, SNR
- [ ] Phase 4: BirdNET ID + GPS

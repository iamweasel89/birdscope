# birdscope

Android app for recording and observing bird songs.

## For LLMs opening this repo

State of the project lives in `memory/`. Start at `memory/root.json` and
descend by `purpose` fields on each link — read only what the task needs.
Format spec: `project-memory.md` in the `protocols` repo.

## Status

Restart with project-memory approach. Previous work preserved on branch
`legacy`: pull from there as needed.

Current build: minimal app — start screen + Check for update button.
No recording, no analysis yet. Self-updates via Updater.kt.

## Build

CI-only via GitHub Actions. Every push to `main` builds an APK and publishes
a release tagged `build-N` with the APK attached.

## Install

1. First time: download APK from the latest release, install (allow unknown sources).
2. After that: tap **Check for update** in the app.
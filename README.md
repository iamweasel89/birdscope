# birdscope

Android app for recording and observing bird songs.

## For LLMs opening this repo

State of the project lives in `memory/`. Start at `memory/root.json` and
descend by `purpose` fields on each link — read only what the task needs.
For a quick visual overview of the whole tree, see `memory/overview.md`.

External protocols this project follows ([protocols repo](https://github.com/iamweasel89/protocols)):

- **`project-memory.md`** — format of the `memory/` folder (read first).
- **`dates-discipline.md`** — honesty rules for any date written into a file.
- **`mobile-app-android.md`** — build pipeline, signing, in-app updater pattern.

Note: the README does **not** describe the current feature set. The feature
set lives in `memory/`, where it stays current. Do not infer from this
README what the app does today — read `memory/root.json` and descend.

## History

Restart with project-memory approach. Previous work preserved on branch
`legacy`: pull from there as needed.

## Build

CI-only via GitHub Actions. Every push to `main` builds an APK and publishes
a release tagged `build-N` with the APK attached.

## Install

1. First time: download APK from the latest release, install (allow unknown sources).
2. After that: tap **Check for update** in the app.
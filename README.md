# birdscope

Android app for recording and observing bird songs.

## For LLMs opening this repo

State of the project lives in `memory/`. Start at `memory/root.json` and
descend by `purpose` fields on each link — read only what the task needs.

**Do not use `memory/overview.md` as your entry point.** It is a
hand-maintained snapshot for human operators and contains less detail than
the leaves. Walk the JSON branches and read leaf markdown directly.

**When delivering changes — file edits, command sequences, commits —
use the launch-block format from `launch-block.md` (see protocols below).
Inline diffs and "add this line here" descriptions are not the working
format here. The operator applies changes by pasting blocks into PowerShell,
not by editing files manually from your description.**

### Direct entry URLs

If your fetch tool only follows URLs that appear in prior results,
these are the entry points to seed your fetcher with:

- https://raw.githubusercontent.com/iamweasel89/birdscope/main/memory/root.json
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/memory/overview.md

From `root.json` you will find `ref` values like `n001.json`, `n002.json`, etc.
Read each by replacing the filename in the URL above:
`.../main/memory/n001.json`, `.../main/memory/n002.json`, `.../main/memory/n201.md`,
and so on. Every node file lives flat under `memory/`.

### Source code direct URLs

- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/src/main/kotlin/com/iamweasel89/birdscope/MainActivity.kt
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/src/main/kotlin/com/iamweasel89/birdscope/Updater.kt
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/src/main/kotlin/com/iamweasel89/birdscope/InstallStatusReceiver.kt
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/src/main/kotlin/com/iamweasel89/birdscope/FftAnalyzer.kt
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/src/main/kotlin/com/iamweasel89/birdscope/SpectrumView.kt
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/src/main/kotlin/com/iamweasel89/birdscope/PhasePortraitView.kt
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/src/main/AndroidManifest.xml
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/src/main/res/layout/activity_main.xml
- https://raw.githubusercontent.com/iamweasel89/birdscope/main/app/build.gradle

### Protocols

External protocols this project follows ([protocols repo](https://github.com/iamweasel89/protocols)):

- **`project-memory.md`** — format of the `memory/` folder (read first):
  https://raw.githubusercontent.com/iamweasel89/protocols/main/project-memory.md
- **`dates-discipline.md`** — honesty rules for any date written into a file:
  https://raw.githubusercontent.com/iamweasel89/protocols/main/dates-discipline.md
- **`mobile-app-android.md`** — build pipeline, signing, in-app updater pattern:
  https://raw.githubusercontent.com/iamweasel89/protocols/main/mobile-app-android.md
- **`launch-block.md`** — format for ready-to-run command blocks:
  https://raw.githubusercontent.com/iamweasel89/protocols/main/launch-block.md

Note: this README does **not** describe the current feature set. The feature
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
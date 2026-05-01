# birdscope features

Permanent IDs. Never reused. New features get the next free number, regardless of build order.

| ID | Name | Status | UI? | Notes |
|----|------|--------|-----|-------|
| F1 | Audio recording to WAV | done | yes | Phase 1 MVP. 44.1 kHz / 16-bit / mono. App-internal storage. |
| F2 | Delete all recordings | done | yes | With confirmation dialog. |
| F3 | Update confirmation dialog | done | yes | User must confirm before download/install. |
| F4 | Level meter (dBFS + peak-hold) | done | yes | Numeric dBFS readout + peak-hold marker (secondary progress on level bar). |
| F5 | Dev tag overlay | done | yes | Small grey F-tags above UI elements; toolbar overflow menu toggles them. Default on. |
| F6 | Compact UI (overflow menu) | done | partial | F2 (delete-all) and F3 (update) consolidated into the toolbar overflow menu. |

## Status meanings

- **proposed** — discussed but not started
- **approved** — agreed, queued for build
- **building** — in progress
- **done** — implemented, tested on phone
- **dropped** — decided against

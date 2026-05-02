# Project memory

This folder is the persistent state of the birdscope project across LLM sessions.

## How to read it

Start at `root.json`. It lists branches with a `purpose` field on each.
Read each `purpose` and decide whether to descend. Only open files
whose purpose matches the task at hand.

For a quick visual overview of the whole tree, see `overview.md`.
It is a hand-maintained snapshot — the JSON and markdown files remain
the source of truth.

File kinds:
- **`*.json`** — branch nodes. Metadata + links to other nodes.
- **`*.md`** — leaf nodes. Actual content with YAML frontmatter.

All files are flat in this folder. Hierarchy is meaning-based, not path-based.
IDs are neutral (`n001`, `n800`); semantics live in `name` and `purpose` fields.

## Format spec

Full specification: `project-memory.md` in the [protocols repo](https://github.com/iamweasel89/protocols).

## Conventions used here

- **Purpose formulations:** see `n802.md` for project-local rules.
- **Dates:** any date written into a leaf (`last_verified`, `review_after`,
  free-text dates) follows `dates-discipline.md` in the protocols repo —
  no invented timestamps, checkable source, staleness needs a plan.
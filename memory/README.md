# Project memory

This folder is the persistent state of the birdscope project across LLM sessions.

## How to read it

Start at `root.json`. It lists branches with a `purpose` field on each.
Read each `purpose` and decide whether to descend. Only open files
whose purpose matches the task at hand.

File kinds:
- **`*.json`** — branch nodes. Metadata + links to other nodes.
- **`*.md`** — leaf nodes. Actual content with YAML frontmatter.

All files are flat in this folder. Hierarchy is meaning-based, not path-based.
IDs are neutral (`n001`, `n800`); semantics live in `name` and `purpose` fields.

## Format spec

Full specification: `project-memory.md` in the [protocols repo](https://github.com/iamweasel89/protocols).

## Project-specific conventions

See `n802.md` for this project's discipline of `purpose` formulations.
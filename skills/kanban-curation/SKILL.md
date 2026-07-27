---
name: kanban-curation
description: "Normalize and curate Kanban/spec task files: ensure frontmatter, normalize tokens, and keep process vocabulary consistent."
---

# Skill: Kanban Curation

## Goal
Keep task/spec metadata consistent across teams: frontmatter presence, canonical tokens, and process hygiene.

In `devel`, canonical status/token behavior comes from `packages/kanban` and should be checked via `bin/eta-mu-board fsm show`.

## Use This Skill When
- You see mixed tokens such as `review` vs `in_review`, `doing` vs `in_progress`, or legacy hyphenated spellings.
- You need to batch-normalize task files.
- You need to convert task/spec metadata to what the new `packages/kanban` tool expects.

## Do Not Use This Skill When
- The user only wants a code change unrelated to task management.

## Output
- Normalized frontmatter/token mapping for `packages/kanban` / `bin/eta-mu-board`.
- A small set of recommended edits.

---
name: kanban-fsm
description: Canonical Kanban workflow FSM contract (states, transitions, gates, invariants, normalization). Use to validate or explain status transitions.
---

# Skill: Kanban FSM

## Goal
Provide the single source-of-truth Kanban FSM contract for this workspace.

In `devel`, the canonical implementation lives in `packages/kanban` and is accessed through `bin/eta-mu-board` (chat shorthand: `@bin/eta-mu-board`).

## Use This Skill When
- You need to validate a task status transition.
- You need to normalize legacy status tokens into the canonical `packages/kanban` tokens.
- You need to explain what a state means (`accepted`, `breakdown`, `blocked`, `ready`, `todo`, `in_progress`, `in_review`, `testing`, `document`, `done`, etc.).
- You need to check the live canonical workflow with `bin/eta-mu-board fsm show`.

## Do Not Use This Skill When
- The user is only editing legacy Promethean kanban internals and does not need the workspace-canonical tool.

## Output
- Canonical next states.
- Required gates/invariants.
- Normalized token mapping aligned to `packages/kanban`.
mapping aligned to `packages/kanban`.

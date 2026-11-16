---
name: spec-driven-dev
description: Manage work via specs + phases + verification, and Kanban state transitions. Use for planning/implementing multi-step changes with traceability.
---

# Skill: Spec-Driven Development

## Goal
Turn requests into a small spec, break into phases, execute with verification and traceability.

When the work also involves Kanban state or backlog handling in this workspace, use `eta-mu kanban` for board operations.

## Use This Skill When
- The work is multi-step or risky.
- You need a spec draft, phased plan, or Kanban lifecycle management.
- You need deterministic receipts (what changed, why, how verified).

## Do Not Use This Skill When
- A trivial one-line change with no meaningful risk (unless user asks for a spec).

## Steps
1. Create/refresh a spec draft.
2. Resolve open questions.
3. If Kanban state matters, inspect the board with `eta-mu kanban count` and `eta-mu kanban list`, then use those states/tokens in the spec.
4. Break the work into phases with build/test checkpoints.
5. Execute phases one at a time; verify after each.
6. Record changes + outcomes in the spec.
7. Update task status with `eta-mu kanban frontmatter <uuid> status <new-status>`.

## Output
- A spec document.
- A phased execution plan.
- Verification steps/results.

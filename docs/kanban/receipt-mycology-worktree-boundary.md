---
uuid: "a2381c02-cbe7-41c1-b60e-9705c714ea20"
title: "Keep receipt and reflection writes inside their Git worktree"
status: "incoming"
priority: "P0"
points: 3
labels: receipt-river, session-mycology, isolation, worktrees
---

# Keep receipt and reflection writes inside their Git worktree

## Context

[Issue #20](https://github.com/riatzukiza/.agents/issues/20) records an actual
cross-worktree receipt write during the October 6 Foresight sweep. Both
`receipt-river/scripts/common.bb` and `session-mycology/scripts/common.bb`
recognize a `.git` directory but skip a linked worktree's `.git` file. They
therefore select an ancestor `.ημ` directory.

Observed global helper source: `0e1101c8d76cca92d6a4321f0e880624bf5e0d14`.
Observed misplaced receipt timestamp: `2026-10-06T13:34:50.023938463Z`; SHA-256
excluding its LF: `90706ba68f2138a63e92d3cc92ee72eaa368a21e9913143d4c3a4e5da29adb18`.
There was no pre-call ancestor byte snapshot: an empty prior file is not proved.
The original misplaced bytes and global installation remain unchanged.

## Outcome

A helper recognizes its owning checkout/worktree boundary and writes only to
that root's ledger. A malformed Git boundary fails visibly before a write
instead of falling through to ancestor metadata.

## Scope

- Repair root discovery in both existing helper families with matching behavior.
- Recognize ordinary checkouts and valid linked-worktree Git files from nested
  directories. Use Git's own read-only validation rather than inventing Git
  ownership semantics; unavailable validation remains a visible failure.
- Preserve explicit local `.ημ` bootstrap support outside Git repositories.
- Never cross a recognized invalid repository boundary to find another ledger.
- Add real temporary Git checkout/worktree and CLI writer fixtures, including
  exact ancestor byte snapshots before and after init/append/reflection calls.
- Document boundary behavior and run the bounded fixture suite in CI after
  reviewed implementation; no external service or shared output is required.

## Non-goals

Modify `/home/err/.agents`, the Foresight `.agents` consolidation checkout,
Git history, shared ledgers, historical receipts, services, user configuration,
or board semantics. Do not repair the scalar-vector defect here; issue #19
owns that distinct cause. Do not introduce a global installation step.

## Acceptance criteria

- Both finders select the same owning root for an ordinary checkout, a valid
  linked worktree under ancestor metadata, and a nested execution directory.
- Worktree init and append create/update only its own intended ledger.
- Mycology initialization/logging affect only that worktree's metadata.
- Ancestor receipt/reflection bytes are unchanged in isolated regression fixtures.
- An absent root produces a visible unavailable result; an invalid Git marker
  fails before any write and cannot silently fall back to ancestor metadata.
- Local metadata remains supported where no Git boundary exists.
- Fixtures cannot modify real ancestor metadata, global caches, or shared state.

## Verification

Create a temporary Git repository with a fixture-only empty commit, then add a
real detached worktree beneath a disposable ancestor `.ημ` with sentinel
ledgers. Run actual receipt and mycology helpers from a nested worktree folder;
assert exact roots/output paths and compare ancestor bytes. Also cover ordinary
Git, local metadata, missing markers and invalid Git boundaries. All temporary
repositories and sentinels are removed only from the fixture's owned directory.

## Risks

Git validation must not honor an inherited override that redirects discovery to
another repository. Independent deployment of either helper family must retain
all required root-discovery support. Recognition and schema compatibility are
separate; passing this card alone does not fix issue #19.

## Planning boundary

Hand-authored incoming Markdown input. No operational admission, reviewed-ready
state, implementation claim, or synthetic event ID is asserted. Canonical
PR Flow planning review and lawful Rheos readiness precede helper changes.

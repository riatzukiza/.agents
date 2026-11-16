---
name: receipt-driven-regression-recovery
description: Before rewriting regression code, search receipts.edn for the prior design decision so you don't lose solved work.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: receipt-led-regression-recovery
  project: gates-of-truth
  discoverable-by:
    - opencode
    - eta-mu
    - claude
  version: 1
---

# Skill: Receipt-Driven Regression Recovery

## Goal
Use the project's receipt-river ledger to recover previously-validated designs when a regression looks like new work.

## Use This Skill When
- A regression appears and you are about to re-implement a fix.
- The project maintains an append-only `receipts.edn` ledger.
- The symptom has been addressed before, or the design space is familiar from earlier work.

## Do Not Use This Skill When
- The project has no `receipts.edn` ledger.
- The regression is clearly new (new feature, new dependency, new environment, newly introduced bug).
- The fix is a simple typo or one-liner with no design history to recover.

## Steps
1. Tail `receipts.edn` for entries matching the same symptom, area, or recent decision.
2. Search for `:decision`, `:test-run`, and `:observation` entries related to the regression.
3. If a prior decision exists, compare the current code against the recorded intent.
4. Recover the design from the receipt rather than re-inventing it.
5. Add a regression test that locks the recovered behavior.
6. Append a receipt explaining the recovery and the test.

## Anti-patterns
- Writing replacement code before checking the ledger.
- Treating a lost prior fix as a new bug.
- Recovering a design without adding a test that prevents the same loss.

## Output
- A regression test that captures the prior design.
- An updated implementation matching the recovered intent.
- A receipt documenting the recovery and the test results.

---
name: pr-red-green
description: pr-flow stages red and green for a ready card. First write the laws (Malli schemas, invariants, pure .cljc) and failing tests that encode the acceptance criteria; then write pure domain logic and outer infra adapters that obey the laws and pass every test and gate.
license: GPL-3.0-or-later
metadata:
  tier: provisional
  origin: pr-flow skill graph, 2026-10-01
---

# Red, then green

## Use this skill when

- A card is `ready`, or `todo`/`in_progress` in Rheos, and implementation starts.
- Code review finds a missing law. Go back to red, add the law and test, then fix.

## Do not use this skill when

- The card is not reviewed or ready. Go back to `pr-sprint-planning`.

## Red: laws and tests first

1. Move the card to `in_progress` through Rheos (`todo→in_progress` has a WIP gate).
2. **Write the laws:**
   - shapes as named, registry-keyed Malli schemas (see `clojure-mu`);
   - invariants and pure decision functions in `.cljc`, following the repository's namespace law (`law/`, `shape/`, `domain/`, `extern`/`infra`).
   - Reuse Katamorph schemas where they exist, and never redefine a Katamorph-owned name.
3. **Write failing tests** that encode each acceptance criterion, plus property tests for invariants. Run them and confirm they fail for the right reason: a missing function or wrong result, not a typo.
4. **Commit the red state** with the failure output summarised in the message. This is the evidence that the tests can fail.

## Green: domain, then infra

5. **Write pure domain logic** until the law tests pass. Effects stay out of it.
6. **Write the infra/extern adapters** at the edge: HTTP, files, processes, databases, GitHub. Convert native JS or JVM values at the boundary, and validate both sides of any replaceable boundary.
7. **Run the full repository gate**, then commit and push. Look the gate up in the repository: clj-kondo, typecheck, tests, mutation testing where configured. Zero warnings means warnings are failures.
8. **Hand off** to `pr-review-settlement` with `pr.cljs request REPO N code`.

## Rules

- Never delete, skip or weaken a test to get to green.
- If the runtime ladder matters, choose the lightest adequate runtime (NBB → Babashka → JVM → compiled CLJS). The runtime must not change the laws.
- One coordinator per PR pushes. Parallel implementers work in their own worktrees or files.

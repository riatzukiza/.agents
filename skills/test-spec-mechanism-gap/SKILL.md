---
name: test-spec-mechanism-gap
description: "Resolve a failing spec-driven test by enumerating the secondary interactions and ordering that the primary mechanism needs to produce the emergent outcome."
triggers:
  - "failing test against spec"
  - "spec says X but test fails"
  - "emergent test failure"
  - "test passes the primary mechanism but fails the outcome"
---

# Skill: test-spec-mechanism-gap

## Goal

Turn a spec-driven test that exercises the primary mechanism but still fails into a green test by discovering and adding the missing secondary interactions, processing order, or hysteresis that the emergent outcome depends on.

## Use When

- A test asserts an emergent outcome (e.g., "one dominant star forms", "market clears", "all packets route").
- The primary mechanism described in the spec is implemented.
- The test still fails because other entities interact in a way that blocks the runaway/merge/convergence.
- The user says: "failing test against spec", "spec says X but test fails", or "emergent test failure".

## Do Not Use

- When the spec acceptance criteria themselves are wrong or outdated; escalate the spec instead.
- When the primary mechanism is simply not implemented; this is a missing-feature problem, not a mechanism-gap problem.
- To tune thresholds or magic constants until a broken mechanism passes.
- For tests that fail because of a simple typo or unit-mismatch bug; use regression-triage first.

## Steps

1. Read the spec acceptance criteria and the test assertions side by side.
2. Identify the primary mechanism the test is exercising.
3. List every entity interaction that could prevent the emergent outcome:
   - merging / capture
   - processing order (most-massive-first, nearest-first, etc.)
   - hysteresis or feedback loops
   - resource competition / starvation
4. Check whether the current code allows each of those interactions.
5. Add the smallest interaction fix that enables the outcome.
6. Re-run the test. If it still fails, return to step 3 with the new failure data.
7. Add a regression test that exercises the missing interaction.

## Output

- A concise analysis of which secondary interaction was missing.
- The minimal code change that closes the gap.
- A regression test that fails if the interaction is removed.
- An updated note in the relevant spec or design doc if the interaction was not implied by the spec.

## Anti-patterns

- Tuning test parameters to make a broken mechanism pass.
- Changing the spec acceptance criteria without comparing to intent.
- Fixing only the primary mechanism while ignoring secondary blocking interactions.
- Re-implementing the entire feature because one test is red.

## References

- session-mycology skill
- spec-driven-dev skill
- regression-triage skill

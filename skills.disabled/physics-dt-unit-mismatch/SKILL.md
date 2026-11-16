---
name: physics-dt-unit-mismatch
description: When a simulation feature suddenly stops producing results, audit dt/time reads before assuming a missing feature.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: physics-dt-unit-audit
  project: gates-of-truth
  discoverable-by:
    - opencode
    - eta-mu
    - claude
  version: 1
---

# Skill: Physics dt Unit Mismatch Audit

## Goal
Quickly diagnose unit or time-step mismatches in simulation physics that masquerade as missing features or broken algorithms.

## Use This Skill When
- A simulation feature (accretion, drag, cooling, ignition, etc.) suddenly stops producing expected results.
- The code recently changed around time-stepping or world-state keys.
- Tests pass but the physical outcome is orders of magnitude off.

## Do Not Use This Skill When
- The issue is clearly a compile-time/runtime error or a missing component.
- You have no headless benchmark or unit test to reproduce the outcome.
- The symptom is in a non-physical system (UI, persistence, networking).

## Steps
1. Reproduce the symptom with a minimal headless sim or focused unit test.
2. Grep all `dt`/`time` reads in the hot path.
3. Compare each read against the active world key (e.g., `:sim/dt` vs `:genesis/dt` vs `:tick-dt`).
4. Verify units match the formula's expectation (seconds, ticks, scaled units, or simulation years).
5. Add a test that asserts the expected world contains the correct `dt` and that the transfer produces a known delta per tick.
6. Run the full suite to ensure the fix does not break other systems.

## Anti-patterns
- Assuming a missing feature before checking the time step.
- Hard-coding a fallback value like `1.0` for `dt`.
- Fixing only the visible symptom without adding a dt-aware test.

## Output
- A targeted test exposing the dt mismatch.
- A corrected time-step read with the right key and units.
- A passing suite.

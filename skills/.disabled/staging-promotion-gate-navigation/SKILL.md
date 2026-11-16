---
name: staging-promotion-gate-navigation
description: "Landing PRs in repos with staging->main promotion gates: staging must fast-forward to the PR head SHA (gates check is-ancestor + per-SHA deploy checks; merge commits break both); fetch depth >1 for ancestor checks; CI environments need clj-kondo/java explicitly installed; long-masked test bugs surface when newer linters run; verify required checks have reporting workflows before assuming green is enough."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: staging-promotion-gate-navigation
  recurrence: 1
---

# Skill: staging-promotion-gate-navigation

## Goal
Landing PRs in repos with staging->main promotion gates: staging must fast-forward to the PR head SHA (gates check is-ancestor + per-SHA deploy checks; merge commits break both); fetch depth >1 for ancestor checks; CI environments need clj-kondo/java explicitly installed; long-masked test bugs surface when newer linters run; verify required checks have reporting workflows before assuming green is enough.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to staging-promotion-gate-navigation.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: Landing PRs in repos with staging->main promotion gates: staging must fast-forward to the PR head SHA (gates check is-ancestor + per-SHA deploy checks; merge commits break both); fetch depth >1 for ancestor checks; CI environments need clj-kondo/java explicitly installed; long-masked test bugs surface when newer linters run; verify required checks have reporting workflows before assuming green is enough.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.

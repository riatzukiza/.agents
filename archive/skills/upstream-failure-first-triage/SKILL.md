---
name: upstream-failure-first-triage
description: "When a user reports a 500 whose detail names an upstream dependency fetch failure, verify that dependency directly and report outage status before changing application code; only patch after proving local code caused the upstream failure."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: upstream-failure-first-triage
  recurrence: 1
---

# Skill: upstream-failure-first-triage

## Goal
When a user reports a 500 whose detail names an upstream dependency fetch failure, verify that dependency directly and report outage status before changing application code; only patch after proving local code caused the upstream failure.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to upstream-failure-first-triage.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: When a user reports a 500 whose detail names an upstream dependency fetch failure, verify that dependency directly and report outage status before changing application code; only patch after proving local code caused the upstream failure.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.

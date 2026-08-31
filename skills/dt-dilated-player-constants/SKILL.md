---
name: dt-dilated-player-constants
description: "When sizing player-facing velocities/forces for a time-dilated simulation, always derive the constant from the integrator's advance rule (displacement per tick = v·dt_sim), never from wall-clock frame feel; add a dt-invariance property test (doseq over the dt range) for every such constant."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: dt-dilated-player-constants
  recurrence: 1
---

# Skill: dt-dilated-player-constants

## Goal
When sizing player-facing velocities/forces for a time-dilated simulation, always derive the constant from the integrator's advance rule (displacement per tick = v·dt_sim), never from wall-clock frame feel; add a dt-invariance property test (doseq over the dt range) for every such constant.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to dt-dilated-player-constants.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: When sizing player-facing velocities/forces for a time-dilated simulation, always derive the constant from the integrator's advance rule (displacement per tick = v·dt_sim), never from wall-clock frame feel; add a dt-invariance property test (doseq over the dt range) for every such constant.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.

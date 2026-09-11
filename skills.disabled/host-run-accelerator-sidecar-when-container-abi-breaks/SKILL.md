---
name: host-run-accelerator-sidecar-when-container-abi-breaks
description: "Recover a local stack that depends on NPU/GPU acceleration by validating the host runtime, redirecting containers to a managed host-side service over host.docker.internal, and demoting the broken containerized accelerator path to an opt-in profile until ABI issues are fixed."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: host-run-accelerator-sidecar-when-container-abi-breaks
  recurrence: 1
---

# Skill: host-run-accelerator-sidecar-when-container-abi-breaks

## Goal
Recover a local stack that depends on NPU/GPU acceleration by validating the host runtime, redirecting containers to a managed host-side service over host.docker.internal, and demoting the broken containerized accelerator path to an opt-in profile until ABI issues are fixed.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to host-run-accelerator-sidecar-when-container-abi-breaks.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: Recover a local stack that depends on NPU/GPU acceleration by validating the host runtime, redirecting containers to a managed host-side service over host.docker.internal, and demoting the broken containerized accelerator path to an opt-in profile until ABI issues are fixed.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.

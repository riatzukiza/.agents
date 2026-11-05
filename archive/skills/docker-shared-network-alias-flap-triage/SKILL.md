---
name: docker-shared-network-alias-flap-triage
description: "When public routes randomly hit different auth/config states, check for staging/prod Docker compose projects sharing a network with duplicate service aliases; verify name resolution from the proxy container and fix by project-specific container names or network isolation."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: docker-shared-network-alias-flap-triage
  recurrence: 1
---

# Skill: docker-shared-network-alias-flap-triage

## Goal
When public routes randomly hit different auth/config states, check for staging/prod Docker compose projects sharing a network with duplicate service aliases; verify name resolution from the proxy container and fix by project-specific container names or network isolation.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to docker-shared-network-alias-flap-triage.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: When public routes randomly hit different auth/config states, check for staging/prod Docker compose projects sharing a network with duplicate service aliases; verify name resolution from the proxy container and fix by project-specific container names or network isolation.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.

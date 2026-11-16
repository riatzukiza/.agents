---
name: nginx-rate-limit-debug-for-browser-503
description: "When browser AJAX requests return 503 but curl works, check nginx rate limiting zones - browsers make concurrent requests that can trigger rate limits that single curl requests don't hit."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: nginx-rate-limit-debug-for-browser-503
  recurrence: 1
---

# Skill: nginx-rate-limit-debug-for-browser-503

## Goal
When browser AJAX requests return 503 but curl works, check nginx rate limiting zones - browsers make concurrent requests that can trigger rate limits that single curl requests don't hit.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to nginx-rate-limit-debug-for-browser-503.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: When browser AJAX requests return 503 but curl works, check nginx rate limiting zones - browsers make concurrent requests that can trigger rate limits that single curl requests don't hit.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.

---
name: shadow-cljs-esm-export-verification
description: "Pattern for verifying shadow-cljs :esm target exports work at runtime: test with node -e after every build, watch for undefined exports caused by CLJS namespace init ordering or paren imbalance, and keep heavy-IO modules as .mjs imports instead of CLJS exports."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: shadow-cljs-esm-export-verification
  recurrence: 1
---

# Skill: shadow-cljs-esm-export-verification

## Goal
Pattern for verifying shadow-cljs :esm target exports work at runtime: test with node -e after every build, watch for undefined exports caused by CLJS namespace init ordering or paren imbalance, and keep heavy-IO modules as .mjs imports instead of CLJS exports.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to shadow-cljs-esm-export-verification.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: Pattern for verifying shadow-cljs :esm target exports work at runtime: test with node -e after every build, watch for undefined exports caused by CLJS namespace init ordering or paren imbalance, and keep heavy-IO modules as .mjs imports instead of CLJS exports.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.

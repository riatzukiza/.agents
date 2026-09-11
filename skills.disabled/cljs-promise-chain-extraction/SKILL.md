---
name: cljs-promise-chain-extraction
description: "When a CLJS promise chain exceeds ~4 nesting levels or has more than ~8 trailing closing parens, extract the inner handlers as separate private defns. Each defn should return a Promise and take minimal args. Verify by checking compiled JS for empty .then()/.catch() calls."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: cljs-promise-chain-extraction
  recurrence: 1
---

# Skill: cljs-promise-chain-extraction

## Goal
When a CLJS promise chain exceeds ~4 nesting levels or has more than ~8 trailing closing parens, extract the inner handlers as separate private defns. Each defn should return a Promise and take minimal args. Verify by checking compiled JS for empty .then()/.catch() calls.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to cljs-promise-chain-extraction.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: When a CLJS promise chain exceeds ~4 nesting levels or has more than ~8 trailing closing parens, extract the inner handlers as separate private defns. Each defn should return a Promise and take minimal args. Verify by checking compiled JS for empty .then()/.catch() calls.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.

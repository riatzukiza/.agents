---
name: verification-lab
description: "Verify whether a claim, integration, provider capability, behavior, or bug report is actually true by checking the narrowest trustworthy sources and producing a clear confirmed/disproven/unresolved ledger."
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: verification
  version: 1
---

# Skill: Verification Lab

## Goal
Turn fuzzy suspicion into a disciplined truth check.

## Use This Skill When
- The user says things like:
  - "verify"
  - "validate"
  - "confirm"
  - "check whether"
  - "does it work?"
  - "against the current code"
- A provider, API, model capability, regression, or configuration must be confirmed.
- You need to separate rumor from reproduction.

## Do Not Use This Skill When
- The task is a broad landscape survey; use `research-landscape-scout`.
- The task is a repo-history excavation; dispatch the `repo-lore-archaeologist` subagent.
- The task is mainly benchmarking; use `benchmark-intelligence`.

## Inputs
- The exact claim to test.
- Candidate sources of truth:
  - code
  - docs
  - runtime output
  - API responses
  - repro commands
  - screenshots or logs

## Workflow
1. Rewrite the claim into a testable sentence.
2. Choose the narrowest trustworthy sources first.
3. Check code and docs before wider speculation.
4. Run the smallest viable repro if runtime evidence is needed.
5. Classify the result as:
   - confirmed
   - disproven
   - unresolved
6. Attach receipts and note what remains uncertain.

## Output
- Claim under test.
- Result: confirmed / disproven / unresolved.
- Evidence and receipts.
- Minimal repro or verification path.
- Short note on what would resolve any remaining uncertainty.

## Strong Hints
- Avoid giant exploratory detours.
- If docs and runtime disagree, say so explicitly.
- Do not blur "seems likely" into "confirmed".
- Prefer one sharp repro over ten loose arguments.

## References
- Related research skill: `research-landscape-scout`
- Related audit skill: `audit-trace-investigator`
- Related structured analysis skill: `research-signal-scoring`

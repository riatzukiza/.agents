---
name: "ultra-code"
description: "Complete substantial work in the current local environment, using either direct execution or native parallel agents with a bounded review and repair cycle."
---

# UltraCode

See the [execution instructions](references/INSTRUCTIONS.md), [contract](CONTRACT.edn), and [qualification configuration](configs/qualification.edn).

Work in the current selected local environment. Do not create a remote task, PR, hosted review, deployment, or service as a side effect of invoking UltraCode. Respect explicit user choices and required repository or platform controls.

Choose one of two paths and start promptly:

1. **Direct local:** use one worker when the work is coupled, small enough to coordinate directly, or native dispatch is unavailable.
2. **Parallel local:** use native agents in the same selected environment for independent packets; assign one writer per resource and integrate their changes locally. Do not use an external runner merely to get parallelism.

Make a short plan identifying the outcome, acceptance checks, dependencies, ownership and risky assumptions. Update it when needed, but do not start a separate planning-review loop. Capture enough source and test evidence to identify what was checked, including dirty and untracked changes where relevant. Use the repository's required evidence format when applicable. A strict EDN plan, manifest and validator are optional, not prerequisites for ordinary local work.

## Implement, check and finish

- Implement independent ready work concurrently when useful, preserving unrelated changes. Serialize overlapping paths and shared resources. Native contexts are not automatically isolated sandboxes.
- Run appropriate focused tests during implementation, integrate the actual combined tree, and run affected checks on it. Report unrun or failing checks honestly.
- Perform one proportionate review of the integrated result, using a fresh native reviewer when available. If no independent context is available, inspect and test directly and report that limitation. A separate skeptic is optional when risk or a disputed finding warrants it, not a required second review.
- Fix substantiated findings and verify the changed behavior. Do not restart a full review solely because a repair changed the candidate. Use a focused recheck of the repair and its affected behavior.
- **Bound the loop:** at most two review-driven repair passes for the entire task by default, including after replanning, integration or handoffs. The budget never resets when a plan, candidate, reviewer or wave changes. One pass can fix multiple findings. Required checks and safety obligations are not waived. If material findings or failed required checks remain after the budget, stop the review/repair loop, preserve the partial work, and report the specific unresolved issues and next decision instead of declaring success or silently starting another round. A user can explicitly choose a different bound.
- Do not treat failed, missing, stale or truncated review as a clean result. Distinguish observed review and test results from assumptions. Do not fabricate approvals or bypass required controls.

For explicit PR or publication work, follow the target repository's actual requirements and its PR flow when available. Publication, merge and deployment require their own authorization. Do not start them merely because local work finished.

## Optional strict native evidence

When a task specifically needs this stricter receipt format, the deterministic helper reads EDN and evaluates supplied receipts. It does not launch models, infer missing outcomes, authenticate a reviewer, enforce a sandbox, post a GitHub review, or authorize a merge.

From this skill directory:

```sh
nbb -cp src scripts/native.cljs sha256 PLAN.edn
nbb -cp src scripts/native.cljs check PLAN.edn RECEIPTS.edn RESULT.edn
nbb -cp src:tests tests/native_test.cljs
node tests/source-integrity.js
```

See [native-schema.md](references/native-schema.md) for generating the exact reviewer-output hash required by skeptic receipts. Use create-only result files. Exit 0 means the supplied complete roster is clean; other qualification results are nonzero. Independently verify that supplied records correspond to actual host execution and the source manifest.

NBB and this strict profile are not required for ordinary local execution. If requested but unavailable, report that it was not run and do not claim its qualification.

## Handoff

Report what changed, the final source state, checks and review performed, and any remaining risks or blockers. Preserve required repository records without inventing successful execution. A background task is ongoing only while a real host handle exists.

## Compatibility and provenance

The six original OpenCode skill assets remain byte-preserved and covered by `tests/source-integrity.js`. [canonical/upstream-skill.md](canonical/upstream-skill.md) records the imported instructions. The old `agents/ultra-*.md`, `workflows/template-review.edn` and `scripts/ultra.bb` belong to the explicit legacy backend. Native roles and evidence use the references above. No historical receipt, quorum or result is reinterpreted as native qualification.

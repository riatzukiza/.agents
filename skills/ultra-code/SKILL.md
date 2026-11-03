---
name: "ultra-code"
description: "Coordinate parallel implementation, independent review and skeptic subagents using the active harness. Persist EDN plans and revision-bound evidence, validate completeness, and review the integrated result. Prefer native foreground or background agents; use the legacy OpenCode/Babashka runner only when explicitly selected."
---

# UltraCode

Use the active harness to execute an explicit dependency graph. Keep the plan and evidence durable; let the harness own spawning, concurrency, notification, waiting and cancellation. Native subagents do not require a second OpenCode process, a background shell workaround, a hosted reviewer, or a separate service.

Read [execution instructions](references/INSTRUCTIONS.md) and the [native evidence format](references/native-schema.md). Use [harness adapters](references/harness-adapters.md) only for the current host. The operating contract is [CONTRACT.edn](CONTRACT.edn); [qualification configuration](configs/qualification.edn) distinguishes native evidence checks from the retained legacy runner.

## Select execution by observed capability

1. Discover the actual spawn, result, wait, cancel and capacity surfaces exposed in this session. Record harness/version where available, context inheritance, write controls and the selected model. A product name or a documented feature is not evidence that this session exposes it.
2. Prefer native agents. Use native background execution and notifications when available. If agents are foreground-only or capacity is one, use the same plan with bounded sequential dispatch; preserve independent contexts.
3. If no native dispatch exists, record the limitation. An explicitly selected external adapter may execute the plan. Read [legacy qualification](references/legacy-qualification.md) before using `scripts/ultra.bb`; its historical result is not a qualified native result.
4. Honor the user's model/cost choices. Resolve actual model IDs from the host; do not assume a named provider, free tier or model exists. Do not elevate model cost or use paid fallback without applicable authorization.

The reference profiles in [configs/native.edn](configs/native.edn) are policy data for the coordinator. They do not install tools or configure a host automatically.

## Plan independent work

Write a user story with the outcome, why it matters and observable acceptance criteria. Record the relevant contract and a failing behavior test before domain changes. For documentation-only changes, use meaningful source and link verification.

Create a run directory under the target repository's `.ημ/runs/<unique-run-id>/`. Persist an immutable source manifest and EDN plan before dispatch. The manifest identifies repository, base/head/tree and any dirty or untracked bytes under review. Pin prompts, role instructions, schemas, write packets, gates, model selection and dependency inputs. Hash the exact plan bytes.

Split work by independently testable contracts. Give each writer an owned worktree or branch and a declared path/resource packet. In a shared tree, use disjoint write ownership and a single integrator; serialize overlapping files, generated outputs, lockfiles, migrations, shared ports and deployment targets. A fresh context is a review boundary, not an automatic filesystem sandbox.

Use one coordinator per run and one writer for each branch/ref. Independent PRs may have different coordinators and proceed together. Serialize only real dependencies or contested resources. Keep operational card state in the existing board owner; the workflow is execution evidence, not a replacement board or event system.

## Execute the graph

- Dispatch ready jobs up to the observed capacity. A job is ready only after its required inputs and successful dependencies exist.
- Give implementers the story, exact source inputs, contract, tests and owned packet. Keep their changes bounded; preserve unrelated work.
- Freeze a concrete candidate before review. A review plan is immutable and does not include writing to that same reviewed snapshot. Implementation waves and review waves have separate snapshot identities.
- Start reviewer contexts without the implementer's conversation, preferred answer or prior verdict. Supply the story, baseline, candidate, relevant source and verification results. Use the [reviewer role](references/native-reviewer.md).
- Predeclare one or more skeptic jobs for each reviewer, including reviews that may return no findings. Each skeptic depends on its reviewer and receives the exact returned artifact plus the underlying source. Use the [skeptic role](references/native-skeptic.md). Review-wide skeptic jobs avoid changing the plan after findings arrive.
- Record the host's returned context/task IDs, spawn observations, lifecycle, actual output, scope and artifact hashes. Keep role/context identity distinct from the connector principal and from GitHub approval identity.
- On completion, validate output before counting it. Missing, truncated, failed, timed-out, cancelled, malformed or uncertain jobs remain visible and cannot supply clean evidence.
- Reuse a completed job only when its immutable inputs and dependencies still match. Reattach to an observed running native task where supported; do not redispatch merely because a tool call yielded. After ambiguous completion, reconcile the original handle before retrying.

Independent review and skepticism are work roles, not a required brand roster. Additional specialist review is chosen from the change's risks. Ordinary review does not require five provider rounds or approval from every available model.

## Collect, repair and integrate

Give every finding a stable ID, evidence, scope and a disposition. Treat skeptic outcomes as confirmed, refuted with evidence, or uncertain. Uncertainty is not refutation. A clean result requires completed declared coverage, successful required gates and all findings resolved or independently refuted. Do not turn an empty result caused by failure into a no-findings review.

Fix warranted findings and add regression evidence. Start a new immutable round after candidate changes; old reviews remain historical. Use a declared per-wave repair budget to bound repeated attempts. At exhaustion, reassess the cause, change the plan or reviewer lens, or preserve a concrete external blocker; do not impose a lifetime round cap or ask again for work already authorized. Exhausting a wave never accepts unresolved work. Continue independent jobs while a different packet is blocked.

The integrator combines qualified packets in dependency order, runs the affected cross-package checks, and commissions fresh reviewer/skeptic contexts on the final combined tree. Per-packet success does not qualify the combined result. Recheck the current base and tested merge candidate before publication or an authorized merge.

## Validate native evidence

The deterministic helper reads EDN and evaluates supplied receipts. It does not launch models, infer missing outcomes, authenticate a reviewer, enforce a sandbox, post a GitHub review, or authorize a merge.

From this skill directory:

```sh
nbb -cp src scripts/native.cljs sha256 PLAN.edn
nbb -cp src scripts/native.cljs check PLAN.edn RECEIPTS.edn RESULT.edn
nbb -cp src:tests tests/native_test.cljs
node tests/source-integrity.js
```

See [native-schema.md](references/native-schema.md) for generating the exact reviewer-output hash required by skeptic receipts. Use create-only result files. Exit 0 means the supplied complete roster is clean; other qualification results are nonzero. Independently verify that supplied records correspond to actual host execution and the source manifest.

When NBB is unavailable, native agents can still perform the work. Record the deterministic validator as unavailable and do not claim its qualification. Preserve the same plan and receipts for a capable host.

## PR and durable handoff

Publish a concise author walkthrough and the reviewed candidate/evidence links through the authorized PR path. A fresh-context review can satisfy the local agent-review role under this workflow. It is not a second GitHub account and does not become a native Bot approval because it is copied into a comment.

Check actual repository requirements independently: required tests, required reviews or App identity, unresolved conversations, merge base and exact head. Honor them. If a required publisher/check cannot consume native evidence, record that adapter gap and continue independent work. Never bypass protection or fabricate hosted approval.

Preserve plans, source manifests, all attempts and original receipts. Follow the repository's adopted Receipt River/Clio format; use immutable per-writer segments where adopted, and do not race to overwrite a shared ledger. Capture attempted, executed, tested and persisted separately. Do not use `--fresh` to delete a retained run.

Report the candidate revision, completed/expected jobs, findings and dispositions, gates, integration status, external requirements and durable artifact location. A native background task is ongoing only while a real host handle exists; do not promise execution after the host session ends without a supported durable mechanism.

## Compatibility and provenance

The six original OpenCode skill assets remain byte-preserved and covered by `tests/source-integrity.js`. [canonical/upstream-skill.md](canonical/upstream-skill.md) records the imported instructions. The old `agents/ultra-*.md`, `workflows/template-review.edn` and `scripts/ultra.bb` belong to the explicit legacy backend. Native roles and evidence use the references above. No historical receipt, quorum or result is reinterpreted as native qualification.

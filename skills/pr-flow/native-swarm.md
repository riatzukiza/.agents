# Native swarm PR review

This profile implements the user's 2026-11-03 review direction. Work in the current local environment; use an independent context for proportionate review when available. Keep the existing GitHub mechanics and required checks at the publication boundary.

## Ownership and parallelism

Assign one coordinator to each PR or tightly coupled stack. Different independent PRs may have different coordinators and run at the same time. Assign one writer to each branch/ref and contested resource. A coordinator may delegate implementation, tests and review; it alone publishes that PR's aggregate state and performs an authorized merge.

Use separate worktrees for concurrent writers when available. In a shared tree, declare disjoint paths/resources, freeze review snapshots, and serialize actual overlaps. Maintain a dependency graph; do not make unrelated PRs wait for a global active-PR slot.

## Loop

1. Write a short outcome, acceptance checks and ownership plan. Do not create a planning-review loop.
2. Execute directly or dispatch independent packets to native local agents. Keep writes bounded and integrate the actual combined tree.
3. Run affected checks and perform one proportionate review of the combined result, using a fresh context when available. A skeptic is optional for risk or a disputed finding.
4. Fix substantiated findings and recheck the affected behavior. Use at most two review-driven repair passes for the entire task, including across replanning or new candidates; do not reset the budget. If material findings or required checks remain unresolved, stop and report them rather than starting another loop or claiming success.
5. For an authorized PR, recheck its current head/base, required checks, approvals and unresolved conversations. Actual GitHub rules remain controlling.
6. Publish a concise author walkthrough through the authorized PR surface: intended outcome, affected story/epic where known, behavior changes, reasons, risks, tests and remaining gaps. Bind it to the exact current head and refresh it when relevant source changes. Inspect existing commentary first, reconcile uncertain publication by readback before retrying, and preserve history. If a separate review is authorized, use COMMENT rather than approval; do not interfere with an existing pending review without authorization. Author commentary does not substitute for independent review, finding settlement, round credit or merge permission. Walkthrough threads retain the existing independent disposition rules; do not self-resolve them to clear a gate. Preserve the fuller walkthrough requirements in PR #57 when integrating that change.
7. Read actual GitHub requirements and live state before an authorized merge: current head/base, required checks and approvals, unresolved conversations, branch policy and tested merge candidate. Bind the merge operation to the verified head. If requirements cannot be read, report that uncertainty; do not infer they are absent.

One proportionate combined-tree review plus passing applicable checks can qualify local work. Additional specialist lenses are chosen from the task risks. Optional hosted reviewers may provide useful findings; their quota or pending state does not add a global review-round obligation to this profile. Findings they have actually raised still require disposition.

## Two evidence boundaries

Native task IDs, fresh contexts, immutable inputs and result artifacts establish the evidence used by the coordinator. They do not create a separate GitHub principal. Do not publish a user-account self-review as a Bot approval or call it an independent account.

A repository may independently require a particular GitHub approval or check. Continue to satisfy that requirement through its authorized producer. If an existing publisher cannot consume native evidence, record an adapter migration task instead of renaming evidence or disabling a check. User authorization changes the requested review process; it does not bypass platform protections.

## CLI boundary

The existing `pr.cljs` and `flow.edn` implement the retained hosted-provider profile. They do not ingest UltraCode's native receipts. Use the optional strict native validator only when that evidence profile is selected, and use the actual GitHub API/CLI for authorized publication with current required-policy checks. Never feed a local review into a provider identity field or treat a non-passing legacy gate as a passing native result.

For required deployed publisher/gate adapters, source changes, protection migration and real runtime verification remain explicit rollout work. This document does not assert those adapters are installed.

## Recovery and persistence

Retain original plans and receipts. Resume observed native task handles where supported; reconcile ambiguous outcomes before reissuing writes. Stop only the blocked dependency branch; continue unrelated ready work within capacity. Native session background execution does not promise work beyond the harness's actual lifetime.

Use the repository's adopted Receipt River/Clio writer and immutable segments. A coordinator owns aggregate publication; do not have every agent overwrite one shared growing file. Preserve previously recorded failures and old provider rounds as historical evidence.

## Integration dependencies

- `.agents` PR #54 adds source-agnostic provider inventory and waivers; preserve it for hosted adapters.
- PR #57 adds the author walkthrough; retain its non-approval boundary.
- PR #34 introduces segmented receipt guidance; do not overwrite retained ledger history.
- PR #52 proposes host/capability placement; capability observations here do not invent a second placement schema.

This is a portable operating profile. Invoke the current `ultra-code` skill for dispatch and evidence details.


# Native swarm review: outcome and acceptance

**Historical proposal:** The 2026-11-05 local-first clarification supersedes this plan's mandatory reviewer/skeptic roster and strict evidence requirements. The current `ultra-code` skill and native PR profile define the bounded default; the strict validator remains optional unless explicitly required.

As a coordinator of concurrent agent work, use the active harness's native subagents for implementation, independent review and skeptical verification, so useful work can proceed without an external runner or a fixed hosted-provider queue.

The user requested this change on 2026-11-03. The audit found explicit external-runner assumptions in UltraCode, repeated provider-round policy in pr-flow, and divergent device catalogs. This source change adds a native profile and preserves the existing hosted-provider implementation as compatibility behavior.

## Acceptance

- Native dispatch is selected from observed session capabilities; no unconditional BB/OpenCode prerequisite.
- Independent PRs may progress together; write ownership is per branch/resource.
- Reviewer and skeptic contexts are fresh, separately observed and bound to the frozen candidate.
- Immutable plan bytes and reviewer-output digests bind receipts; missing, invalid, stale, incomplete or failed evidence never qualifies clean.
- No required GitHub check/review, source boundary or merge authorization is fabricated or bypassed.
- Combined changes receive integration tests and fresh review.
- The native helper has failing behavior tests before repairs and executable green regression evidence.
- Original legacy assets and historical receipts are preserved.
- The complete skill bundles include contracts, instructions, configs, scripts, tests and verified manifests.

## Verification and limits

Native evidence validation is a pure .cljc decision with NBB/Node adapters. The helper validates supplied records and cannot authenticate execution, enforce a sandbox or publish GitHub approval. The hosted pr-flow CLI does not ingest native receipts. Required hosted publisher adoption and actual per-host installation remain separate rollout work.

Reconcile with .agents #54/#53 reviewer inventory, #57 author walkthrough, #34 segmented receipts and #52 capability placement. Keep their unrelated changes. See the accompanying Foresight native-swarm review proposal for the fleet ownership map.

The current-source snapshot and test logs are recorded in the immutable session receipt. No board status transition, merge or production deployment is claimed by this plan.

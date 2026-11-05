# Publish a typed Kimi full-review verdict

Status: planning only. This document changes no executable rule, installed
profile, native review, approval, or release admission.

## Observed problem and ownership

[Proxx PR445](https://github.com/open-hax/proxx/pull/445) has a successful
[native producer and publisher attempt](https://github.com/open-hax/proxx/actions/runs/37208570573/attempts/3)
on `aba0697a4d08a87bf59a33cc03e171b95eef2016`. Its authenticated App published
a `COMMENTED` review after reviewing all ten changed files. Its no-findings
summary differs from the one sentence currently admitted by
`kimi-passing-verdict?` in [the canonical law](scripts/pr_flow/law.cljc).
Full-input provenance and native publication are established for that attempt;
its prose supplies no admitted approval. Preserve that outcome unchanged.

Proxx owns the structured review runner and credential-separated native App
publisher. The canonical `.agents` pr-flow owns native publication admission
and review evidence. This work extends those existing boundaries. It neither
moves approval into the [advisory classifier](raw-review-classifier.md) nor
adds another parser for provider prose. The separate
[native intent plan](https://github.com/riatzukiza/.agents/blob/b94404ba45158fc1086df64de701007b3a15db66/skills/pr-flow/native-review-intent-plan.md) remains independent.

## Outcome and scope

A new versioned full-review protocol carries the reviewer's explicit decision
from the authenticated completed structured response to the actual GitHub
review state. Canonical admission verifies the exact same typed artifact and
native publication custody before recognizing that state. The legacy protocol
remains historical evidence with its original rules, never inferred or upgraded.

Rheos card `publish-a-typed-kimi-full-review-verdict` is `incoming` in
`/home/err/spaces/foresight/docs/agile/kanban/`; the CLI returned that exact
canonical identity. One five-point standalone card covers the protocol and its two existing
boundaries; planning review may require a split. Rheos owns its initial state
and subsequent transitions in the Foresight fleet board.

## Acceptance criteria

1. Define named pure `.cljc` shapes/laws and explicit version 2. Require
   `verdict` in `approve`, `changes_requested`, or `comment`, plus a typed
   `coverage` outcome in `complete` or `incomplete`. Keep the immutable
   repository/PR/base/head, full diff digest, ordered changed-file coverage,
   model/runtime/schema identity, summary and findings bound to the response.
   The verdict and coverage decision must be emitted by the actual completed
   native structured assistant/tool response, not added by the coordinator,
   classifier, publisher, prompt, artifact cache, or an empty-findings heuristic.
   Keep helper-owned runtime/identity provenance separate from model-owned
   judgments, with their complete existing validation and binding.
2. Only explicit `approve` plus `complete` with zero actionable findings can
   request native `APPROVE`. Explicit `changes_requested` maps to
   `REQUEST_CHANGES` and requires at least one substantive actionable finding;
   an explicit `comment` or `incomplete` outcome maps to `COMMENT`. A
   finding-free `comment` stays nonpassing, and missing linked required context
   must be disclosed as incomplete. Unknown versions/keys, contradictory
   decision/finding combinations, missing decisions and malformed output refuse
   before any native publication. Summary prose remains explanatory and has no
   verdict parser.
3. Reuse immutable protected runtime/base-ancestry checks, bounded isolated
   credential-free inference, exact diff/full-file coverage, App identity,
   artifact digest, run/producer attempt, current live base/head/draft checks,
   byte bounds and native publication readback. Proposal data cannot select
   publisher credentials, a review state, or an accepted runtime. Put semantic
   decisions in the pure layer; existing Node/GitHub adapters only perform I/O
   and call those laws. No new TypeScript policy.
4. Extend authenticated native admission to the versioned state and complete
   artifact, retaining verified numeric/node/login Bot identity, full coverage,
   native review ID/body/state/source, workflow/runtime ancestry and revocation.
   A native `APPROVED` label without its qualified complete v2 custody refuses.
   Preserve the v1 `COMMENTED` route unchanged; do not reinterpret old comments
   or convert the cited attempt into approval. Later dismissal, change request,
   source edit, lost history or conflicting custody revokes as under current law.
5. Publication idempotence binds version, typed decision, source and native
   state. If an existing review has incompatible state/body, refuse rather than
   creating another review or silently editing state. Uncertain POST/readback
   outcomes remain uncertain without blind retries. Notification receipts and
   their independent failure/attempt custody remain intact.
6. Planning review and lawful Rheos ready precede implementation. Review the
   pure protocol and each owning adapter in their repositories. Land the
   protected runtime independently, integrate it into the immutable base, then
   update the consumer pin; never execute PR-head helpers with model or App
   credentials. Before canonical adoption, qualify an actual accepted v2
   invocation and authenticated App readback, plus controlled negative and
   revocation evidence. A local fixture or planned text cannot supply native
   IDs, approve its own implementation, or bootstrap its own merge gate.

## Verification

Retain the original failing evidence: the cited real v1 COMMENTED publication
has complete coverage but no admitted Kimi approval. Add RED/GREEN pure and
adapter tests covering all verdict/coverage/finding combinations, v1 stability,
unknown/extra keys, forged classifier output, assistant identity or structured
response mismatch, stale head/base, partial file coverage, replay/conflicting
publication, incomplete source and later dismissal/revocation. Run the owning
Proxx Kimi runner tests and canonical `test_law.cljs`, `test_policy.cljs`,
`test_cli.cljs` and publication tests, with zero warnings and actual native
CI on their current heads. Use accepted full-input invocation and readback
qualification before operational adoption; no rerun-until-favorable experiment.

## Non-goals and risks

No HTML/prose semantic approval grammar, sentence allowlist expansion, automatic
classifier approval, provider fallback, new identity grant, quota exemption,
new review rounds, settlement credit, CI bypass or production cutover. Missing
linked issue/card context remains explicitly incomplete. Typed output cannot
prove semantic correctness: the full-review prompt, protected model purpose,
input custody and review of the protocol still matter. Keep all findings,
independent assessment, cohort, required checks and immutable release gates.

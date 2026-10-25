<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Native review intent: integration design

Status: planning source only. This document changes no installed collector,
assessment purpose, disposition, reviewer identity, settlement or merge gate.
Rheos remains the authority for any linked board state; this design supplies
no board transition or board validation.

## Problem and outcome

The merged [raw classifier](raw-review-classifier.md) preserves a complete
captured review and validates model JSON, but its output is advisory. Meanwhile,
unsupported review-body items conservatively become finding obligations. In
the shared-login stack, native acknowledgements and real unmarked requests
appear together in changing HTML wrappers. Calling every acknowledgement
`Fixed` would invent repairs; extending presentation parsing cannot establish
semantic intent reliably.

The desired outcome is one separately qualified native semantic judgment that
can identify nonactionable body content without removing actual requests or
granting review approval. The canonical owner is this repository; the protected
model invocation, App publication and readback belong to `open-hax/eta-mu`.
Those changes must be independently reviewed and adopted before operational use.

Sources: [classifier PR45](https://github.com/riatzukiza/.agents/pull/45),
[existing body settlement](positive-review-body.md),
[native context and history](actionability.md), and
[independent settlement](../pr-review-settlement/SKILL.md).

## Authority boundaries

| Stage | May produce | Must not produce |
| --- | --- | --- |
| Fresh native collector | Complete raw source, identity, permission, edit and context custody | Intent or admission flags |
| Raw classifier | Advisory source-quoted `actionable`, `informational`, `uncertain` items | Approval, settlement, source freshness or a GitHub write |
| Independent native assessor | Its own explicit judgment of the complete source and applicable history | A copied classifier verdict or a full-PR approval from this purpose |
| Protected publisher | One eligible native assessment with authenticated readback | Synthetic Bot identity, a retry after an uncertain write, or inferred success |
| Canonical disposition | A fresh, revocable finding-obligation decision from qualified evidence | Changed original severity, review coverage, votes, rounds, CI credit or deployment authority |

An `informational` label by itself never clears a blocker. The independent
assessor must consider the original full body, not just selected quotes or a
classifier summary, and must be able to disagree. `actionable` or `uncertain`
content retains its conservative obligation. Quota and transport failures stay
unresolved. Approval and passing-verdict recognition remain outside this design;
in particular this purpose cannot turn a Kimi summary into an approval vote.

## Three separately reviewable implementation slices

### NRI-1: collect fresh source and preserve advisory custody

Reuse the canonical native collector rather than introducing another GitHub
page reader. Bind repository numeric/node identity, PR identity and current
head, actual review numeric/node identity, native author numeric/node/login/type,
published state, immutable reviewed commit, submission and latest edit time,
exact raw body bytes/hash, complete review and issue pages, and applicable
append-only history. Include the policy, prompt and schema identities in model
request custody. Current head and historical reviewed commit remain distinct.

Address selected content with exact source offsets/quotes and the body hash.
Preserve any existing canonical native item identity separately. A derived
content address must never be presented as a native GitHub ID. Admission must
map back to the exact original obligation; matching titles, semantic similarity
or omission from model output cannot clear another item. Ambiguous correspondence
retains the original whole-body/item blocker.

Use the existing bounded raw classifier. Preserve every source character and
all its refusal limits; never truncate a body or select only positive-looking
regions. Identity agreement in a supplied capture is not evidence of freshness.
Refresh after inference and refuse changed head/body/edit/permissions/history.
Collection and advisory output remain free of GitHub mutation and gate effects.

The existing content-derived classifier `request-id` is repeatable and is not
replay protection. NRI-1 therefore adds a versioned, one-use challenge at the
owned parent boundary: generate a fresh 256-bit nonce from the host CSPRNG for
each invocation, never from a capture, model reply or caller-supplied nonce.
Bind that nonce, source/corpus/history digests, policy/prompt/schema identities
and the immutable raw content address into the inference request ID. Require
the model's exact echoed request ID and the transport's exact payload digest.
Keep a single active challenge in the parent, consume it once on success or
failure, and never restore an active challenge from an audit receipt. A restart
generates a new nonce. The original stable source digest stays separate.
Use the existing Receipt River owner for append-only attempt/consumption
evidence; those records supply provenance and cannot themselves reactivate a
challenge or establish native freshness. This requires a reviewed versioned
extension of `prepare`/`validate-result` and its transport binding; the merged
capture-only interface does not already provide this guarantee.

Acceptance: actual collector/transport tests reject incomplete pages, mismatched
REST/GQL source, stale edits, foreign identities, missing history, replayed
digests, dropped units, changed permissions, failed inference and uncertain
output. A valid result still has only advisory authority. Retain original
requests alongside acknowledgements across different presentation wrappers.
Replay controls must classify the same unchanged capture under two different
parent-generated challenges, reject the first output under the second request,
refuse a second consumption of either challenge, reject an externally supplied
challenge, and refuse an old output after a parent restart. Matching a stable
content digest alone must never satisfy these controls.

### NRI-2: independent purpose and protected native publication

Add a distinct body-intent purpose instead of pretending an acknowledgement
is an incorrect corrective request or reusing an author-walkthrough scope.
Specify its named law/schema before adding adapters. Bind the whole selected
native review, complete PR source/context/history, latest authorized proposal,
classifier provenance if supplied, protected source/profile/runtime identities,
and the independent accepted invocation. Retain uncertainty explicitly.

The native assessor must be an enrolled GitHub Bot. Bind and verify its complete
native principal tuple: provider, numeric author ID, node ID, canonical login
and type. Compare it with the opener and every proposal/settlement writer:
matching numeric ID, node ID or canonical login is a collision and refuses
independence, including a renamed/aliased Bot. Unknown or inconsistent fields
also refuse. Validate the enrolled Bot type separately; sharing the type `Bot`
alone is not a principal collision, since independent Bot reviewers necessarily
share it. Normalize only the existing native REST/GraphQL login representation;
never invent another identity from a model, provider label or installation.
It independently decides whether
the selected body content contains a defect, correction request or unresolved
question. The classifier may route attention but cannot supply that decision.
Explicit original P0/P1 requests retain their existing correction, rejection or
preservation rules; a default unknown-item fallback is separate provenance.
Mixed bodies cannot acquire blanket informational admission.

Reuse the protected ETA collector, permission and source checks, bounded
credential-free model execution, complete-input verification and one-shot
App publisher. Never pass an operator-authored transcript as a native Bot result.
Refresh the entire bound input before publication; record actual response and
readback, then apply unchanged native-identity and chronology checks. A candidate
body or successful POST alone is not accepted evidence.

Acceptance: actual owning adapters refuse collisions, self-corroboration,
partial or altered input, unknown purposes, stale proposals, skipped pages,
missing accepted invocation, changed source/profile, missing App permission,
edited/replayed readback and uncertain writes. No fallback publisher, retry,
approval event, conversation resolution or scope expansion is permitted.

### NRI-3: canonical revocable disposition and native qualification

Only after NRI-1/2 source review, explicit adoption and successful native runtime
qualification may the canonical gate consume the new evidence. Keep the source
review and every historical item visible. Do not rewrite previous receipts,
settlements, severity or reviewed commits, or let a local cache become authority.
Preserve existing supported settlements and all inline-thread obligations.

Fresh admission can distinguish a qualified nonactionable body obligation from
a finding; any later distinct writer acknowledgement must be truthful and
cannot invent a repair. Unresolved conversations still block. A new head, body
edit/restoration, request, participant, proposal, policy/profile change,
incomplete recollection or exact-source withdrawal revokes applicable evidence.
Retained revocation history must prevent resurrection of an old source ID.

Represent each change with a canonical observation payload in the existing
Receipt River envelope, rather than a second event store or a fabricated
GitHub event. Its named versioned shape is:

```clojure
{:schema :pr-flow.body-intent/custody-change-v1
 :observation-id <derived digest, never a native GitHub ID>
 :observed-at <collector timestamp>
 :repository {:id <native node ID> :database-id <native numeric ID>}
 :pull-request {:id <native node ID> :number <native number>}
 :trigger <one of the trigger kinds below>
 :target <exact native source or enrolled profile coordinates>
 :before <prior typed custody fields> :after <fresh typed custody fields>
 :native-sources <complete authenticated REST/GraphQL records and digests>
 :previous-custody-generation <prior canonical observation digest>}
```

Derive `observation-id` from trigger, exact target, before/after custody and
previous generation. The envelope ID belongs to Receipt River; neither ID is
a native change-event ID. A repeated observation is idempotent. Native event
IDs/times may be included only when actually supplied and verified by GitHub.
Each admitted assessment records its exact source, permission, proposal,
history, policy/profile and corpus dependencies. Apply changes through those
dependencies with these explicit scopes:

| Trigger | Required target and before/after custody | Revocation scope |
| --- | --- | --- |
| `:head-change` | Exact PR identity and verified old/new full head SHAs | All evidence bound to that PR's prior head/generation; restoration of an old SHA cannot revive its consumed attempts |
| `:review-body-change` | Native review ID/node ID, raw body hash and native latest edit time, including restoration | Every assessment depending on that source or the changed complete PR corpus |
| `:request-change` | Native issue/review/thread comment ID, author tuple, parent/root coordinates, body hash and edit times | All assessments whose input includes that changed request or PR corpus; an unscoped new comment conservatively invalidates that PR corpus |
| `:participant-change` | Native principal tuple and changed source participation, repository permission or enrolled role | Assessments depending on that participant/permission/role and any affected complete PR corpus |
| `:proposal-change` | Native proposal ID, authorized writer tuple, exact target, body hash and edit times | Assessments bound to the changed proposal or a corpus containing it; a newer eligible proposal invalidates the predecessor |
| `:policy-profile-change` | Enrolled immutable policy/profile/runtime identity and old/new digest/version | Evidence consuming the replaced or withdrawn identity/version, across only its explicitly enrolled targets |
| `:collection-incomplete` | Exact attempted PR scope, collector/error identity and missing/failed page or history custody | No evidence for that attempted PR scope may be consumed; recovery requires a fresh complete generation and a new challenge |
| `:assessment-withdrawal` | Authenticated enrolled assessor's native withdrawal ID, exact referenced assessment ID/URL, author tuple, body hash and times | All dispositions consuming that assessment; it supplies no withdrawal of unrelated assessments or review approvals |

Whole-PR context is an explicit dependency: changing a record in that corpus
can revoke every body assessment for that PR. Other PRs and independent enrolled
profiles remain valid unless they share the changed dependency. Unknown scope
blocks the affected collection; it never guesses an item from prose or revokes
unrelated repositories. Preserve revoked generations even if bytes later return
to an earlier value. Missing usable native edit/history custody is uncertainty,
not proof that an unobserved edit-and-restoration never happened; qualification
must demonstrate the required history or refuse admission.

Acceptance: native qualified admission and every negative control execute
through the actual status/gate boundary. Removing or revoking the evidence
restores the conservative blocker. Required checks, full scope, explicit
reviewer requirements, approval quorum and completed-round rules have identical
outcomes before and after an informational admission. No formatting fixture,
allowlisted item/repository, copied Bot text or model agreement supplies native
qualification. A policy manifest must enumerate exact supported purposes and
qualified source/profile/runtime versions; a permissive feature flag is not
adoption. Approval-recognition changes require a separate design and review.
For every trigger above, test the affected dependency and a genuinely unrelated
PR/profile: the former regains its conservative blocker, the latter retains its
prior outcome. Test repeated observations, changed-then-restored bytes, unknown
scope, missing history and withdrawn assessments without resurrecting consumed
attempts or supplying review/CI credit.

## Qualification corpus and rollout

Retain the two public native review fixtures already shipped with PR45. Add
independently adjudicated controls for acknowledgements beside an unmarked
request, contradictory follow-up, genuine P0/P1 prose, quoted/generated markers,
prompt injection, translated wording, wrapper changes, repeated quotes,
unavailable history and body edits followed by restoration. Label derived
controls separately from authentic native source; never forge edit history.
Record model/prompt/schema/runtime identity and cost/latency/coverage for each
actual evaluation. Two successful examples are not a measured accuracy claim.

First compare advisory results to retained independent judgments. Next qualify
the independent purpose and its publisher on fresh actual native input. Adopt
canonical consumption only after its complete end-to-end positive and negative
evidence is reviewed. Preserve one-invocation/no-automatic-paid-fallback behavior;
no model download, new account grant or paid provider activation is included.
Until then, existing review-body obligations and deployment gates remain in force.

<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Fresh native advisory custody (NRI-1 candidate)

Planning [PR47](https://github.com/riatzukiza/.agents/pull/47) merged as
`1f3677d2a1878fbbc90baa39b49e3dccba581a22`. Rheos card
`d1acd458-3e9b-4641-afb7-50e34d51703b` lawfully reached `in_progress`.
This candidate implements the advisory source/parent API. It installs no gate,
independent assessment, App publisher, native disposition, or approval rule.
[NRI-2 and NRI-3](native-review-intent-plan.md) still own those boundaries.

`pr-flow.native-review/select-source` takes the actual canonical collector's
complete snapshot and selects one whole published native review by its numeric
ID. It never requires an HTML-derived item selection. Raw REST/GraphQL source,
repository/PR identity and current head, historical reviewed commit, all review,
thread and issue pages, native principal tuples, observed User permissions,
policy and history remain separate custody. Existing canonical obligations are
preserved as metadata; they do not determine semantic intent or disappear.
Unknown history is `nil` and refuses; a healthy empty history is `[]`.

The pure `raw-review/prepare-native` and `validate-native-result` implement
`raw-review/v2`. The stable v1 source request ID remains separate. V2 binds a
fresh challenge plus corpus, permission, history and policy digests to the
existing complete raw units, prompt and response schema. Quote addresses use
explicit UTF-16 code-unit offsets into the exact body; SHA256 binds UTF-8 body
bytes. These derived addresses and item hashes are not native GitHub IDs.
The capture CLI and its v1 chat/System One/native-client behavior are unchanged.

`pr-flow.fresh-review/parent` generates each 256-bit nonce with Node's CSPRNG.
The caller selects repository, PR and numeric review ID, model and supported
protocol; extra selection keys and caller nonces refuse before collection.
One parent holds at most one active challenge. Its returned `consume!` function
atomically consumes that exact challenge before validation/recollection, even
on failure. A replay or second consumption refuses without consuming a newer
challenge. No receipt/capture can restore an active challenge after restart.
The convenience `classify!` returns a Promise, calls the supplied bounded
transport once and awaits either a response or Promise before consumption.
Synchronous throws and asynchronous rejections both take the same consumed
failure path. There is no retry or fallback. Its native JavaScript async/await
interop supplies only runtime sequencing: NBB 1.3.204 does not implement the
ClojureScript `await` form. Source `classify!` keeps `^:async` metadata and no
Promise chain or domain policy is implemented in the JavaScript boundary.

The parent uses `native-context/collect-context` with complete body collection
before and after successful inference. It re-reads permissions and policy,
compares complete source/history custody, checks the child transport's exact
serialized payload digest, then validates every unit and quote. It supports
the existing JSON chat and native OpenCode transports. V2 System One invocation
is not defined here; the existing v1 capture path remains available.

## Trusted host responsibilities and evidence limits

This is an importable host API, not a new installed command or a capture-based
freshness flag. The host supplies the canonical read-only JSON/permission/history
readers, an enrolled immutable policy/source identity, the existing bounded
transport, and `record!`. Those are trusted effect boundaries, not fields read
from a model or capture. A caller who replaces them with fabricated functions
has not produced native evidence. The parent does not authenticate such a host.
Canonical collection and `record!` are synchronous host callbacks. The host must
complete and verify the owner append before returning from `record!`; starting
an asynchronous append is not that contract. Only `classify!`'s `infer!` boundary
supports either synchronous or Promise-returning transports.

`record!` must call the Receipt River owner's event constructor/validator and
append-only writer, throwing on an unavailable or uncertain append. The parent
requires it and retains complete attempt source/request/model/protocol/payload,
then consumption status and actual transport response. It implements no second
receipt decoder, validator, persistence engine or board command. An uncertain
append refuses classification and never reactivates the consumed challenge.
The protected ETA adapter and its actual owner-backed append/native qualification
remain work of NRI-2. Test record callbacks are explicitly synthetic, not native
receipts or evidence that this host adapter has been adopted.

Fresh observations cannot prove unobserved intermediate edits never occurred.
History/source gaps, edit-and-restoration custody and operational adoption still
require the separate qualification/admission contract. A structurally valid
classification is only `:triage-only`; it does not prove semantic completeness,
resistance to prompt injection, independent judgment or measured accuracy.

## Verification

`test_fresh_review.cljs` exercises the actual canonical collector with synthetic
native pages, separate principal/permission/history changes, REST/GQL mismatch,
missing pages, caller nonce refusal, replay/restart/second-use controls, failed
transport and uncertain receipt callbacks, and full Unicode quote offsets.
`test_fresh_review_transport.mjs` runs that parent through the actual bounded
HTTP child against a synthetic local provider, verifying source-bound output,
exact payload digest, one invocation and one consumption. No public native IDs
or qualifying model judgments are manufactured by these fixtures.
The same transport test covers promised responses, rejected Promises and
synchronous throws without early consumption, unhandled rejection or retries.

Run those alongside the existing raw classifier, law, policy, CLI and transport
suites. CI registers both new suites. Required current-head code review and
native checks remain prerequisites to source merge; merging this candidate
would still grant no NRI-2/3 adoption or fleet deployment credit.

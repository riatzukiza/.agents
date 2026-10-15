# Candidate informational disposition

Introduced from `9ee8831ffe001b025d425a239a2d30e95378ea6a`. The parent accepted
this bounded design on 2026-10-03. It remains provisional: source publication and
local tests do not qualify operational use. Current hosted source review,
retained revocation history and a fresh independent native assessment remain
necessary. Neither captured walkthrough is qualified by this document or the
synthetic tests. Frozen handoffs record preparation-time state separately.

## Authority and scope

All threads default to finding obligations. The initial positive scope is a
resolved native User=PR-author thread, with complete ordered comments from that
same User and empty native enclosing COMMENTED reviews from that User. Native
current comment commits must bind the PR head; original commits remain separate
historical identities. Every comment, enclosing review and diff hunk is retained.
Unknown/missing metadata and partial pagination block admission. The current
100-comment cap remains fail-closed; this feature does not waive it.

The separate `resolved-author-only-commented-review-context` scope permits a
nonempty native enclosing body, retaining the same resolved author-only User
and COMMENTED-review requirements. Its exact body hash is already in the
ordered manifest. For matching true native/normalized resolved flags, all empty
bodies keep the original scope; at least one nonempty body selects this scope.
Missing body metadata has no scope. Empty/nonempty body and empty-hash
consistency are checked; the existing effectful boundary computes exact UTF-8
hashes. An independent assessment must consider the whole enclosing body,
complete conversation and relevant source. No walkthrough prefix, caller flag
or semantic regex grants informational status. Fresh old-scope evidence cannot
admit a nonempty review, and additional-scope evidence cannot admit an empty one.

The distinct `unresolved-author-only-commented-review-context` scope requires
both native and normalized resolution flags to be exactly false and at least
one nonempty enclosing author COMMENTED body. Complete custody, fresh writer
proposal and independent no-defect/no-request/no-question judgment retain the
same laws. Empty unresolved reviews and missing/mismatched flags have no
supported scope. Qualified unresolved informational conversations remain
unsettled and block the merge gate's all-thread unresolved check.

`actionability/resolution-authorization` is a pure eligibility result for a
canonically classified thread. A caller must first use `law/classify-thread`,
which derives existing same-Bot source withdrawals, then pass that complete
thread. The function requires its derived withdrawal set of positive native
IDs and recomputes disposition; raw input, malformed sets and supplied
informational/authorization booleans cannot supply eligibility. This data shape
alone does not prove native provenance: an effectful adapter must freshly
collect and classify the context itself, never accept caller-provided flags or
derived sets as authority. Its positive result binds the head, context vector,
proposal and assessment IDs and channel; it grants no actor permission, GitHub
effect, settlement or merge credit. The pure function itself adds no resolver, collector or transport.
The separate candidate CLI effect boundary is described below; it requires
protected source review and adoption before operation. Every actual finding still needs settlement before resolution,
and the opener cannot settle their own pushback.

A later guarded resolution changes the manifest's resolved flag and digest.
Old pre-resolution evidence must lose binding. Recollect native facts and obtain
a new writer proposal and independent assessment under the resolved scope;
never flip an old manifest flag or transfer the earlier assessment. Such native
administration requires a separately reviewed/adopted adapter and actual fresh
assessment route before use.

The importable `scripts/pr_flow/native_context.cljs` collector reuses the CLI's
native page validation and exact UTF-8 hydration through explicitly injected
synchronous JSON, repository-permission and history readers. It returns native
contexts, exact issue records and history separately; unavailable history remains
distinct from a healthy empty history. Importing or collecting does not classify,
append observations, dispatch the CLI or authorize a native mutation. The CLI
retains its canonical classifier, complete history reader, observation writer
and changed-snapshot guards. Nested comment pagination still marks the collection
incomplete. The pure `pr-flow.permissions/writer-role?` predicate exposes the
existing exact `admin`, `maintain` and `write` role membership; native identity,
permission reads and failure handling remain at the caller boundary. Injected
data is not proof of native provenance: a later adapter must
collect fresh native facts and use canonical classification and authorization;
this extraction supplies no resolver, assessment transport or operational credit.

`:review/actionability` is a separate consumed candidate flow default. Only
verified native OpenCode `opencode-agent[bot]`, user `219766164`, node
`BOT_kgDODRldlA`, is admitted globally here. A source-preparation extension in
`:repository-identities` additionally admits native MiMo `eta-mu-ai[bot]`, user
`270021952`, node `BOT_kgDOEBg1QA`, only for the exact native repository pairs
`["open-hax/proxx" 1178288746]`, `["open-hax/knoxx" 1199983316]`, and
`["riatzukiza/knoxx" 1401249297]`.

The development-fork entry is the same two-field repository enrollment as the
existing entries. Its transport must independently verify the exact public
repository and native node `R_kgDOU4VeEQ` before creating a target token. An
enrollment entry does not establish an App installation grant, a completed
independent invocation or permission to resolve a conversation. The synthetic
fork-shaped controls in `test_informational.cljs` execute through the registered
`test_law.cljs` entrypoint; they supply no native assessment or review credit.
Both fields must match; aliases, renames,
foreign repositories and matching names with different IDs do not admit MiMo.
The real full-review tuple supplies identity evidence, not a new scoped native
assessment, independent execution proof or activation credit. This extension
still needs fresh full native source qualification, protected source publication
and explicit adoption with the entire then-current authentic operational
history and reflection; retain this source fork's provenance separately.
Protocol withdrawals must match their source's exact admitted Bot tuple.
Neither rejection identities nor approval
identities automatically admit an actionability assessor. The default reviewer
quorum, participants, minimum, mandatory overrides and invitations are unchanged.

The parent explicitly chose the same authenticated native PR issue-comment
transport principle as 9ee rejection evidence. There is **no new requirement
that the originating workflow finish successfully**. This proves observed native
identity/publication, not immutable runtime, model, execution independence or
App-key isolation. Candidate-authored instructions are not assessment policy.
Parent must review this source/profile and qualify an actual fresh native
assessment before operational use; no invocation or activation occurs here.

## Version 1 binding

`actionability/context-manifest` emits a fixed ordered EDN vector:

1. Version, native repository IDs/name.
2. Native PR ID/number/current full head and author identity.
3. Native thread ID, resolved/outdated flags, path and current line.
4. Ordered comment tuples: native node/numeric IDs, URL, author identity,
   creation/edit times, current/original commits, exact body/diff-hunk hashes,
   and enclosing review ID/state/edit time/author/commit/body hash.

Hash exact GitHub-decoded UTF-8 bodies/hunks using SHA-256. Hash UTF-8
`pr-str` of the ordered manifest for the context digest. No map order or prose
normalization is used. Hydration computes these hashes; pure law checks their
binding and metadata without implementing a second cryptographic runtime.

Each live protocol record has **two unquoted protocol lines**: the header below
and one canonical `pr-str` EDN vector of strings/integers. Only the observed
OpenCode trailing blank line plus `[github run](/owner/repo/actions/runs/id)`
footer is additionally allowed, bound to the same repository. It is transport
format, not a run-success/provenance claim. Other extra fields/lines, versions,
fenced/quoted/generated copies and ambiguous payloads do not qualify.

The examples below show the original scope. The other scopes use the same
fixed arities and protocol, replacing that field with the derived resolved or
unresolved COMMENTED-context scope; no other field or judgment requirement changes.

```text
Actionability proposal v1 for <40-hex-current-head>:
["actionability/v1" "<repo-node>" "<PR-node>" "<thread-node>" <root-numeric-id> "<context-sha256>" "resolved-author-only-empty-reviews"]
```

The proposal is an authenticated repository-writer native issue comment.
Creation must follow every captured context edit. An edited proposal, including
restored text, requires a fresh proposal ID. Latest proposal/edit chronology
controls; no old agreement survives a later proposal.

```text
Actionability assessment v1 for <40-hex-current-head>:
["actionability/v1" "<repo-node>" "<PR-node>" "<thread-node>" <root-numeric-id> "<context-sha256>" "resolved-author-only-empty-reviews" <proposal-native-id> "<proposal-body-sha256>" "informational" "complete-context/no-defect/no-request/no-question" "<independent substantive reason>" "<scoped code/test/document evidence>"]
```

Only the independently admitted native Bot may assess, after the proposal.
It must differ from proposer/PR author/root author. `finding` and `uncertain`
decisions remain obligations; conflicting or ambiguous current-context native
assessments fail closed. Reason length and evidence syntax are mechanical minima:
**they cannot prove independent reasoning or guarantee semantic correctness**.
An assessor can be wrong. A real defect must not be called informational; native
assessment/source review remains necessary rather than a semantic regex classifier.

`actionability/details?` exposes this mechanical reason/evidence minimum for
transport adapters to check before publication. Named JavaScript (`.js`),
CommonJS (`.cjs`) and JavaScript module (`.mjs`) sources are accepted alongside
the existing code/document references and HTTP(S) URLs. This predicate checks
neither native authority nor the assessment's meaning: adapters must retain
complete binding checks before minting and native disposition verification
after publication. Calling it alone supplies no classification or review credit.

```text
Actionability withdrawal v1 for <40-hex-current-head>:
["actionability/v1" "<repo-node>" "<PR-node>" "<thread-node>" <root-numeric-id> "<context-sha256>" "resolved-author-only-empty-reviews" <assessment-native-id> "<assessment-body-sha256>" "<reason>"]
```

The existing 9ee native withdrawal grammar is also reused with this separate
identity policy: same-Bot explicit withdrawal naming the exact assessment's
native issue-comment ID or URL revokes that source, without a repeated target
line. Wrong-source, forged and quoted withdrawals cannot revoke it.

## Effects and persistence

Informational is **not settlement**. The finding-obligation predicate controls
severity blockers, settlement, contestation, deferral and convergence counts.
Unresolved conversations still block independently; native resolved flags and
branch protection remain untouched. Other findings/review bodies/checks remain
obligations. No approval, coverage, rejection agreement or review-round credit is
created. Disposition itself never replies to, resolves, deletes or relabels a
native conversation; existing explicitly invoked settlement commands retain
their normal authority and cannot supply author self-settlement credit.

CLI reads full paginated native issue context and rechecks native snapshots on
status/settle/gate paths. Changed head/comments/edit metadata/enclosing reviews,
missing evidence, conflicting assessments and withdrawals revoke eligibility.
Old text or a deleted/restored record cannot revive an observed revoked source;
fresh attempts require fresh independent evidence and new native IDs.

When actual scoped evidence exists, these normally observational CLI commands
also append admission/revocation observations to the canonical skill repository's
existing `.ημ/receipts.edn`, using Receipt River's unversioned compatibility
record format. Each new observation includes the repository name supplied to
the CLI in lowercase alongside its native IDs and complete decisions vector.
The reader selects decisions by the authoritative native repository ID after
validating the complete history. Display names are provenance, so rename or
owner transfer cannot discard retained revocation. Repository-less legacy
records remain readable when their decisions carry that native ID. Missing,
invalid or conflicting decision repository IDs make history unavailable.
The reader
continues to preserve and read existing records without rewriting them. This is **local
receipt I/O**, not GitHub mutation or a second ledger authority. The ledger is
needed to remember observed source revocation across executions; it is not
approval. Missing/unreadable history blocks admission. The CLI normalizes its own
append before snapshot comparison; actual native changes still block merge.
No observation is appended merely because an author calls a thread informational.
Persistence is local to the canonical checkout. Receipt River handoffs must
preserve/commit these observations across hosts; this feature does not provide
distributed history or recover deleted evidence absent that history. Parent
must qualify that operational persistence boundary before deployment.

The Knoxx fixture preserves three actual unresolved author walkthroughs and
their nonempty author review unchanged. New proposals/assessments and prospective
resolution flags are explicitly synthetic controls. Actual originals without
fresh independent native evidence remain findings; even qualified unresolved
informational contexts remain merge-blocking. The pure pre-resolution eligibility
layer prepares the bootstrap decision but supplies no collector, assessor
transport, workflow, native resolver, deployment qualification or installed-policy
adoption. Source and fixtures alone cannot clear those conversations.

Tests use current captured Proxx57f/Uxx68e and Knoxx387 conversations, isolated
synthetic protocol records and mocked GitHub effects. They prove binding/gate mechanics,
not actual native approval, model correctness or readiness of either PR.
`PR_FLOW_BASELINE_PROOF=1` additionally runs the same CLI fixtures against exact
9ee Git objects locally. Historical objects are not required in shallow hosted
checkouts; all candidate GREEN and negative tests always run there.

GPL-3.0-or-later.
# Prospective publication and complete history (stacked source proposal)

`pr-flow.actionability/assessment-publication` takes a freshly canonically
classified thread, an accepted summary string, and a prospective native-shaped
Bot user containing only `:login`, `:id`, `:node_id` and `:type`. It factors the
existing disposition evaluation rather than introducing a second admission
policy. The canonical derived positive-ID withdrawal set and complete history
vector are mandatory; caller classification or eligibility flags supply no
authority. The existing strict protocol, latest unedited authorized proposal,
context binding, chronology, conflict and repository-scoped identity predicates
validate the proposed two-line body. A previous informational assessment is not
required. The accepted string must exactly equal the reconstructed body; the
prospective API emits no optional run footer or extra prose.

A positive result contains `:eligible?`, the exact body, native head/context and
proposal bindings, `:expected-user`, and `:created-after` (the actual proposal
edit timestamp). It creates no assessment ID, native publication time, source
URL, assessment body hash, observation or qualified disposition. The principal
comparison is pure shape/policy eligibility, not live App authentication.
Invocation acceptance and semantic independent judgment remain host obligations.
The host must authenticate the actual App, refresh context and history before
publication, POST only the approved body, and re-GET the real native comment.
Actual ID/node/URL/author/body hash and unedited creation strictly after the
proposal must then pass unchanged canonical `disposition`; raced or edited
records fail. Later resolution eligibility is separate, and every unresolved
conversation remains merge-blocking.

`pr-flow.actionability-history/observations` accepts complete decoded UTF-8
receipt text and the native repository ID. It imports the existing edamame
record decoder without importing operational `pr.cljs`. Every nonblank line is
validated before repository selection: exactly one map, with the original
actionability observation grammar and repository binding. Nil, invalid text or
an invalid repository ID return nil; an existing valid source with no matching
observations returns `[]`. The CLI retains its module-relative filesystem path,
actual complete read, error handling and append writer. A pinned source checkout
does not establish current revocation history. The outer caller must retain the
complete source bytes/digest and acquisition provenance; unavailable history
blocks publication. This decoder grants no history or native acquisition
authority and has no read or append effect.

This is stacked source preparation on PR28, not protected merge, installation,
native assessment, resolver operation or deployment qualification. It adds no
identity roster and no approval, coverage, settlement or round credit.

## Candidate resolve-only CLI boundary

`pr.cljs resolve-informational REPO PR THREAD EXPECTED [--apply]` is a separate
source proposal for native administration after independently qualified
unresolved informational disposition. Until this successor is protected,
reviewed and adopted, the route supplies no operational authority. `EXPECTED`
is one canonical `pr-str` EDN map (or `-` for stdin) containing exactly
`:repo`, `:repository-database-id`, `:pr-number`, `:head`, `:thread-id`,
`:root-comment-id`, `:context-binding`, `:proposal-id`, `:assessment-id` and
`:channel`. These are comparison coordinates from actual qualified native
evidence; supplied booleans, withdrawal sets or classifications are refused.

The route creates one GitHub.com keyring `gh` environment and uses it for
identity, repository permissions, complete collector reads, the one optional
resolution POST and readback. This resolver removes `GH_TOKEN`, `GITHUB_TOKEN`,
`GH_ENTERPRISE_TOKEN` and `GITHUB_ENTERPRISE_TOKEN`, and fixes `GH_HOST` to
`github.com`; inherited Enterprise credentials or a host override cannot
redirect its native evidence or mutation. The ordinary CLI authentication and
fallback path retain their existing environment behavior. A native REST `User`
with exact login, numeric ID, node ID and writer role operates the adapter. That User is not an assessor.
The unchanged classifier and actionability law independently verify the
configured native Bot assessor and its current whole-context issue assessment.
The existing structural selector requires a complete, open, nondraft, exact
repository/PR/head/thread snapshot with available full history. Preview reads
and reports eligibility only; it performs no native mutation or ledger append.

With `--apply`, the existing canonical observation writer retains admission.
The adapter compares complete raw ledger bytes and decoded text around that append, recollects all
native input, reclassifies, recomputes authorization and refreshes the same User
and writer permission before the single `resolveReviewThread` attempt. It
posts no reply, review request, assessment, settlement or merge operation.
Unavailable/changed history, target, body, proposal, assessment, withdrawal,
identity or permission refuses before POST. No GitHub head/context CAS exists
for this API; the remaining read-to-POST race is checked by subsequent actual
readback rather than represented as atomic.

After the attempt, recollection must observe the exact target resolved with no
other native change. The existing law computes lost pre-resolution binding;
the existing writer retains its actual revocation observation. The result
reports `:resolved-needs-fresh-assessment` only when mutation response, complete
readback and append custody agree. POST errors, GraphQL errors, malformed or
wrong-target results, unexpected native changes and readback/append failure
produce `:uncertain-native-outcome` and exit 3. Actual command exit/stdout/stderr
are retained; the adapter never repeats an uncertain POST or undoes resolution.
A mutation-attempt count is not a claim that GitHub applied the change.

Resolution changes the real manifest and digest. A new resolved-scope writer
proposal and independent assessment are still required, followed by the normal
all-thread/approval/check gate. This route grants no settlement, rejection,
approval, coverage or round credit, and every actual finding retains the normal
reply-before-resolve policy. The three author walkthrough controls use inert
synthetic P/A issue records around captured context; they authenticate no real
assessment or native resolution.

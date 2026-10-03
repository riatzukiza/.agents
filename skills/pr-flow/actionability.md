# Candidate informational disposition

Preparation from `9ee8831ffe001b025d425a239a2d30e95378ea6a`. The parent accepted
this bounded design on 2026-10-03 and authorized isolated implementation only.
This candidate is provisional: it has not been published, installed, reviewed on
GitHub, or supplied with a fresh native assessment. Neither captured walkthrough
is currently qualified by this document or the synthetic tests.

## Authority and scope

All threads default to finding obligations. The initial positive scope is a
resolved native User=PR-author thread, with complete ordered comments from that
same User and empty native enclosing COMMENTED reviews from that User. Native
current comment commits must bind the PR head; original commits remain separate
historical identities. Every comment, enclosing review and diff hunk is retained.
Unknown/missing metadata and partial pagination block admission. The current
100-comment cap remains fail-closed; this feature does not waive it.

`:review/actionability` is a separate consumed candidate flow default. Only
verified native OpenCode `opencode-agent[bot]`, user `219766164`, node
`BOT_kgDODRldlA`, is admitted here. Neither rejection identities nor approval
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
existing `.ημ/receipts.edn`, using the Receipt River envelope. This is **local
receipt I/O**, not GitHub mutation or a second ledger authority. The ledger is
needed to remember observed source revocation across executions; it is not
approval. Missing/unreadable history blocks admission. The CLI normalizes its own
append before snapshot comparison; actual native changes still block merge.
No observation is appended merely because an author calls a thread informational.
Persistence is local to the canonical checkout. Receipt River handoffs must
preserve/commit these observations across hosts; this feature does not provide
distributed history or recover deleted evidence absent that history. Parent
must qualify that operational persistence boundary before deployment.

Tests use current captured Proxx57f/Uxx68e conversations, isolated synthetic
protocol records and mocked GitHub effects. They prove binding/gate mechanics,
not actual native approval, model correctness or readiness of either PR.
`PR_FLOW_BASELINE_PROOF=1` additionally runs the same CLI fixtures against exact
9ee Git objects locally. Historical objects are not required in shallow hosted
checkouts; all candidate GREEN and negative tests always run there.

GPL-3.0-or-later.

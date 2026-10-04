---
name: pr-review-settlement
description: The planning and code-review loop of pr-flow. Continue until five full review rounds have completed or available agents unanimously approve the current head, and every finding is settled. Prefer verified fixes of every priority during the first five rounds. Outright rejection needs detailed reasoning and explicit agreement from an independent agent besides CodeRabbit.
license: GPL-3.0-or-later
metadata:
  tier: provisional
  origin: pr-flow skill graph, 2026-10-01; user review-policy correction, 2026-10-03
---

# Review settlement

## Use this skill when

- A PR has review comments from CodeRabbit, Codex, MiMo, Kimi, OpenCode, Claude or humans.
- `pr.cljs status` reports a blocked gate because of findings or incomplete review evidence.

## Do not use this skill when

- No review has run on the current head. Run `pr.cljs request` and `wait` first.

## Convergence policy

Five is a **soft minimum**, never a cap. Continue until at least five completed
review rounds have been generated in the current planning or code stage,
**or every available review agent explicitly approves the current head**.
Every finding must also be settled and required gates must pass. One clean
first round can finish; zero P0/P1 findings or quorum one alone cannot end an
earlier round. After five, the default approval quorum remains one, but open
findings, disputed settlements and required checks still block completion.

Each completed round includes full reviews from every available agent; several agents in one round count once. Count actual completed rounds, preserving their native IDs. Deduplicate
multiple representations of one requested review. Requests, acknowledgements,
quota replies, skipped runs and incomplete reviews are not completed passes.
A new push invalidates approvals; it does not erase completed stage history.
Planning history does not satisfy the subsequent code-review stage. Missing,
pending or stale approvals never supply unanimity. Authenticated quota-unavailable optional agents are excluded from the current available cohort under pr-flow; they supply no approval or completed review. Mandatory reviewers remain required, and historical findings remain obligations.

An authorized native User's unedited legacy `pr-flow-head:<full SHA>` plus
planning/code marker preserves that PR's stage declaration at its actual native
time. It supplies no review or approval. A later same-stage request does not
erase it; a different stage still starts a new timestamp boundary. There is no
new retroactive binding of undeclared prior reviews. Native Rheos2 declaration
5968488345 is source evidence of an earlier code stage, rather than a reason to
backdate request5978541505 or reinterpret older heads as current approvals.

## First five rounds

1. Verify every finding against code, contracts and relevant tests.
2. Prefer exactly the CodeRabbit change when it addresses a verified problem.
3. Usually fix every verified finding during these rounds, regardless of
   priority, including nitpicks, docstrings and outside-diff items. Group fixes
   when useful, but do not defer merely because the priority is low or there
   are many comments.
4. If the proposed change is incorrect, harmful or a deliberate won't-do,
   give detailed reasoning, concrete evidence and the consequences of leaving
   it unchanged. Obtain independent agreement before rejecting it outright.
5. If an exceptional deferral requires a user decision, keep it open and
   present the concrete tradeoff. Once the completed-round count is strictly
   above the configured minimum (default five), scoped P2/P3 deferral can be
   justified with an existing follow-up and specific reasoning.

`Handled` means a concern is already addressed: identify the existing code,
fix or duplicate and its verification. It must not disguise a rejected request
or a verified issue that remains unfixed.

## Native opener withdrawal

An authenticated configured Bot withdrawing its own finding is distinct from
an author deciding not to fix it. Preserve the original priority, native root,
enclosing review, withdrawal ID/URL and history. The CLI's bounded grammar
recognizes the opener's own live `I withdraw this finding.` sentence, a
substantive paragraph explaining why that finding was incorrect, and live
source links. Analysis/learning blocks, fenced or quoted examples and generated
shares cannot supply that evidence. Unsupported or ambiguous wording remains
blocking; never publish a transcript or relabel assent as withdrawal.

The native root and withdrawal must both be on the current head and in the
same complete ordered conversation, by the same configured Bot, with no later
pushback or conflicting edit. Different openers, author claims, quota replies,
cross-root scope and old heads do not qualify. The conversation must be resolved
and an authorized User writer must post a settlement naming the current head,
exact root pair and actual native withdrawal URL, for example:

```text
Handled: The finding opener withdrew this finding on <full current head SHA>.
Finding: <PRRT_thread ID> comment<numeric root comment ID>
Withdrawal: <actual native opener withdrawal URL>
Reason: <specific withdrawn premise and its verified source evidence>
```

Reviewer auto-resolution can precede that writer reply; until the reply exists,
the finding is unsettled. This narrow `Handled:` disposition can settle the
withdrawn P0/P1 finding but supplies no independent rejection, approval, full
coverage or round credit. Generic `Handled:` and resolution alone cannot clear
a Major finding. All other findings, approval/cohort/minimum requirements and
required checks remain obligations. Local prospective settlement fixtures are
not native qualification or permission to publish an unsupported settlement.

## Independent rejection

An agent **other than CodeRabbit** must independently agree with the proposed
rejection. CodeRabbit's assent, author self-agreement, a generic acknowledgement,
silence, or an unavailable integration does not count. This applies regardless
of priority and regardless of round. Ask for an assessment of the finding, the
relevant code and tests, and the counterargument; do not ask only for agreement.

For example, request Codex review. If the OpenCode GitHub integration is actually
installed, a comment can say:

> /opencode Independently assess CodeRabbit's request at <finding URL> on <head SHA>. I think the requested change is unnecessary because <reason>. Check that argument against <code/tests>; explain whether you agree or disagree.

Keep the request open while awaiting the answer. Preserve the actual agent
identity, exact head, finding reference, evidence and reasoning. A local
independent agent can supply a real assessment, but a coordinator-published
transcript must not impersonate a native GitHub Bot response. The automated
CLI requires authenticated allowlisted native evidence; unsupported channels
remain a documented operator limitation, not a fabricated approval.

For a thread rejection recognized by the CLI, record the writer's
`Rejection proposal for <full head SHA>:` in that thread, with `Reason:` and
`Evidence:`. The independent allowlisted Bot must reply afterward with
`Rejection agreement for <full head SHA>:` and its own reasoning/evidence.
Only then post `Rejected:` with the detailed `Reason:` and `Evidence:` and
resolve the thread. A new proposal or head needs fresh agreement. Configure a
verified OpenCode app identity before relying on its native evidence; never
guess its login or widen the approval quorum identity list for that purpose.

An installed App may publish its completed assessment as a **PR issue comment**
rather than a thread reply. The CLI supports that native channel when the
authenticated allowlisted non-CodeRabbit Bot uses the exact first line
`Rejection agreement for <full current head SHA>:` and exactly one live
`Finding: <PRRT_thread ID> comment<numeric root comment ID>` line. The writer's
matching in-thread proposal must name those same two native IDs. The Bot's
`Reason:` and `Evidence:` may be inline or multiline sections, but must contain
specific reasoning and code, test or documentation references. Missing,
ambiguous, quoted, fenced or generated markers and generic assent do not count.

The native issue comment must be created after the latest matching proposal
(including any proposal edit), and published/last edited before the writer's
final supported `Rejected:` reply. The finding opener cannot corroborate its
own finding; author self-agreement and non-writer settlements remain blocked.
A new proposal or head needs fresh agreement; neither a different thread nor a
different root comment can reuse it. Later scoped reviewer pushback or withdrawn
agreement blocks the rejection. The CLI reads complete paginated native issue
context, including same-Bot withdrawals explicitly referencing the original
agreement's native issue-comment ID or exact source URL. Such a withdrawal
revokes that source even without a repeated `Finding:` pair, including after
settlement; a withdrawal of an older source does not revoke a later independent
agreement. A same-Bot withdrawal naming only this thread leaves its agreement
ambiguous and blocked. Quoted examples, wrong-source references and unrelated
discussion cannot revoke another finding's evidence. The CLI reads native issue
comments through GitHub on every thread/settle/gate path and reports
`github-issue-comment`, the native comment ID and source URL separately from
`github-review-thread` evidence. Rejection writes recheck the complete native
conversation snapshot and head before replying/resolving; concurrent changes
require re-evaluation. Never repost a Bot transcript to fill this
channel.

The actual published assessment remains evidence if its originating workflow
is subsequently cancelled. This is not an approval, a full review, a completed
review round, or a waiver of any required check: those laws remain separate.

## Candidate informational conversations

The separate [informational disposition](../pr-flow/actionability.md) is a
provisional bounded correction for resolved author-only User explanations with
empty native enclosing reviews. Same-author `Handled`, author identity, a
walkthrough prefix, unknown severity and GitHub resolution do not qualify.
All threads default to finding obligations. Only fresh independently admitted
native evidence with exact complete context may remove that obligation; it is
not settlement or independent rejection and gives no approval/coverage/round
credit. Real defects/questions remain findings and unresolved conversations
still block. Mechanical protocol checks cannot guarantee the assessor's semantic
judgment. Parent owns review/publication and actual fresh native qualification;
no invocation is authorized by the preparation fixtures.

For a review-body item, include its scope on both first lines:
`Rejection proposal for <SHA>: review-id:<review ID> cr-comment:v1:<item ID>`
and `Rejection agreement for <SHA>: review-id:<review ID> cr-comment:v1:<item ID>`.
Use `review-body:<review ID>` for a whole-body request instead. Both messages
include `Reason:` and `Evidence:`. The writer's final itemized reply includes
`review-id:<review ID>` and `- Rejected cr-comment:v1:<item ID>: Reason: …; Evidence: …`,
matching the proposed decision. A thread's agreement cannot be reused for this
separate item.

## Settlement and loop

Every finding gets a reply **before** its thread is resolved:

| Opening | Meaning |
| --- | --- |
| `Fixed in <sha>: …` | changed; cite the test or evidence |
| `Handled: …` | Already addressed, or bound native opener withdrawal above |
| `Deferred to <card-uuid or issue>: …` | justified scoped follow-up only when completed rounds are strictly above the configured minimum (default five); the follow-up exists |
| `Rejected: …` | detailed reasoning and evidence, corroborated by an independent non-CodeRabbit agent |

1. **Wait.** Run `pr.cljs wait REPO N`. Preserve cooldowns and reuse pending
   requests. Rate-limited or skipped output supplies neither a review nor an
   approval.
2. **Triage all reviewers.** Run `pr.cljs threads REPO N`; also read review-body
   findings. Apply the first-five policy above.
3. **Verify fixes.** Run the relevant repository gates; add regression evidence
   when the finding exposes a bug.
4. **Commit and push.** Use explicit paths and ordinary commits.
5. **Settle threads.** Run `pr.cljs settle REPO N THREAD_ID "<Opening> …"`.
   Outdated threads still require verification. The CLI refuses an unsupported
   rejection before replying or resolving.
6. **Answer review-body items.** Use `review-id:<numeric GitHub review ID>` and
   itemized `Fixed`, `Deferred`, `Rejected` or `Handled` bullets naming each
   `cr-comment:v1:<ID>`. Body-only or legacy requests without individual IDs use
   `review-body:<review ID>`; explain every concern and its verification.
   Independent rejection evidence must name that item as well as its head;
   thread-only agreement does not reject an unrelated review-body finding.
7. **Decide.** `law/loop-verdict` converges only after findings are settled and
   the minimum is reached or approval is unanimous. Otherwise continue review.
   Passing five never authorizes an unqualified merge.
8. **Re-request as needed.** Use the same `planning` or `code` kind and the
   configured providers. A sixth, seventh or later review is allowed. Respect
   cooldowns and deduplicate an existing exact-head request. Ask the user only
   for a real unresolved decision, not because a counter reached five.

## Mechanics and reviewer obligations

Use the GraphQL thread ID (`PRRT_…`). `settle` replies with
`addPullRequestReviewThreadReply` and then calls `resolveReviewThread`.
A reviewer who contests a settlement makes it unresolved for the gate even
when GitHub displays the conversation as resolved. Verify and re-settle the
pushback. Quoted or generated examples supply no acceptance verdict.

Coverage and approval are distinct. A full review or exact-head marker can
establish coverage without approval. The default merge quorum after the
minimum is one trusted exact-head formal approval or explicit completed passing
verdict with verified commit coverage from CodeRabbit, Codex, MiMo or Kimi.
All configured reviewers remain invited; every provider's findings remain in
scope. Explicit `--reviewers` and repository requirements still impose all-of
approval. Required deterministic checks and branch protection remain in force.

One coordinator owns pushes, external review requests and settlement replies,
even when parallel agents verify or fix findings.

Availability and its native evidence are defined in [pr-flow](../pr-flow/SKILL.md). Invite all available agents. Retain quota/failed/skipped states truthfully, with reset evidence; the current quota exception never settles a finding or waives required CI.

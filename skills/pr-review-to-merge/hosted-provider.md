# Hosted-provider review to merge

This is the explicitly selected hosted-provider compatibility profile. Return to [the skill entry point](SKILL.md) for profile selection and common requirements.

## Hosted-provider compatibility procedure

1. **Select one active PR.** Inspect live heads, bases, branch rules, required
   checks, review state, and dependency order. Keep later stack layers draft.
   Make the active PR ready for review once its actual prerequisites and base
   integration are present. Parallel workers may prepare independent fixes;
   one coordinator owns branch propagation, external review requests and merge.
   Inspect `autoMergeRequest` and ready-triggered repository automation. If a bot
   queues auto-merge before the review gate, wait for that job to finish, disable
   auto-merge on this PR, and verify it is absent before resolving the final
   conversations. Recheck this after readiness transitions; do not change global
   repository settings or assume a green bot status means its review completed.
2. **Make the active change mergeable.** Reproduce failures and repair code,
   tests, build configuration and integration conflicts within the authorized
   scope. Bring real prerequisites into the active dependency chain or repair
   the split boundary. A green cumulative tree does not prove an earlier layer
   builds. Preserve unrelated work; stage explicit paths, never amend or force
   push, and propagate stack changes with ordinary commits/merges.
3. **Close the review loop.** Read every actionable bot thread, including older
   threads still relevant to the current diff. Fix valid findings and add the
   appropriate regression evidence. Reply in each thread with the fix commit,
   exact tested context and result. If you disagree, explain the code path and
   evidence, request adjudication, and keep it open pending that decision.
   Linking a report to a task is intake, not resolution.
4. **Request current-head follow-up.** Supply an author walkthrough where the
   repository requires it. Invite CodeRabbit, Codex, MiMo and Kimi, and request any additional
   reviewer explicitly required by the active repository policy. Reuse an in-flight
   request for the same head; never duplicate it. A request acknowledgment,
   stale review or rate-limit reply is not completion. After each change,
   keep all available reviewers invited for the latest head and relevant base. One eligible approval satisfies the default quorum; explicit mandatory overrides remain all-of. Pending or rate-limited optional reviewers are not marked green and do not replace approval. Follow `pr-review-settlement` for the soft review minimum, unanimous early exit and independently corroborated rejections. Continue beyond five when findings remain.
5. **Resolve verified threads.** Resolve a finding only after the fix and
   relevant checks demonstrate it is addressed, or the reviewer adjudicates
   the documented disagreement. Reply with evidence before resolving. Do not
   resolve an actionable finding merely because it is outdated, copied to a
   task, or inconvenient. Inspect all conversations again after follow-up.
6. **Recheck the merge gate.** Fetch fresh remote state. Require no actionable
   findings, one eligible trusted exact-head approval or explicit completed passing verdict with verified commit coverage, plus all mandatory reviewer approvals, all
   required checks passing with applicable suites actually exercised, all
   review conversations resolved, branch protection satisfied, and no unresolved
   prerequisite or integration blocker. Missing/pending evidence is not a pass.
   Do not weaken checks, suppress failures, change protection, use administrator
   bypass, or merge an unqualified layer to advance the queue.
7. **Merge the verified head.** Use an API/CLI operation that asserts the exact
   reviewed head SHA and obeys the repository's merge method and protections.
   If the head/base changes or merge is refused, refresh evidence and return to
   the relevant gate. Queuing auto-merge is still pending work: observe the
   actual GitHub merged state and merge commit before declaring completion.
   Check actual PR state during review waits too. If another actor or automation
   merges early, record the missing evidence, finish the outstanding review and
   land any required follow-up before advancing. Do not report that the intended
   review gate passed merely because the PR merged.
8. **Advance the stack.** Record the merged PR and commit, retarget the next
   authorized layer to the correct surviving base, check its actual diff and
   dependencies, propagate changes without rewriting history, and repeat.
   A squash merge can leave predecessor commits in descendant history; repair
   that ancestry/diff with an ordinary integration commit before review.

## Hosted-provider quotas and continuation

A quota, pending CI run, or unavailable reviewer is an observed wait state, not
completion. An optional quota-unavailable agent may leave the current available
cohort under pr-flow; keep proceeding with available agents while preserving all
findings, mandatory reviewers and required checks. Record its evidence, next eligible retry time, and the next action.
Use an available supported continuation mechanism when the user has authorized
continued work; keep its objective **fix, review, verify, merge, and advance**.
Never reduce the objective to collecting review comments or reporting status.
On waking, inspect current state first and resume the owning loop. Back off on
unchanged waits and notify only on meaningful progress, merge, failure or a
required user decision. If no continuation can actually be scheduled, state
that limitation and preserve a resumable handoff; do not promise background work.

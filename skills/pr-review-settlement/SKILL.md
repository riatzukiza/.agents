---
name: pr-review-settlement
description: The review loop of pr-flow, used for both planning and code review. Wait for CodeRabbit and other agents on the exact head, triage every finding by severity (P0–P3), fix, defer, reject, or mark it handled (already correct; P2/P3 only), each with a reply that explains the outcome, resolve the thread, and re-request review until no P0/P1 is unfixed or the loop budget escalates.
license: GPL-3.0-or-later
metadata:
  tier: provisional
  origin: pr-flow skill graph, 2026-10-01
---

# Review settlement

## Use this skill when

- A PR has review comments from CodeRabbit, Codex, Kimi, OpenCode, Claude or humans.
- `pr.cljs status` reports a blocked gate because of threads or review-body items.

## Do not use this skill when

- No review has run on the current head. Run `pr.cljs request` and `wait` first.

## The settlement law

Every comment gets a **reply before it is resolved**. The reply opens with one outcome word, so the result is machine-readable (`law/resolution-of`):

| Opening | Means | Allowed for |
| --- | --- | --- |
| `Fixed in <sha>: …` | changed, with the test or evidence that shows it | all |
| `Handled: …` | already correct, or covered elsewhere; show where | P2, P3 (and review-body items) |
| `Deferred to <card-uuid or issue>: …` | real, but out of scope; the card must exist | P2, P3 |
| `Rejected: <reason>` | wrong or harmful; cite the code path or evidence | P2, P3 |

P0 and P1 must be **fixed**. Only `Fixed` clears them; `Handled`, `Deferred` and `Rejected` do not (`law/unsettled-blockers`). If you believe a P0/P1 finding is wrong, reply with the evidence, leave the thread **open**, and ask the user to adjudicate.

Severity comes from the comment banner:
- 🔴 Critical maps to P0.
- 🟠 Major and ⚠️ Potential issue map to P1.
- 🟡 Minor and 🛠️ Refactor suggestion map to P2.
- 🔵 Trivial and 🧹 Nitpick map to P3.
- An explicit P-label wins over the banner.

## Loop

1. **Wait** with `pr.cljs wait REPO N`. A rate-limited run (exit 3) is not a review: wait out the window and re-request.
2. **Triage** with `pr.cljs threads REPO N`. Group the findings by theme and fix whole batches, so each push answers several threads and you avoid no-op pushes.
3. **Verify each fix** locally with the repository's gate. Add a regression test where the finding was a bug.
4. **Commit and push** with explicit paths and ordinary commits. Never amend or force-push.
5. **Settle** each thread with `pr.cljs settle REPO N THREAD_ID "<Opening> …"`, which replies and then resolves. Outdated threads still need verification first.
6. **Answer review-body items** (nitpicks, outside-diff, duplicates) in a PR comment with `review-id:<numeric GitHub review ID>`. Each bullet starts with `Fixed`, `Deferred`, `Rejected`, or `Handled` and names the finding's `cr-comment:v1:<ID>` marker. P0/P1 items require `Fixed`; a generic reply cannot settle a review.
7. **Re-request** with `pr.cljs request REPO N KIND` on the new head, using the same `planning` or `code` kind as the initial request. Every round counts toward `:review/max-loops` (default 5) for that stage.
8. **Decide** using `law/loop-verdict`:
   - `:converged` (no P0/P1 open) goes to the merge gate.
   - `:iterate` goes back to step 1.
   - `:escalate` stops; summarise the open blockers for the user.

## Mechanics

- Use the thread id (`PRRT_…`) from GraphQL `reviewThreads`. `settle` replies with `addPullRequestReviewThreadReply`, then calls `resolveReviewThread`. The REST equivalent is `POST repos/O/R/pulls/N/comments/<comment-id>/replies`. Note the `N`: `pulls/comments/<id>/replies` is wrong.
- CodeRabbit answers settlement replies: it verifies the claimed commit and either confirms ("✅ Review thread resolved") or pushes back. `law/classify-thread` marks a thread `:contested?` when a reviewer replies after the last settlement without confirming it. The gate then blocks, even if GitHub shows the thread resolved. To re-settle, fix or answer the pushback, then run `settle` again with a new reply; GitHub's `unresolveReviewThread` mutation can reopen the thread first if humans should see it open.
- Settlement replies also show up as "reviews" in the reviews API. Only a full pass (body headed "Actionable comments posted") counts as review coverage of a head.

## Required reviewers

The gate requires a full pass on the exact head from every reviewer in `:review/required` (default `coderabbit`). Pass `--reviewers coderabbit,codex` where the repository also requires Codex (Knoxx does). Codex labels findings with `P1 Badge` and `P2 Badge` images; the severity parser reads them.

## Other agents

Read comments from every reviewer, not just CodeRabbit. When a parallel agent (a subagent or an ultra-code wave) fixes findings, one coordinator still owns pushes, review requests and settlement replies.

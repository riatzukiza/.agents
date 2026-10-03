---
name: pr-flow
description: Entry point for every pull-request interaction. A state machine (flow.edn) that routes muse → epic/stories → PR (ready, auto-merge off) with CodeRabbit planning review → ready cards → red (laws, tests) → green (domain, infra) → bounded code review → an exact-head approval quorum → authorized merge, naming the skill, inputs, outputs and exit for each state, with an nbb CLI for the GitHub mechanics.
license: GPL-3.0-or-later
metadata:
  tier: provisional
  origin: User request 2026-10-01 (foresight session); distilled from Claude Code/OpenCode session logs, receipts and session-mycology spores across ~/spaces.
---

# PR flow

## Use this skill when

- You create, review, reply on, update, or merge **any** pull request.
- The user muses about work that will become cards and PRs.
- You resume PR work from a previous session (run `pr.cljs status` first).

## Do not use this skill when

- The change is not going through a PR. Example: a direct local edit the user asked you not to commit.
- The repository's own AGENTS.md forbids PRs or AI review on that engagement. Client contracts that prohibit AI use win.

## Authority

Routine git and GitHub actions are pre-authorized by the user: commit, push, fetch, pull, branch, opening PRs, commenting, replying, resolving threads, and requesting reviews. Merge and auto-merge require authorization for the active PR or stack, either in the current request or a standing instruction. A review-only, draft-only, or planning-only request does not imply merge authority. When authorized, act without asking again.

Still confirm destructive actions: force-push, history rewrite, branch or tag deletion, and closing someone else's PR. Stage explicit paths and never sweep up unrelated dirt.

## The graph

`flow.edn` is the source of truth. Run `pr.cljs flow` to print it, or `pr.cljs flow <state>` for one state's skill, exit condition, CLI and next states.

| State | Skill | Leaves when |
| --- | --- | --- |
| muse (ambient) | `pr-muse-connect` | intent names an outcome and a card |
| plan | `pr-sprint-planning` | epic + stories on a branch, uuid-linked |
| planning-review | `pr-sprint-planning` + `pr-review-settlement` | PR opened ready with auto-merge off; every planning comment is settled, or the loop budget escalates to the user |
| card-ready | `pr-sprint-planning` | stories are `ready` in Rheos |
| red | `pr-red-green` | new laws and tests fail for the right reason |
| green | `pr-red-green` | domain then infra pass all gates |
| code-review | `pr-review-settlement` | no P0/P1 unfixed; everything else fixed, deferred or rejected with a reason |
| merge-gate | `pr-review-to-merge` + `pr.cljs gate --apply` | ready + auto-merge set on the head that passed |
| merged | `pr-review-to-merge` | merge verified, stack advanced, parent pointers bumped, cards closed via Rheos |
| reflected | `session-mycology` + `receipt-river` | receipt appended; at most one spore incubated |

Card status belongs to Rheos. A state's `:board/expects` only names the transition to perform through `eta-mu kanban …`. Never hand-edit `status:` to move a card.

## CLI

```bash
P="nbb -cp ~/.agents/skills/pr-flow/scripts ~/.agents/skills/pr-flow/scripts/pr.cljs"
$P flow [STATE]
$P status  owner/repo N            # draft, checks, CodeRabbit, threads by severity, gate verdict
$P request owner/repo N planning|code [--note TEXT] [--reviewer coderabbit|codex]
$P wait    owner/repo N [--timeout 1800 --interval 30]   # 0 done, 3 rate-limited, 4 timeout
$P threads owner/repo N [--all]
$P settle  owner/repo N THREAD_ID "Fixed in <sha>: …"    # reply, then resolve; refuses other openings
$P gate    owner/repo N [--apply] [--method merge]   # merge commits by default, never squash unless the repo requires it
```

Laws live in `scripts/pr_flow/law.cljc` (severity, settlement, merge gate, loop budget) and `scripts/pr_flow/flow.cljc` (FSM well-formedness). They are pure `.cljc`, and the CLI is the only effectful layer. Tests from the repository root: `nbb -cp skills/pr-flow/scripts skills/pr-flow/scripts/test_law.cljs`, `test_policy.cljs` and `test_cli.cljs`. The `PR flow laws and CLI` hosted job runs all three on the actual PR head. Local results are preparation, not hosted qualification.

## Hard-won rules

- **gh auth.** A fine-grained `GH_TOKEN` can push but may fail `createPullRequest` with "Resource not accessible by personal access token". The CLI retries with the token unset so gh uses its keyring login. For raw `gh`, use `env -u GH_TOKEN -u GITHUB_TOKEN gh …`.
- **Approval quorum.** One trusted exact-head GitHub `APPROVED` review or explicit completed passing/no-issues verdict from CodeRabbit, Codex, MiMo or Kimi satisfies the default quorum. A COMMENTED state alone, coverage alone, `CHANGES_REQUESTED`, acknowledgements and stale reviews do not qualify. All four remain invited. All findings from every reviewer must be settled; required CI and evidence gates must pass.
- **Rate limits.** Pending, failed, rate-limited or skipped optional reviews stay in those states. They do not block a different valid approving reviewer when the remaining gates pass. A mandatory reviewer or required check is never waived. Requests are manual, deduplicated for the exact head, and delayed until the parsed cooldown expires; an unknown cooldown needs operator attention.
- **Exact head and identity.** Only allowlisted GitHub Bot logins in reviewed `:review/identities` can grant approval. CodeRabbit issue verdicts need an actual completed recent review with no actionable comments and its exact-head coverage marker. Codex issue verdicts need its explicit passing statement and a Reviewed commit marker resolved through GitHub to the full current SHA. The latest decisive state on the exact 40-hex head controls; a later dismissal or request for changes revokes that provider’s approval. A new push invalidates the old head, and a changed head during evidence collection blocks merging. Kimi is invited but needs its verified app identity configured before its approval can count.
- **No-findings reviews.** CodeRabbit may finish a full review without creating a REST review record. An authorized exact-head request marker followed by CodeRabbit's "Full review finished" reply, together with a completed current-head CodeRabbit check, is review evidence. A trigger acknowledgement alone is not.
- **Outdated threads.** An outdated thread still needs verification before you settle it.
- **Review bodies.** Nitpicks and outside-diff findings live in the review body, not in threads. Answer every finding in an itemized PR comment with `review-id:<numeric GitHub review ID>`; each line names its `cr-comment:v1:<ID>` and opens with `Fixed`, `Deferred`, `Rejected`, or `Handled`. A body-only change request or explicitly listed legacy finding without item IDs uses `review-body:<numeric GitHub review ID>` for the whole body, conservatively P1: a `Fixed` bullet must explain every item and its verification. Mentioning a heading with no actual findings creates no phantom obligation. Human change requests stay active across pushes; a later approval or dismissal by that reviewer on the same or current commit supersedes a body-only request. Historical identified/listed findings retain their settlement requirement. The gate accepts only repository-writer settlements and excludes the thread opener from settling their own finding.
- **Review rounds.** Review requests record `pr-flow-stage:planning|code` in a PR comment by a repository writer. The five-round budget applies to the current consecutive stage; earlier planning rounds do not consume code-review rounds. Native no-findings issue completions count even when there is no REST review; representations of the same request count once. Public commenters cannot reset the stage marker. A current-head failed/skipped/cancelled check that completed after a request ends that attempt; a manual retry still needs available budget and an expired known cooldown. Pending and ambiguous attempts remain deduplicated.
- **Mandatory overrides and checks.** `--reviewers coderabbit,codex` requires **both** exact-head approvals in addition to quorum. Repository requirements are unioned with that explicit override. The October 3 user policy applies quorum one to Knoxx as well. A later explicit repository policy or user override can require additional reviewers; the CLI cannot remove such explicit requirements. All deterministic/required check failures or pending states block; skipped or cancelled required checks block. Optional reviewer checks are classified separately.
- **Reviewer reach.** CodeRabbit does not auto-review drafts, or PRs whose base is not the default branch (every stacked PR). Request explicitly. Codex and other agents may ignore drafts entirely (`@codex review` only reaches a ready PR), so when they are required, mark the PR ready and keep auto-merge off until the gate passes.
- **Quota.** CodeRabbit subscriptions cover only the account or org they were bought on, and limits are per developer per hour. Observed 2026-10-01: the personal account `riatzukiza/*` is on Essentials at 5 reviews per hour. The `open-hax` and `octave-commons` orgs are on the free OSS program at 1 review per hour, because OSS limits scale with star count. The footer of each review says how many reviews remain. "Review limit reached" means wait. Never repeat a pending request, and never treat an acknowledgement, a skipped run or a stale review as completion. The CLI uses manual `@coderabbitai full review` with exact-head and reviewer markers, pending deduplication and parsed cooldowns. Reaching the stage budget blocks another request even if earlier findings are settled; changing reviewer does not reset the shared budget. This PR’s historical six rounds do not authorize a seventh.
- **Size caps.** CodeRabbit skips PRs over 100 files, so split them. When a PR passes about 100 comments, merge it with follow-up cards, or close it with remarks.
- **Comment floods.** Docstring and nitpick floods go to issues or cards. Ask the reviewer to file them, grouped, and settle the threads as `Deferred to <issue>`.
- **Merge method.** Use merge commits, not squash (the user's correction). `gate --apply` uses `--merge --match-head-commit <gated head>`.
- **Auto-merge availability.** `allow_auto_merge` and required checks are repository settings. If `--auto` is refused, `gate --apply` merges directly at the gated head. Change repository settings only when the active task or standing instructions authorize it.
- **Read your merges.** After merging or updating from main, look at what the merge brought in before claiming nothing changed.
- **CI tooling.** Check the CI checks too. Missing tools on CI runners (clojure-lsp, clj-kondo, java) have repeatedly turned required checks into silent no-ops or permanent failures. Known traps: `DeLaGuardo/setup-clojure` silently ignores a `clojure-lsp:` input, so install the release binary instead. `clojure-lsp` resolves the classpath through `bb print-deps` whenever `bb.edn` exists, so CI needs `bb` too. When a gate discards a tool's stderr, add a temporary step that runs the tool with visible output.

## References

`pr-muse-connect`, `pr-sprint-planning`, `pr-review-settlement`, `pr-red-green`, `pr-review-to-merge`, `receipt-river`, `session-mycology`, `eta-mu-kanban`, `ultra-code` (review waves), `staging-promotion-gate-navigation`.

## Coverage, approvals and execution boundaries

The CLI preserves native formal approvals and explicit passing verdicts as
distinct evidence channels. CodeRabbit’s authenticated issue-comment coverage
marker alone is observed coverage only; a completed explicit no-actionable
verdict with verified current-commit coverage qualifies under the user’s
October 3 decision. “Full review finished” alone does not qualify and no
synthetic GitHub APPROVED state is created.
Commit binding identifies the reviewed revision; it does not prove every
changed file was available to the reviewer. An explicit admission of incomplete
review or omitted input disqualifies even a formal `APPROVED` review, and a later
such verdict revokes that provider's earlier approval on the same head. Passing
deterministic gates cannot stand in for admitted unreviewed input. Truncation
repaired by retrieving and reviewing the omitted input remains eligible;
approval does not require an exhaustive proof of program correctness. Quoted
examples and generated boilerplate do not supply the current scope verdict.
The CLI reports incomplete evidence separately from observed commit binding.
An incomplete approval also cannot supersede an unsettled body change request.
CodeRabbit and Codex requests use their explicit mention surfaces; MiMo and
Kimi use configured hosted workflows. All remain invited even after quorum.

AgenticKey-authenticated CLI review is a separate provider and evidence artifact.
It cannot impersonate native CodeRabbit approval or satisfy the hosted GitHub
approval quorum. Do not publish a synthetic approving review to fill the quorum.
Preserve named credentials, current-head evidence gates and branch protection;
missing credentials are an operator blocker, not permission to suppress a job.

### Authorized governance cycle and CI reruns

The user authorized a new bounded process/governance cycle on 2026-10-03,
covering quorum policy, exact-head evidence, latest-check selection and Rheos
input permissions. Record its scope and test/review evidence separately from
the historical six-round repair loop. That authorization does not reset the
old loop or permit endless retries of the same findings. New review requests
still need an explicit bounded cycle budget; absent that budget, keep the
historical cap and send no seventh request.

For duplicate checks, select the latest same-head run of each context and
workflow, preserving requiredness across reruns. A later required failure,
cancellation or pending run still blocks; an older failure superseded by the
latest successful run does not. Ambiguous run ordering fails closed. CodeRabbit
issue coverage requires its actual exact-head `final_review_risk_coverage`
marker; even a formal CodeRabbit approval alone does not supply that marker.
The user accepted explicit passing verdicts with verified commit coverage on
2026-10-03; the gate recognizes observed native provider formats, verifies
identity and commit binding, and retains the source channel and record ID.

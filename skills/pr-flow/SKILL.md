---
name: pr-flow
description: Entry point for every pull-request interaction. A state machine (flow.edn) that routes muse → epic/stories → PR (ready, auto-merge off) with CodeRabbit planning review → ready cards → red (laws, tests) → green (domain, infra) → bounded code review → auto-merge, naming the skill, inputs, outputs and exit for each state, with an nbb CLI for the GitHub mechanics.
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
$P request owner/repo N planning|code [--note TEXT]
$P wait    owner/repo N [--timeout 1800 --interval 30]   # 0 done, 3 rate-limited, 4 timeout
$P threads owner/repo N [--all]
$P settle  owner/repo N THREAD_ID "Fixed in <sha>: …"    # reply, then resolve; refuses other openings
$P gate    owner/repo N [--apply] [--method merge]   # merge commits by default, never squash unless the repo requires it
```

Laws live in `scripts/pr_flow/law.cljc` (severity, settlement, merge gate, loop budget) and `scripts/pr_flow/flow.cljc` (FSM well-formedness). They are pure `.cljc`, and the CLI is the only effectful layer. Tests: `nbb -cp scripts scripts/test_law.cljs`.

## Hard-won rules

- **gh auth.** A fine-grained `GH_TOKEN` can push but may fail `createPullRequest` with "Resource not accessible by personal access token". The CLI retries with the token unset so gh uses its keyring login. For raw `gh`, use `env -u GH_TOKEN -u GITHUB_TOKEN gh …`.
- **Rate limits.** A rate-limited or skipped CodeRabbit run is **not** a pass. Wait out the window and re-request; never admin-merge around it.
- **Exact head.** Review evidence belongs to one head SHA. A new push invalidates it, so re-run `wait` and `status`.
- **Outdated threads.** An outdated thread still needs verification before you settle it.
- **Review bodies.** Nitpicks and outside-diff findings live in the review body, not in threads. Answer every flagged review in an itemized PR comment that opens with `Handled:` and includes `review-id:<numeric GitHub review ID>`. A generic answer does not settle subsequent reviews.
- **Reviewer reach.** CodeRabbit does not auto-review drafts, or PRs whose base is not the default branch (every stacked PR). Request explicitly. Codex and other agents may ignore drafts entirely (`@codex review` only reaches a ready PR), so when they are required, mark the PR ready and keep auto-merge off until the gate passes.
- **Quota.** CodeRabbit subscriptions cover only the account or org they were bought on, and limits are per developer per hour. Observed 2026-10-01: the personal account `riatzukiza/*` is on Essentials at 5 reviews per hour. The `open-hax` and `octave-commons` orgs are on the free OSS program at 1 review per hour, because OSS limits scale with star count. The footer of each review says how many reviews remain. "Review limit reached" means wait. Never repeat a pending request, and never treat an acknowledgement, a skipped run or a stale review as completion. For an idempotent retry, use `@coderabbitai full review` with an HTML marker naming the head SHA.
- **Size caps.** CodeRabbit skips PRs over 100 files, so split them. When a PR passes about 100 comments, merge it with follow-up cards, or close it with remarks.
- **Comment floods.** Docstring and nitpick floods go to issues or cards. Ask the reviewer to file them, grouped, and settle the threads as `Deferred to <issue>`.
- **Merge method.** Use merge commits, not squash (the user's correction). `gate --apply` uses `--merge --match-head-commit <gated head>`.
- **Auto-merge availability.** `allow_auto_merge` and required checks are repository settings. If `--auto` is refused, `gate --apply` merges directly at the gated head. Change repository settings only when the active task or standing instructions authorize it.
- **Read your merges.** After merging or updating from main, look at what the merge brought in before claiming nothing changed.
- **CI tooling.** Check the CI checks too. Missing tools on CI runners (clojure-lsp, clj-kondo, java) have repeatedly turned required checks into silent no-ops or permanent failures. Known traps: `DeLaGuardo/setup-clojure` silently ignores a `clojure-lsp:` input, so install the release binary instead. `clojure-lsp` resolves the classpath through `bb print-deps` whenever `bb.edn` exists, so CI needs `bb` too. When a gate discards a tool's stderr, add a temporary step that runs the tool with visible output.

## References

`pr-muse-connect`, `pr-sprint-planning`, `pr-review-settlement`, `pr-red-green`, `pr-review-to-merge`, `receipt-river`, `session-mycology`, `eta-mu-kanban`, `ultra-code` (review waves), `staging-promotion-gate-navigation`.

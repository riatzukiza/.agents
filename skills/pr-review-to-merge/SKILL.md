---
name: pr-review-to-merge
description: Carry an authorized pull request or ordered stack through profile-appropriate review, required checks and verified merge.
license: GPL-3.0-or-later
metadata:
  origin: User-requested workflow extracted from open-hax/knoxx PR 305
---

# PR review to merge

Read [pr-flow](../pr-flow/SKILL.md) first, including on direct invocation, and
use its selected profile. This skill does not itself authorize a merge.

For the default native profile, use [native swarm review](../pr-flow/native-swarm.md).
Independent PRs may proceed concurrently with one coordinator per PR or
coupled stack and one writer per branch/resource. Review the combined result
proportionately and use focused repair checks; at most two review-driven repair
passes apply across the whole task by default, unless the user chooses another
bound. If material findings or required checks remain, stop and report them.
Read actual GitHub requirements, verify current head/base, settle actual findings,
pass required checks and approvals, and merge only with authorization using an
exact-head guard. Local review evidence is not a GitHub approval.

The separate hosted-provider procedure and quota rules apply only to the
**explicitly selected hosted-provider compatibility profile**. Its provider roster, global one-active-PR
rule and five-round logic do not apply to native work. The actual platform's
requirements, publication safeguards and user authorization still apply to both.

Adapted from open-hax/knoxx `.agents/skills/pr-review-to-merge` at `0fdaae13`.

## Use this skill when

- The user authorizes merging a PR or advancing an ordered PR stack.
- The user asks you to resume an already authorized review-and-merge workflow.

## Do not use this skill when

- The request is only to review, split, task out, or prepare draft PRs. Those
  actions do not grant merge authorization.
- The user pauses or cancels merging, or the next PR is outside the authorized scope.

Follow the active harness, user instructions, project AGENTS.md, and applicable
canonical principles. Existing session authorization persists; do not ask again
merely because a turn ended, a reviewer replied, or a quota reset is needed.

For PRs that change deployment qualification, also apply the repository's
promotion workflow. This skill still owns their review and merge; it does not
replace any required deployment qualification or promotion gate.

## Inputs and durable state

Record the authorized scope, repository, ordered PR/task URLs, intended bases,
merge method, and required reviewers. For each PR retain its current head and
base SHAs, current-head review evidence, check results, unresolved thread URLs,
blockers, and the next concrete action. Use the project's existing tracker and
receipt location; do not create a competing queue or record credentials.

## Profile-specific procedure

For native work, follow the profile above and the current pr-flow native swarm
guidance. When the hosted-provider profile is explicitly selected, read the
retained [hosted-provider procedure and quota rules](hosted-provider.md).
Never infer that optional hosted reviewers or their quota state are required by
the native profile. Actual required checks and review requirements still bind.

## Completion and output

An individual merge task is complete only after GitHub confirms that PR merged
and the required review/fix loop is closed, including any early-merge recovery.
An authorized stack is complete only after every in-scope PR merged, unless the
user changes the scope. A draft, review request, green suite, resolved-thread
list, or enabled auto-merge is an intermediate state. Report merged PR/commit
links and the next active PR, or the exact blocker and continuation state.

## Provenance and compatibility

This native skill was explicitly requested during the Knoxx PR #305 decomposition
after review collection had not yet delivered the authorized merge outcome. It
does not grant merge authorization by itself. Its activation/governance data is
in [CONTRACT.edn](CONTRACT.edn).

The canonical local catalog is `~/.agents/skills/pr-review-to-merge/`. A project
may carry a versioned copy for a self-contained checkout; keep that copy equal
to the canonical skill rather than editing two independent policies. OpenCode
may discover a project link under `.opencode/skills/pr-review-to-merge/`; legacy
Pi and Codex discovery directories may link to the canonical catalog. Installation
does not imply that an already-running harness has reloaded its skill list.

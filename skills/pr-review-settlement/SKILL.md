---
name: pr-review-settlement
description: Settle PR review findings using the active pr-flow profile; retain required checks, current-head evidence, and truthful dispositions.
license: GPL-3.0-or-later
metadata:
  tier: provisional
  origin: pr-flow skill graph, 2026-10-01; user review-policy correction, 2026-10-03
---

# Review settlement

Use for PR review findings or review convergence. Do not use for work that has
no pull request or for unrelated implementation tasks.

## Profile selection, including direct invocation

Read [pr-flow](../pr-flow/SKILL.md) first and use its active profile. Directly
invoking this skill does not select the hosted-provider profile.

For the default native profile, follow [native swarm review](../pr-flow/native-swarm.md):
review the integrated result once, fix substantiated findings, and use focused
rechecks rather than restarting a full review for each repair. At most two
review-driven repair passes apply to the entire task by default, even across
replanning or handoffs; a user may explicitly choose another bound. If material
findings or required checks remain unresolved, preserve the work and report the
blocker rather than starting another loop or claiming success. Honor actual
repository requirements, current-head evidence, and previously raised findings.
Local reviewer output is not a GitHub approval. Never bypass protections.

The sections below describe **only** the explicitly selected hosted-provider
compatibility profile and its existing CLI. Their provider roster, rounds,
quorum, first-five rules, and CLI admission mechanics do not become native
requirements just because this skill was loaded. Required platform checks,
authorization, accurate evidence and actual finding dispositions still apply.

## Hosted-provider compatibility profile

Only when that profile is explicitly selected, read the retained
[hosted-provider settlement procedure](hosted-provider.md), including its
provider roster, review rounds, finding dispositions and CLI mechanics. The
native profile does not acquire those review-round rules by invoking this skill.

---
name: ultra-code
description: Evidence-gated dynamic workflows for opencode. EDN-defined orchestration executed out-of-conversation by scripts/ultra.bb with content-hash journaled, resumable subagent fan-out. Use for adversarial review waves, multi-card audits, and any task needing plan -> fan-out -> skeptic-verify -> verdict topology. Self-contained global port of the eta-mu ultracode skill.
---

# ultra-code

Dynamic workflows are DATA, not prompting theater. The durable orchestration plan lives in an EDN workflow file; execution lives in `scripts/ultra.bb` (babashka); every agent dispatch is journaled by content hash so interrupted runs resume exactly where they stopped.

## Layout of this pack

- `scripts/ultra.bb` — the orchestrator (plan/run). Repo-agnostic; invoke from the target repo root.
- `agents/ultra-reviewer.md`, `agents/ultra-skeptic.md`, `agents/ultra-implementer.md` — the opencode agent definitions the workflows dispatch to. Installed globally to `~/.config/opencode/agents/`; the copies here are the canonical source.
- `workflows/template-review.edn` — generic cards × lenses review workflow. Copy it into `<repo>/.ημ/workflows/<id>.edn` and edit.

## Prerequisites

- `bb` (babashka) on PATH.
- `opencode` CLI on PATH; agents dispatch via `opencode run --format json --agent <name>`.
- The three `ultra-*` agents registered in the opencode config scope you run from (they are installed globally; re-copy from `agents/` here if they drift).

## When to use

- Adversarial review of one or more kanban cards / change sets before promotion.
- Any fan-out where findings must survive independent skeptic votes before they count.
- Bounded-write implementation waves with gates (`:implement` stages — see below).

## Running

Run from the target repo root so artifacts land in that repo's `.ημ/`:

```
bb ~/.agents/skills/ultra-code/scripts/ultra.bb plan <workflow.edn>          # dry-run: job inventory + first prompt
bb ~/.agents/skills/ultra-code/scripts/ultra.bb run  <workflow.edn>          # execute (resumes from journal)
bb ~/.agents/skills/ultra-code/scripts/ultra.bb run  <workflow.edn> --fresh  # discard journal, start over
bb ~/.agents/skills/ultra-code/scripts/ultra.bb run  <workflow.edn> --limit=2  # smoke: cap jobs per stage
```

`--limit` truncates each stage's job list, including skeptic votes — with quorum 2 and a limit of 1 no finding can survive. Use it to validate plumbing, never to gate a promotion.

Artifacts land in `.ημ/runs/<workflow-id>/` (relative to CWD): `journal.jsonl` (started/result/error events keyed `v2:<sha256>`) and `<timestamp>-result.edn` (`:confirmed` + `:all` findings). Structured output is extracted from the agent event stream last-text-part-first with a `{...}` substring fallback; agents that produce no parseable JSON are logged as errors and retried on the next invocation.

## Laws

1. Workflow definitions are EDN files — never inline prompts in conversation when a workflow file exists.
2. Every agent dispatch requires a content-hash journal entry; completed jobs are never re-run, errored jobs retry on the next invocation.
3. Findings are claims, not facts. A finding counts only after `:stage/quorum` skeptic votes fail to refute it.
4. Review and skeptic agents are read-only by constitution (see `agents/`). Orchestration never writes production code outside an explicit `:implement` stage's declared packet.
5. Failed agents (limits, timeouts, parse errors) are excluded from quorums and logged — never silently counted as either refuted or confirmed.
6. After a run, report `:confirmed` vs `:all` honestly, and append a receipts.edn entry for the wave.

## Authoring a new workflow

Copy `workflows/template-review.edn` into the target repo and edit:

- `:vars` — the cartesian inputs (cards × lenses, or any item set).
- `:schemas` — JSON Schema for each agent's structured output.
- `:stages` — `:map-agent` (parallel agent per item) and `:vote-fan-out` (N skeptic votes per finding, quorum reduce). Prompt templates interpolate `{card/key}`, `{lens/prompt}`, `{finding/title|40}`, `{vote/n}`, `{repo}`.
- `:run` — model (default `kimi-for-coding/k3`), timeout, max concurrency, and `:dir` (absolute target repo path passed to `opencode run --dir`).

## Stage kinds

- `:map-agent` — fan out one agent job per cartesian item; results feed later stages.
- `:vote-fan-out` — for every finding under `:stage/finding-path` in the `:stage/over` stage's results, dispatch `:stage/votes` skeptic votes; a finding `:survives` when at least `:stage/quorum` votes do not refute it.
- `:implement` — bounded-write implementation of one card with retry-over-gates. Declares `:stage/packet` `{:write [...] :read [...] :forbid [...]}`, `:stage/gates` shell commands, optional `:stage/review` (an inline review+verify wave whose confirmed findings count as gate failures), optional `:stage/commit`. If `:stage/uuid` is set, the script also drives a Rheos-style kanban FSM via `node packages/rheos/dist/cli.cjs status-update` inside `:vars :repo` — omit `:stage/uuid` in repos without that CLI (FSM calls are skipped entirely).
- `:return` — terminal marker.

## Claude session import (optional)

Recovered Claude Code dynamic-workflow sessions are manifest-first: `.ημ/imports/claude/<session-id>/manifest.edn` records sha256-pinned source artifacts (workflow script, run state, journal, transcript) and the translation table into ημ artifacts. The EDN workflow is the executable projection; the raw JS remains the evidence of original intent. Resume is semantic (re-dispatch un-cached jobs), never a claim to attach Claude's runtime.

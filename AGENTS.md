# Agent Instructions

This repository is the canonical source for Err's reusable agent process, skills, contracts, scripts, and harness adapters.

Keep this file short. It is a map, not the encyclopedia.

## Read order

For substantive work in this repository:

1. obey active safety, harness, and user instructions,
2. read [`PRINCIPLE.edn`](PRINCIPLE.edn),
3. read [`PROCESS.md`](PROCESS.md),
4. read [`STYLE.md`](STYLE.md),
5. classify the active environment with `environment-classifier`,
6. read the matching harness adapter,
7. inspect `.ημ/receipts.edn`,
8. load the smallest relevant skill set,
9. read a skill's `CONTRACT.edn` when changing activation or governance.

Harness adapters:

- [`CHATGPT.md`](CHATGPT.md)
- [`CLAUDE.md`](CLAUDE.md)
- [`CODEX.md`](CODEX.md)
- [`OPENCODE.md`](OPENCODE.md)
- [`PERPLEXITY.md`](PERPLEXITY.md)

## Universals

- Preserve user sovereignty and explicit authority.
- Separate observation, interpretation, recommendation, decision, verification, and acceptance.
- Preserve epistemic tiers: `observed -> derived -> provisional -> accepted`.
- Name canonical sources, durable ledgers, and rebuildable projections.
- Prefer append-only correction over rewritten history.
- Keep work bounded, reviewable, and honestly stateful.
- Define contracts and failure shapes before dependent adapters.
- Isolate foreign/runtime boundaries.
- Run checks relevant to the touched responsibility.
- Record unavailable capabilities and unperformed checks.
- Learn from repeated friction through Receipt River and Session Mycology.

## Process data

The preferred project-local home for operational process data is `.ημ/`.

New receipts, board event ledgers, runs, sessions, mycology records, config, plugins, and projections should converge there when the project adopts them.

Legacy paths such as `.eta-mu/`, root `receipts.edn`, and `kanban/.events/ledger.edn` are evidence. Preserve and migrate them deliberately; never delete history merely to normalize layout.

No repository is required to install eta-mu, Rheos, Muse, Katamorph, event-ledger, a daemon, database, hook, or MCP server. Use `process-bootstrap` to select the smallest useful layer.

## Core skills

- `environment-classifier` — establish actual harness capabilities first.
- `process-bootstrap` — initialize or converge the portable process without requiring tools.
- `receipt-river` — append execution truth for non-trivial work.
- `session-mycology` — record friction and incubate reusable lessons.
- `eta-mu-kanban` — operate Rheos/eta-mu boards lawfully and migrate legacy ledgers.
- `fork-tax` — use only when Π, fork tax, full dump, snapshot, or deterministic handoff is explicitly invoked.
- `grok-intention` — recover dense intent from prompt, repository, notes, and history.
- `sing-the-songs-of-your-people` — produce truthful corpus-native synthesis.
- `skill-authoring` — create or materially revise reusable skills.

Explicit invocation wins. Otherwise activate only when gates match.

## Skill changes

When adding or revising a skill:

1. read `skills/skill-authoring/SKILL.md`,
2. search for overlap,
3. keep activation and anti-activation explicit,
4. keep steps executable and capability-adaptive,
5. reference global rules instead of duplicating them,
6. preserve provenance,
7. update contracts and indexes that would drift,
8. append receipts and mycology state.

Do not promote a mycology spore in the same session that created it. Direct user requests to create a skill are ordinary authorized skill work, not automatic spore promotion.

## Repository work

- Assume concurrent work unless exclusivity is explicit.
- Use branches and pull requests for governance changes.
- Scope mutations to owned paths.
- Never use destructive repo-wide cleanup against unrelated dirt.
- Re-read touched files.
- Verify Markdown links, skill frontmatter, folder/name agreement, s-expression balance, and canonical uncertainty code points.
- Run scripts or parsers only when the environment actually supports them.
- Record skipped checks and blockers.
- Do not create Π tags or full handoff manifests unless Fork Tax is invoked.

## Handoff

Report:

- recovered intent,
- files and authority boundaries changed,
- why the change is safe,
- verification performed or unavailable,
- receipt/mycology artifacts,
- branch, commit, and pull-request state,
- and exactly one next action.

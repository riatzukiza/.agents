---
name: process-bootstrap
description: Bootstrap the portable .agents process in a repository using the smallest capability-appropriate layer, with optional eta-mu, Rheos, Muse, and event-ledger setup when requested.
license: LGPL-3.0-or-later
metadata:
  audience: agents
  workflow: process-bootstrap
  version: "1"
---

# Skill: Process Bootstrap

## Goal

Make a repository able to respect the shared process without requiring any particular agent harness or eta-mu tool.

## Use This Skill When

- A repository lacks process instructions, Receipt River, Session Mycology, or a declared board contract.
- The user asks to initialize `.ημ/`, eta-mu, Rheos, Muse, or cross-harness agent support.
- An existing repository has scattered process artifacts that need a non-destructive convergence plan.
- A harness needs guidance for using the process with limited capabilities.

## Do Not Use This Skill When

- The repository already has a working declared process and the task does not change it.
- The user asks only for analysis and does not authorize repository mutation.
- Setup would overwrite existing instructions, ledgers, board state, or generated host configuration.

## Inputs

- Active environment classification.
- Applicable `AGENTS.md`, process/style docs, ADRs, task sources, CI, and scripts.
- Existing `.ημ/`, `.eta-mu/`, root receipts, board ledgers, skill folders, and harness config.
- The user's requested level of automation.

## Invariants

- The file-only process is complete enough to stand alone.
- Tools automate the process; they do not become constitutional authority.
- Existing history is preserved.
- Generated host files remain projections.
- Secrets never enter instructions, receipts, spores, or committed config.
- Do not install software, start services, alter hooks, or mutate global config unless the task explicitly includes setup and the environment permits it.

## Bootstrap levels

### Level 0 — inventory

Record:

- instruction files and authority order,
- task/board sources,
- receipt and event ledgers,
- session/mycology records,
- static/test gates,
- harness adapters,
- generated projections,
- legacy paths and drift,
- and available tools.

### Level 1 — file-only substrate

Add only missing pieces:

```text
AGENTS.md                 # short project map
.ημ/receipts.edn          # append-only execution truth
.ημ/session-mycology/
  ledger.md
  spores/
```

Link to global `PROCESS.md` and `STYLE.md` when `~/.agents` is available. Otherwise vendor a clearly identified snapshot or include repository-local equivalents.

A board is optional. Do not invent one for trivial or stable reference repositories without a work-management need.

### Level 2 — helper scripts

Use small scripts for append, tail, validate, snapshot, or migration when manual formatting is repetitive or risky.

Prefer Babashka for portable EDN workflows when it is available, but keep the file format usable without it.

### Level 3 — Rheos/eta-mu

When requested:

- detect an existing eta-mu/Rheos installation before installing,
- follow the active repository's build/install instructions,
- project existing task documents rather than replacing them,
- configure lawful transition operations,
- place new operational ledgers under `.ημ/`,
- and preserve legacy board ledgers for replay/migration.

Use `eta-mu-kanban`.

### Level 4 — Muse adapters

When cross-harness tools, hooks, permissions, or MCP surfaces are needed:

- author host-agnostic resources/config,
- compile host-native artifacts,
- verify parity across selected targets,
- and avoid hand-editing generated outputs.

Muse is a compatibility compiler, not a new harness or semantic owner.

### Level 5 — runtime services

Add event-ledger, databases, daemons, dashboards, or watchers only when:

- authority and ownership are declared,
- operational value exceeds complexity,
- failure and recovery are observable,
- and a file-only degraded path remains understandable.

## Migration procedure

For any legacy process path:

1. inventory source and consumers,
2. classify canonical versus projection data,
3. preserve hashes/counts and timestamps,
4. define the target path and schema,
5. copy or replay without rewriting the source,
6. compare parity,
7. switch writers,
8. observe,
9. freeze or archive the legacy writer,
10. record rollback.

Never rename a ledger and call that a migration.

## Harness adapters

Use the matching root guide:

- `CHATGPT.md`
- `CLAUDE.md`
- `CODEX.md`
- `OPENCODE.md`
- `PERPLEXITY.md`

When the harness is not listed, use the environment classification plus `PROCESS.md`.

## Output

- environment classification,
- inventory of existing process artifacts,
- selected bootstrap level,
- files/config/services changed,
- preservation and migration notes,
- verification performed,
- and one explicit next operational step.

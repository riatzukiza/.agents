---
name: eta-mu-kanban
description: Operate a Rheos/eta-mu markdown board through the strongest lawful CLI, MCP, connector, or file-only surface; preserve append-only provenance and migrate legacy board ledgers toward .ημ/.
license: LGPL-3.0-or-later
metadata:
  audience: agents
  workflow: rheos-kanban
  version: "2"
---

# Skill: Rheos / eta-mu Kanban

## Goal

Manage a markdown-backed board as lawful, ledger-backed motion rather than arbitrary frontmatter edits, while remaining usable when Rheos or eta-mu is not installed.

## Use This Skill When

- Listing, searching, reading, sizing, creating, or moving work items.
- Recording progress or decisions on a task.
- Working under a project's board/FSM contract.
- Bootstrapping Rheos or eta-mu board support.
- Migrating `kanban/.events/ledger.edn` or another legacy board ledger into `.ημ/`.
- Reconciling CLI, UI, MCP, task files, and generated board projections.

## Do Not Use This Skill When

- The project has no declared board and the task does not require one.
- The user is asking about an unrelated external board service.
- A status mutation would bypass the declared FSM, WIP limit, dependency, review, or completion gate.
- Only generated `board.json` is available and the task source cannot be identified.

## Authority

Resolve board authority in this order:

1. project constitution/process and approved decisions,
2. project board contract (`AGENTS.md`, process policy, config),
3. task source documents plus authoritative board event ledger,
4. Rheos transition law,
5. generated snapshots/UI/search projections.

A card never overrides architecture decisions. A generated board never overrides task source or ledger history.

## Environment first

Run `environment-classifier`.

Select the strongest available surface:

1. **Rheos CLI** (`rheos ...`)
2. **eta-mu compatibility CLI** (`eta-mu kanban ...`)
3. **Rheos MCP/agent tools**
4. **repository connector with project-declared mutation semantics**
5. **file-only read/proposal mode**

Skill availability does not prove the required executable or permission exists.

## Discovery

Locate:

- board config (`openhax.kanban.json`, `kanban.json`, or project declaration),
- task roots such as `docs/kanban`, `kanban/tasks`, or `docs/agile/tasks`,
- project FSM and completion gates,
- authoritative board ledger,
- generated snapshots,
- available `rheos` or `eta-mu` binaries,
- and legacy versus target process paths.

Useful commands when available:

```bash
command -v rheos || true
command -v eta-mu || true
git rev-parse --show-toplevel
```

Do not install or build tooling unless the task includes setup.

## Canonical data target

The preferred default for new project-local board operational history is:

```text
.ημ/
├── ledgers/
│   └── rheos.edn
├── runs/
│   └── rheos/
└── projections/
    └── rheos/
        └── board.json
```

A project may declare a different `.ημ/` path. Follow it.

Task Markdown may remain in the project's existing human-facing board directory. The requirement is that new operational ledgers and run state converge under `.ημ/`, not that every task document be hidden there.

Recognize these as legacy evidence, not garbage:

- `kanban/.events/ledger.edn`
- root or package-local event logs
- `.eta-mu/`
- tool-specific board databases
- historical `board.json`
- direct task-file transition comments

## Lawful operations

### Read

With Rheos:

```bash
rheos read-board --project <id>
rheos read-task <task-uuid> --project <id>
rheos search-tasks --query "<text>"
rheos events [task-uuid] --limit <n>
rheos drift
```

Compatibility surface:

```bash
eta-mu kanban list --tasks-dir <path>
eta-mu kanban count --tasks-dir <path>
eta-mu kanban find <slug-or-id> --tasks-dir <path>
eta-mu kanban search "<text>" --tasks-dir <path>
eta-mu kanban content <id> --tasks-dir <path>
```

Command names vary by installed revision. Read `--help`; do not invent a command merely because an older skill listed it.

### Mutate

Prefer the shared dispatch surface so CLI, MCP, and UI apply the same law:

```bash
rheos move <task-uuid> --to <status> --project <id>
rheos status-update <task-uuid> --to <status> --project <id>
rheos add-comment <task-uuid> --text "<note>" --project <id>
rheos create-subtask <parent-uuid> --title "<title>" --project <id>
```

Compatibility commands may include:

```bash
eta-mu kanban comment <id> "<note>" --tasks-dir <path>
eta-mu kanban frontmatter <id> status <status> --tasks-dir <path>
```

Use compatibility frontmatter mutation only when that installed revision routes it through the project FSM and ledger. If it is a raw file edit, do not use it for status transitions.

### File-only fallback

When no lawful mutation surface exists:

- read task files and board policy,
- produce the proposed transition/comment/subtask,
- append an observation or blocker receipt,
- and leave status unchanged.

Directly editing status to bypass missing tooling is not a fallback.

A project may explicitly authorize append-only comments in task Markdown. Follow its declared format and never rewrite prior comments.

## Default workflow

1. Read board policy and environment classification.
2. Inspect counts, ready work, dependencies, WIP, and drift.
3. Select or create a bounded card.
4. Record scope and expected evidence.
5. Move through lawful transitions one hop at a time.
6. Append progress, anomalies, decisions, and verification.
7. Regenerate projections through the tool.
8. Complete only after relevant gates and acceptance are recorded.
9. Append Receipt River and Session Mycology state.

Project policy wins over the following common defaults:

- work starts in `icebox` or `incoming`,
- work is refined through `accepted` and `breakdown`,
- `ready` requires explicit acceptance criteria and dependencies,
- cards larger than the project's ready threshold are split,
- `in_progress`, `review`, and `done` are gated,
- generated snapshots are never hand-edited.

## Migration from legacy ledger paths

Do not move the file and declare victory.

1. Identify current writers/readers and schema.
2. Freeze no writer yet; inventory counts, last sequence/time, hashes, and task IDs.
3. Declare the target `.ημ/ledgers/...` path.
4. Copy or replay events while preserving original identity, ordering, causality, and timestamps.
5. Point a test instance or adapter at the target.
6. Rebuild the board projection from both sources and compare.
7. Switch writers.
8. Observe for divergence.
9. Freeze/archive the legacy writer but retain the original ledger.
10. Record cutover and rollback receipts.

When event-ledger is adopted, conform to its envelope/idempotency/causality laws instead of inventing a parallel event format in this skill.

## Setup guidance

If the project requests Rheos/eta-mu setup:

1. use `process-bootstrap`,
2. inspect the repository's active eta-mu build/install instructions,
3. build or install only the needed package,
4. configure the existing task root,
5. declare the FSM and ledger target,
6. test read, lawful transition, event append, drift detection, and projection rebuild,
7. document the degraded file-only path.

Do not make the repository dependent on a global daemon merely to read its tasks.

## Output

- environment and chosen board surface,
- board/task/ledger authority map,
- operations performed or proposed,
- exact transition/check results,
- legacy drift or migration state,
- and remaining blockers.

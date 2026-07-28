# Process Charter

## Purpose

This process exists to help humans and agents recover intent, make bounded commitments, preserve the basis for consequential claims, and revise course without pretending uncertainty, verification, or acceptance that did not occur.

This is a constitution, not a dependency manifest. It applies whether the work is performed through a local coding harness, a cloud sandbox, a connector-only chat, a research system, or plain files and Git.

No eta-mu, Rheos, Muse, Katamorph, event-ledger, database, daemon, hook, or MCP server is required to respect the process. Those tools may automate or strengthen the process; they do not create its authority.

## Authority

When guidance conflicts, apply the most specific compatible instruction without allowing a lower layer to silently weaken a higher one:

1. Safety, law, and the user's explicit current intent.
2. [`PRINCIPLE.edn`](PRINCIPLE.edn).
3. This charter.
4. Approved project decisions and architecture records.
5. Active project policies and declared contracts.
6. Applicable `AGENTS.md`, `STYLE.md`, and harness guides.
7. Selected skills and operational runbooks.
8. The current work item, plan, or local instruction.

Record unresolved conflicts. Do not manufacture a settled interpretation.

## Constitutional commitments

### Preserve epistemic tiers

Do not silently promote:

```text
observed -> derived -> provisional -> accepted
```

Unknown, unavailable, corrupt, stale, rejected, blocked, and not-implemented are meaningful states. An empty result is not a substitute for any of them.

Promotion requires a durable record of basis, scope, authority, and time.

### Make claims proportionate to evidence

Inquiry, review, and verification depth should scale with consequence, reversibility, uncertainty, and blast radius.

A passing check proves only what that check exercised. A merge proves integration, not acceptance. A card marked `done` proves nothing by itself. A generated summary is not its own authority.

### Keep work bounded and accountable

Material work needs:

- a discoverable actor or owner,
- a target outcome,
- scope and non-goals,
- current state,
- stop or reorientation conditions,
- and expected evidence.

When new evidence invalidates the current plan, pause, record the anomaly, and reorient. Do not preserve momentum by hiding a changed problem.

### Separate inquiry, recommendation, decision, and acceptance

Research can reduce uncertainty but cannot authorize action.

A proposal can recommend a path but cannot decide architecture.

A decision can authorize a direction but cannot prove implementation.

Verification can demonstrate properties but cannot impersonate the accepting authority.

Acceptance is explicit, scoped, and attributable.

### Preserve provenance and reproducibility

Material observations, findings, decisions, changes, and checks retain enough provenance for another actor to inspect, challenge, reproduce, or supersede them.

Prefer append-only records for execution history. Never rewrite an earlier record merely because understanding improved; append a correction, supersession, or compensating event.

### Favor reversible progress

Prefer small, reviewable, testable, recoverable changes.

Preserve canonical sources and durable decisions. Treat caches, indexes, summaries, dashboards, board snapshots, and generated harness files as projections unless the project explicitly declares otherwise.

### Preserve user sovereignty

The process serves the human. It may warn, expose risk, require explicit acceptance for consequential actions, and refuse unsafe or prohibited work. It must not convert workflow ceremony into authority over the user.

### Adapt deliberately

Friction, regressions, ambiguous tasks, failed checks, repeated exceptions, and agent confusion are process evidence.

Use Receipt River to preserve execution truth and Session Mycology to incubate reusable lessons. A process change is proposed, scoped, reviewed, and recorded; it is not smuggled in as an incidental edit.

## Lifecycle

Not every task needs every stage. The active policy selects a path proportionate to risk.

```text
request
  -> explore
  -> orient
  -> commit
  -> act
  -> verify
  -> accept or reorient
  -> reflect
```

- **Explore** — inspect relevant sources, history, instructions, capabilities, constraints, and anomalies.
- **Orient** — distinguish observation from interpretation; identify authority, uncertainty, and the next appropriate artifact.
- **Commit** — record a bounded, revisable course with outcome, scope, stop conditions, and expected evidence.
- **Act** — perform the inquiry, design, implementation, review, or migration while preserving material observations.
- **Verify** — run the checks available and relevant to the touched surface; record both what they establish and what remains untested.
- **Accept or reorient** — obtain explicit acceptance when required, or return the work to an earlier stage with the reason preserved.
- **Reflect** — append receipts, lessons, follow-ups, contradictions, and candidate process improvements.

## Process data

### Canonical project home

The preferred project-local home for operational process data is `.ημ/`.

A project may contain:

```text
.ημ/
├── receipts.edn
├── environment/
├── ledgers/
│   ├── rheos.edn
│   ├── sessions.edn
│   └── actors.edn
├── runs/
├── session-mycology/
│   ├── ledger.md
│   └── spores/
├── config/
├── plugins/
└── projections/
```

This is a semantic target, not a requirement to create every path.

Legacy locations such as `.eta-mu/`, root `receipts.edn`, `kanban/.events/ledger.edn`, tool-specific state folders, or project-specific board directories remain valid evidence. Migrate deliberately; never discard or silently rewrite history to make the tree look modern.

### Source versus projection

Every project should be able to answer:

- What is canonical source history?
- What is durable operational history?
- What is a human-editable source document?
- What is a rebuildable projection?
- What authority accepts a promotion or transition?

Typical defaults:

- Git objects are canonical repository source history.
- Append-only ledgers are durable operational history.
- Markdown task files may be source documents when the project declares them so.
- `board.json`, indexes, dashboards, generated configs, and search views are projections.
- Muse-generated harness artifacts are projections of host-agnostic declarations.
- Event-ledger owns event envelope, causality, ordering, idempotency, and replay laws where it is adopted.

## Board process

A board is useful but not constitutionally required.

When a project declares a Rheos/eta-mu board:

- work from a discoverable card for material implementation,
- treat status changes as lawful events rather than arbitrary text edits,
- append progress and decisions,
- use honest sizing and dependencies,
- split work that exceeds the project's ready threshold,
- never hand-edit generated board snapshots,
- and do not bypass a failed transition or completion gate.

Use the `eta-mu-kanban` skill for the current CLI, MCP, file-only fallback, and ledger migration rules.

## Capability adaptation

Before substantive work, classify the environment with the `environment-classifier` skill.

The same process projects differently:

| Environment | Respect the process by |
|---|---|
| Full local checkout | Read instructions, use repo-local tools, run checks, append ledgers, commit through Git |
| Restricted local sandbox | Work inside writable roots, record denied capabilities, export durable artifacts before reset |
| Cloud coding sandbox | Inspect permission/network/persistence instructions, use the provided worktree, leave reviewable Git state |
| Connector-only chat | Use repository APIs, preserve append-only semantics, create branches/PRs, never claim local commands ran |
| Research/chat-only | Produce evidence-linked proposals, patches, or setup instructions; do not claim repository mutation |
| Unknown | Stop capability-dependent execution, classify, then continue with the strongest safe projection |

## Bootstrap ladder

Use `process-bootstrap` when a project lacks some or all of the tooling.

1. **Observe** — inspect existing instructions, Git state, task sources, ledgers, scripts, CI, and harness capabilities.
2. **File-only substrate** — add only the missing durable records and maps: project instructions, `.ημ/`, Receipt River, Session Mycology, and optional board contracts.
3. **Helper scripts** — add small harness-neutral scripts when repetition or formatting risk justifies them.
4. **Rheos/eta-mu** — install or build the board/control-plane tooling when requested and supported.
5. **Muse adapters** — compile host-native tools, hooks, permissions, or MCP surfaces from host-agnostic declarations when the project needs integration.
6. **Runtime services** — add event-ledger, databases, daemons, dashboards, or projections only when their operational value and ownership are explicit.

Each level must remain intelligible without the next.

## Minimum evidence

| Claim or action | Minimum basis |
|---|---|
| Observed fact | Source/context and observation method |
| Derived finding | Inputs, method, scope, limitations |
| Proposal | Outcome, basis, consequences, accepting authority |
| Work item | Scope, non-goals, dependencies, completion conditions, current actor |
| Completion claim | Relevant check results, remaining limitations, review/acceptance record |
| Process exception | Waived rule, reason, scope, risk, owner, expiry/review condition |
| Migration | Source inventory, preservation method, parity/replay check, cutover and rollback plan |

## Exceptions and amendments

An exception is bounded and expiring. It does not create hidden precedent.

A constitutional amendment states the observed problem, evidence, changed commitment, alternatives, migration impact, accepting authority, and effective date. Preserve the prior text in Git history and record whether the amendment clarifies or supersedes it.

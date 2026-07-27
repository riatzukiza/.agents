# The Weave

## Intent

The repositories are not a scattered pile of conventions. They are multiple projections of one evolving operating process.

The weave does not choose one repository and copy it everywhere. It recovers recurring structure, names authority boundaries, preserves historical divergence, and places the portable grammar in `~/.agents`.

## Source roles

### Epiphany — constitutional clarity

Epiphany contributes the strongest current separation between constitution, policy, operational guide, work item, evidence, decision, verification, and acceptance.

Portable lessons:

- preserve epistemic tiers,
- make authority order explicit,
- scale evidence to consequence,
- distinguish source from rebuildable projection,
- record unavailable/unknown instead of manufacturing emptiness,
- and treat reflection as part of responsible work.

### Truth — enforcement scars

Truth demonstrates why principles need mechanical floors.

Portable lessons:

- warnings can be failed contracts,
- architecture invariants can be executable checks,
- regressions should recover prior design evidence before rewriting,
- local skills capture repeated domain traps,
- and a `done` card is false when its gates have regressed.

Truth's simulation-specific ECS laws remain local.

### eta-mu — control plane and bootstrap

eta-mu contributes:

- environment detection,
- project initialization,
- the `.ημ/` process-data home,
- Sol/session integration,
- Rheos board operations,
- Receipt River,
- Session Mycology,
- and the ambition to connect Git, sessions, events, tasks, and harnesses without hiding their boundaries.

eta-mu is not required for the process. It is the preferred bootstrap/control-plane implementation when available.

### Rheos — lawful motion

Rheos contributes a board as an FSM with append-only provenance and shared CLI/UI/MCP transition law.

Portable lessons:

- work state is an event, not arbitrary frontmatter,
- generated boards are projections,
- progress is appended,
- transitions have gates,
- tasks are honestly sized and decomposed,
- and all host surfaces should agree on legal motion.

Current repositories still contain legacy ledger locations. The target is project process data under `.ημ/`, with preservation and replay rather than destructive relocation.

### Muse — compatibility compiler

Muse contributes the distinction between host-agnostic declaration and host-native artifact.

Portable lessons:

- author capabilities, implementations, exposures, permissions, and profiles as data,
- compile adapters for OpenCode, Claude, MCP, and future harnesses,
- keep generated host files rebuildable,
- and do not let the compiler claim semantic ownership of the systems it exposes.

### Katamorph — data as interpreter

Katamorph contributes executable resource grammar:

- declarations are plain EDN,
- kind-specific contracts validate them,
- interpreters realize facets,
- and registries/references remain explicit.

Portable lesson: use declarative data when a reusable grammar exists; do not duplicate policy across host edges.

### event-ledger — operational truth

event-ledger contributes:

- append-only envelopes,
- stable event identity,
- causal roots and parents,
- monotonic ordering,
- idempotent append,
- principal attribution,
- and replay/watch semantics.

Portable lesson: ledgers preserve facts and causality; they do not decide identity, authorization, domain meaning, or acceptance.

### Maintained reference systems

- **UXX** — one canonical implementation with parity projections for multiple frameworks.
- **Knoxx** — explicit extern boundaries and thin host adapters; a working composition should remain stable while its lessons move upstream.
- **OpenPlanner** — mechanical review and test floors; projections should not become memory authority.
- **Proxx** — declarative policy sources interpreted by CLJS, with mechanical guards against policy leaking into TypeScript.

These repositories are references and stable products, not migration playgrounds.

### Promethean — the sacrificed titan

Promethean is most valuable as lineage.

Its present tree contains many superseded implementations, but its recurring commitments birthed the current system:

- owner sovereignty,
- AI as compiler of intent,
- learn once and reuse,
- failed attempts as learning evidence,
- human final authority,
- traversable documentation,
- modular intent,
- and Fork Tax as durable snapshots.

Promethean should be read chronologically with Epiphany's epistemic discipline. Current state alone is an incomplete witness.

## Common grammar

Across the corpus:

```text
intent
  -> discovery
  -> explicit description/specification
  -> contract
  -> pure shape
  -> foreign boundary
  -> domain decision
  -> effectful composition
  -> verification
  -> durable evidence
  -> acceptance
  -> reflection
```

And:

```text
source declarations/history
  -> interpreter or compiler
  -> host/runtime adapter
  -> rebuildable projection
```

And:

```text
work
  -> receipt
  -> mycology observation
  -> incubating spore
  -> later review
  -> skill
```

## What is universal

- user sovereignty and explicit authority,
- facts versus interpretations versus narratives,
- epistemic tiers,
- provenance and append-only correction,
- bounded work and honest state,
- source/projection distinction,
- contracts before adapters,
- boundary isolation,
- relevant mechanical checks,
- capability honesty,
- targeted skill discovery,
- and deliberate learning.

## What remains local

- exact language and dependency choices,
- exact namespace names,
- board columns and WIP limits,
- database/backend selection,
- deployment topology,
- product-specific invariants,
- exact test commands,
- and whether a project adopts eta-mu, Muse, Rheos, Katamorph, or event-ledger.

## Target topology

```text
~/.agents/
├── PRINCIPLE.edn
├── AGENTS.md
├── PROCESS.md
├── STYLE.md
├── CHATGPT.md
├── CLAUDE.md
├── CODEX.md
├── OPENCODE.md
├── PERPLEXITY.md
└── skills/

<project>/
├── AGENTS.md
├── project decisions, designs, tasks, and source
├── .agents/skills/          # project-specific skills when needed
└── .ημ/
    ├── receipts.edn
    ├── ledgers/
    ├── runs/
    ├── session-mycology/
    ├── config/
    ├── plugins/
    └── projections/
```

The global repository teaches the grammar. Each project declares its own authority, commands, adapters, and invariants.

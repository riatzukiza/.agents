# GLOSSARY

## Domain terms

- **skill** — a reusable, folder-scoped agent capability: `skills/<name>/SKILL.md` (human-operational) plus optional `CONTRACT.edn` (machine-readable) and supporting scripts/references.
- **spore** — an incubating skill candidate in `.ημ/session-mycology/spores/`, grown from repeated friction. Spores promote only via review in a later session.
- **CONTRACT.edn** — the machine-readable activation/governance contract for a skill: priority, triggers, effects, non-override layers.
- **skills.disabled/** — the device-side parking lot for skills no harness currently loads. Parking, not deletion.
- **harness** — any agent runtime (OpenCode, Claude Code, connectors, …) that discovers and loads skills.
- **launcher** — the harness entry point that resolves instructions (AGENTS.md chain, skill discovery paths) before a turn begins.
- **canonical root** — `~/.agents`: the single source of truth for skills, whether a harness reads it directly, via symlink, or via a connector.
- **receipt-river** — the append-only `receipts.edn` ledger discipline: evidence of observations, decisions, tests, and push-truth, never rewritten.
- **session-mycology** — the post-work practice of scoring friction and incubating at most one reusable spore when a pattern generalizes.
- **fork-tax** — Π-mode persistence: a deterministic commit + tag + push + manifest handoff, only when explicitly invoked.
- **presence (主)** — the context symbol for the attention anchor: whoever/whatever currently holds the focus of the exchange. Distinct from 己 (self), 汝 (user), 彼 (third parties), 世 (world).
- **grok-intention** — the skill of recovering dense, symbolic, or underspecified intent from prompt + repo + prior evidence before asking the user to repeat themselves.
- **model routing** — choosing the right model per task type (e.g. implementation vs interpretation) instead of defaulting to one model for everything.

## Mythical / named classes

- **Rheos** — the kanban FSM: the flow-control law for card states and transitions.
- **epiphany** — the knowledge-archaeology product/charter that PROCESS.md here is modeled on.
- **muse** — the compatibility compiler: turns EDN/CLJC plugin data into tools for every harness.
- **Prometheus** — the org mythos of open-hax/promethean: fire-stealing infrastructure work, done in the open.
- **eta-mu** — the agent runtime: η (eta) = transduction, μ (mu) = evaluation; the pair that turns input into assessed output.
- **Π (pi)** — fork-tax/persistence: the operator that demands the repository absorb the work, truthfully.
- **α (alpha)** — structural integrity: the invariant-keeper class; what refuses to let the frame silently deform.

## Clojure (for the pre-Lisp reader)

- **homoiconicity** — code and data are the same stuff: a program is just a data structure you can also treat as data. This is why CONTRACTs are `.edn` — a contract is data a machine can read, diff, and score without a special compiler.
- **EDN** — Extensible Data Notation: the plain-text serialization of Clojure data (like JSON, but with keywords, symbols, and lists). `{:kind :decision}` is a map with keyword keys.
- **atom** — a single mutable reference cell inside an otherwise immutable world. You swap a function over it, and updates are atomic — no torn writes.
- **transducer** — a composable transformation of values-one-at-a-time (map/filter fused into one pass). Think of a conveyor-belt stage you can bolt onto any stream.
- **laziness** — sequences compute their elements only when asked. Infinite or huge pipelines stay cheap until you actually pull from them.
- **macro** — code that writes code at compile time. You program the language itself instead of waiting for a framework.
- **protocol / multimethod** — polymorphism as data: protocols group functions over types; multimethods dispatch on any function of the arguments, not just inheritance.
- **REPL-driven development** — you keep a live session open, evaluate expressions against real running state, and grow the program interactively instead of write-compile-run cycles.
- **babashka (bb)** — a fast-starting Clojure interpreter for scripts and CLIs; the reason `scripts/*.bb` helpers ship harness-neutral inside skills.

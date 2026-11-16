# GLOSSARY — the skill catalog's named things

Written for a competent stranger who is about to meet a filesystem full of Greek letters and proper nouns that sound like weather. Scope: this catalog (`~/.agents`) — machine-wide jargon lives in each device's home glossary.

## Part I — Domain terms

- **skill** — a reusable, folder-scoped agent capability: `skills/<name>/SKILL.md` (human-operational) plus optional `CONTRACT.edn` (machine-readable) and supporting scripts/references.
- **spore** — an incubating skill candidate in `.ημ/session-mycology/spores/`, grown from repeated friction. Spores promote only via review in a later session.
- **CONTRACT.edn** — the machine-readable activation/governance contract for a skill: priority, triggers, effects, non-override layers.
- **skills.disabled/** — a device-side parking lot for skills no harness currently loads (present on some device branches, e.g. `device/yoga`; not present in this `device/stealth` checkout). Parking, not deletion.
- **harness** — any agent runtime (OpenCode, Claude Code, connectors, …) that discovers and loads skills.
- **launcher** — the harness entry point that resolves instructions (AGENTS.md chain, skill discovery paths) before a turn begins.
- **canonical root** — `~/.agents`: the single source of truth for skills, whether a harness reads it directly, via symlink, or via a connector.
- **receipt-river** — the append-only `receipts.edn` ledger discipline: evidence of observations, decisions, tests, and push-truth, never rewritten.
- **session-mycology** — the post-work practice of scoring friction and incubating at most one reusable spore when a pattern generalizes.
- **fork-tax** — Π-mode persistence: a deterministic commit + tag + push + manifest handoff, only when explicitly invoked.
- **grok-intention** — the skill of recovering dense, symbolic, or underspecified intent from prompt + repo + prior evidence before asking the user to repeat themselves.
- **presence (主)** — the context symbol for the attention anchor: whoever/whatever currently holds the focus of the exchange. Distinct from 己 (self), 汝 (user), 彼 (third parties), 世 (world).
- **graded uncertainty (ლა / לா)** — the global contract's uncertainty operators: `ლა` (soft) = unresolved but recoverable from context; `לா` (hard) = tokenization is suspect, do not automate on this claim.
- **model routing** — choosing the right model per task type (e.g. implementation vs interpretation) instead of defaulting to one model for everything.
- **device branch** — the per-machine branch of record for a repo in the federation (this host: `device/stealth`). Device branches carry machine truth (state sections, parking lots, device deltas) on top of canonical `main`.
- **superproject / gitlink** — a superproject is a git repo that records other repos as submodules; a **gitlink** is the bare SHA tree entry it records for a child. Wiring, not content.

## Part II — Named things

These are products, services, and systems referenced by the catalog. They are real artifacts with real repositories; the mythology is naming convention, not fiction.

- **epiphany** — the knowledge-archaeology product (`spaces/foresight/epiphany`): mines Git history for the history of ideas. Its `PROCESS.md` is the constitutional model PROCESS.md here imitates. Its one domain rule — *never silently turn similarity into identity* — is quoted whenever data promotion is discussed.
- **eta-mu (ημ)** — the agent runtime and workflow system, source of the `.ημ/` runtime-state directories this repo uses for receipts (`receipts.edn`) and mycology spores. The Greek letters are load-bearing: **η** (eta) is transduction — consuming an artifact, producing another; **μ** (mu) is evaluation — scoring, judging, correcting.
- **muse** — the compatibility compiler: turns EDN/CLJC plugin data into tools for every harness.
- **Prometheus / promethean** — the org mythos and its agent-host runtime ecosystem.
- **Rheos** — the kanban FSM: the flow-control law for card states and transitions (related `kanban-*` skills live in this catalog).
- **Π (Big Pi)** — fork-tax/persistence: the operator that demands the repository absorb the work, truthfully. Π-tags mark deterministic handoff snapshots.
- **α (alpha)** — structural integrity: "is the thing we're about to use well-formed before it is used" — schemas, deterministic checks, canonical identity. The invariant-keeper posture `CONTRACT.edn` files encode.

## Part III — Clojure, in plain language

Clojure is a Lisp; these are the concepts that trip up experienced non-Lisp programmers, with the honest version. They matter here because contracts and ledgers are data files.

- **homoiconicity** — "code is data." A Clojure program is itself a Clojure data structure: a list `(f x)` is first a list of two symbols and only by convention an invocation. This is why contracts can be data: a `CONTRACT.edn` file *is* a structure the runtime can interpret.
- **EDN** (Extensible Data Notation) — the serialization Clojure uses: like JSON but with keywords (`:like-this`), symbols, and lists. EDN is data-only (no functions), so it is the lingua franca for ledgers (`receipts.edn`), contracts, and config here.
- **atom** — not "a tiny thing": the atomic mutable reference cell in an otherwise immutable world. `(swap! a f)` updates it safely under concurrency. State is isolated in these; everything else is data.
- **transducer** — a composable transformation *recipe*, independent of source or sink: `(map f)` alone is a function that, given a reducing step, returns a new one. One recipe, reusable over vectors, streams, channels.
- **laziness** — most sequences are computed on demand and memoized. Powerful, occasionally surprising: a `map` that "didn't run" usually means nobody consumed it.
- **macro** — a function that runs at compile time to transform code (which, per homoiconicity, is data). You program the language itself instead of waiting for a framework.
- **protocol / multimethod** — polymorphism that works on data: protocols dispatch on type-ish structures, multimethods on any function of the arguments. Open extension without inheritance.
- **REPL-driven development** — you build the program *in* the running process, one form at a time, rather than compile-run-repeat.
- **babashka (`bb`)** — a fast-starting Clojure interpreter for scripts and CLIs; the reason `scripts/*.bb` helpers ship harness-neutral inside skills.

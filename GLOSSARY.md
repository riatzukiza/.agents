# Glossary — the catalog's named things

Written for a competent stranger meeting this repository for the first time.
Mirrors the structure of the machine glossary (`/home/err/GLOSSARY.md` on
yoga): mythical names, process lore, Clojure in plain language, jargon.

## Part I — Named things

- **`~/.agents`** — this repository: the canonical, cross-harness catalog of
  agent skills, contracts, scripts, and the global principle contract.
  Cloned at `~/.agents` on every device; harnesses point their skill
  discovery at `~/.agents/skills` rather than copying it.
- **PRINCIPLE.edn** — the global intent contract at the repo root: mission,
  directives, operator grammar, uncertainty operators, output shape, safety,
  licensing. Skills may specialize it; nothing may silently override it.
- **epiphany** — the knowledge-archaeology product
  (`spaces/foresight/epiphany`). Its `PROCESS.md` is the constitutional
  model this catalog's `PROCESS.md` miniaturizes. Domain rule: *never
  silently turn similarity into identity*.
- **muse** — the compatibility compiler for agent tooling; compiles plugin
  definitions for multiple harnesses. Related skills live here, but session
  semantics remain the property of the eta-mu sources.
- **`.ημ/`** — the eta-mu runtime-state directory: `receipts.edn`,
  `session-mycology/` (ledger + spores), handoff state.
- **`.skill-lock.json`** — provenance and version metadata for imported
  skills, at `skills/.skill-lock.json`.

## Part II — Process lore

- **receipt-river** — the append-only `receipts.edn` ledger practice. Every
  substantive turn appends a receipt (what, evidence, time, origin). Never
  rewritten; the river only flows.
- **session-mycology** — turning a hard turn's reusable friction into an
  incubating **spore** (a draft skill with scope and promotion gates).
  Spores are promoted only after review, never in the session that created
  them.
- **fork-tax (Π)** — the persistence ritual: commit + tag + push + manifest
  when a fork of effort is made, so the next handoff starts from truth.
  Invoked explicitly, never by default.
- **operator grammar (η μ Π A)** — the contract's modes: η minimal
  executable delivery, μ formal mode, Π fork-tax mode, A art mode.
  Standalone tokens in a prompt activate the mode.
- **graded uncertainty (ლა / לா)** — `ლა` (soft): unresolved but likely
  recoverable from context; `לா` (hard): structurally suspect, do not
  automate on this claim.
- **context symbols (己 汝 彼 世 主)** — who/where an observation speaks
  from: self, you, third parties, external world, presence.
- **CONTRACT.edn** — a skill's machine-readable declaration of activation
  and governance. Present on 78 of the catalog's 121 skills.

## Part III — Clojure, in plain language

- **homoiconicity** — "code is data": a program is itself a data structure.
  This is why a `CONTRACT.edn` file *is* an interpretable structure.
- **EDN** — Clojure's data-only serialization: like JSON but with keywords
  (`:like-this`) and symbols. The lingua franca for ledgers and contracts.
- **keyword** — an EDN/CLJS value that names itself: `:name` is both the
  label and the value.
- **atom** — the atomic mutable reference cell in an otherwise immutable
  world; `(swap! a f)` updates it safely under concurrency.
- **transducer** — a composable transformation recipe independent of source
  or sink: `(map f)` is a function that, given a reducing step, returns a
  new one.
- **macro** — a compile-time function that transforms code (which is data);
  the door DSLs walk through.
- **babashka (`bb`)** — a fast-starting Clojure interpreter for scripting;
  the machine's `bb.edn` is its Makefile. The catalog's shipped scripts are
  bb-first.
- **clj-kondo** — the Clojure static analyzer. Zero warnings is the
  machine-wide law.
- **`.cljc`** — conditional source valid on both JVM Clojure and
  ClojureScript, with reader conditionals at the seams.

## Part IV — Jargon

- **device branch** — the per-machine branch of record for this repo
  (`device/knoxx`, `device/yoga`, `device/stealth`), verified against
  `origin/main` rather than reset. Device deltas (e.g. `skills.disabled/`)
  live only on device branches that need them.
- **gitlink** — the tree entry a superproject records for a submodule: a
  bare SHA pointing into the child's history. Wiring, not content; it
  advances by explicit commits.
- **skill spore** — an incubating draft skill under `.ημ/session-mycology/
  spores/`, awaiting recurrence and review before promotion.
- **promotion** — moving a reviewed spore into `skills/<name>/` with
  provenance preserved.

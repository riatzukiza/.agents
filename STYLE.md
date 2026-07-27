# Construction Kernel

## Purpose

This document captures the common construction style that recurs across Epiphany, Truth, eta-mu, Muse, Katamorph, Knoxx, Proxx, and related systems.

It is a semantic kernel, not a demand that every repository use Clojure, Malli, or these exact namespace names. Projects may map the responsibilities into their own language and directory structure, but they should not collapse the distinctions invisibly.

`PROCESS.md` governs claims, authority, evidence, and acceptance. This kernel governs how intent becomes bounded system behavior.

## Construction form

```clojure
(η Discovery (-> (-> Describe specify define) (-> μ shape extern domain infra)) Π)
```

- **`η`** — effects, reports, contradictions, existing behavior, history, and anomalies whose cause or meaning is not yet settled.
- **Discovery** — inventories the field without silently converting observations into explanations.
- **Describe** — states what the system is trying to cause in reviewer-readable prose.
- **specify** — makes success, failure, non-goals, and exit evidence observable.
- **define** — declares admissibility, schemas, operations, errors, and transition laws.
- **`μ`** — available capability whose limits or meaning are not yet sufficiently bounded.
- **shape** — pure transformations between declared data shapes.
- **extern** — foreign runtime, SDK, network, filesystem, process, database, or host boundaries.
- **domain** — pure meaning and decisions over trusted shapes.
- **infra** — effect orchestration, adapter composition, retries, scheduling, persistence, and configuration.
- **`Π`** — the integrated product: declarations, implementation, verification evidence, and repository state considered together.

The form is recursive. Every step can reveal new `η`.

## Categories and contracts

A **category** describes the space of meaningful kinds and transformations.

A **contract** decides whether a particular value, event, transition, or action is admissible under current obligations.

Do not substitute one for the other:

- a keyword can classify without validating,
- a schema can validate shape without deciding meaning,
- a passing contract does not accept an interpretation,
- a capability does not become a domain concept until its boundary and meaning are explicit.

## Source, interpreter, and projection

Prefer clear authority splits:

```text
declaration/source -> interpreter/compiler -> runtime adapter -> projection
```

Examples in the current ecosystem:

- Katamorph interprets EDN resources as executable declarations.
- Muse compiles host-agnostic resources into OpenCode, Claude, MCP, and other host artifacts.
- Rheos interprets task and transition laws into board operations and projections.
- event-ledger preserves causal operational events.
- Epiphany observes source history and produces rebuildable retrieval/lineage projections.
- UXX keeps one canonical component implementation and projects parity bindings.

Rules:

- Generated artifacts do not become authority merely because a host consumes them.
- Adapters do not acquire ownership of the semantics they transport.
- A projection must be rebuildable or explicitly promoted into a new source.
- More-specific declarations may override earlier defaults only through a declared merge/order law.
- Preserve exact observed identifiers and paths when identity matters.

## Layer responsibilities

| Layer | Owns | Must not hide |
|---|---|---|
| `law` | schemas, predicates, operation declarations, error/absence shapes, transition guards | I/O or domain decisions |
| `shape` | parsing, encoding, normalization, enrichment, loss accounting | I/O, hidden coercion, acceptance |
| `extern` | foreign invocation and immediate decoding | raw host values leaking upward |
| `domain` | pure meaning, categories, transitions, selection, policy | I/O and adapter selection |
| `infra` | ports, adapters, configuration, persistence, retries, scheduling | invented domain meaning |
| application boundary | named use-case coordination | becoming a junk-drawer second domain |

The names can differ. The responsibilities may not disappear.

## Law before adapter

For a new persisted record, public operation, foreign-boundary value, event, transition, or consequential claim:

1. describe the intent,
2. specify observable obligations,
3. define the contract and failure shapes,
4. then build the adapter or orchestration.

Absence and failure should be explicit when they matter:

```text
unknown
unavailable
invalid
corrupt
stale
rejected
blocked
not-implemented
```

Do not use `nil`, an empty collection, a generic boolean, or swallowed exception to erase a distinction a user or retry policy needs.

## Data as interpreter

When behavior is policy-like, compositional, or expected to vary independently of the host edge, prefer declarative data plus a bounded interpreter over duplicated imperative branches.

Keep:

- facts before derived rules,
- specific clauses before catch-alls,
- declarations versioned and validated,
- host edges thin,
- interpreter behavior tested,
- and policy boundaries mechanically enforced where practical.

Do not force data-driven design onto simple one-off logic. Use it where the grammar is genuinely reusable.

## Boundary discipline

Foreign representations are facts about another system, not internal domain data.

A boundary should:

1. invoke or receive the foreign capability,
2. capture relevant context and failure information,
3. decode into declared plain data,
4. validate or return an explicit boundary failure.

Domain code should not learn SDK object models, HTTP response quirks, database cursor shapes, browser globals, or connector payload accidents.

## The anomaly rule

A surprise is new `η`, not friction to absorb silently.

Record:

- location and observed effect,
- source or reproduction,
- whether it is reusable shape, contradiction, missing law, boundary behavior, or unresolved domain meaning,
- whether it invalidates the current description, specification, contract, or plan,
- and the next move.

If the anomaly invalidates the target, return to Describe/specify/define. Do not bury it in infrastructure to preserve momentum.

## One path over parallel realities

Prefer one canonical substrate or route for each semantic responsibility.

When a feature does not fit:

- reshape it to use the common mechanism,
- or deliberately evolve the common mechanism.

Do not create an undocumented side channel, shadow policy engine, second source of status, duplicate world model, or special-case adapter that forks reality.

Compatibility paths are allowed when they are named, bounded, tested, and have an ownership/migration story.

## Verification

Warnings are failed contracts when the project declares a zero-warning gate.

Select checks by the changed responsibility:

- laws: valid/invalid instances, transition and error cases,
- shapes: properties, edge cases, preservation and loss,
- externs: decoding, translated failures, version compatibility,
- domain: pure decisions and state transitions,
- infra: integration, configuration, retries, observable failure,
- projections: rebuild and parity against source,
- migrations: replay, counts/hashes, cutover, rollback.

A green suite is evidence for the suite run. It does not erase an unmodeled boundary or unsupported claim.

## Documentation

Optimize for the next actor's working memory:

- keep root instructions short and navigational,
- make deeper docs traversable,
- link decisions, designs, research, tasks, and checks,
- name canonical sources and generated projections,
- preserve superseded records,
- and arrest ambiguity where it is discovered.

## Change practice

- survey before editing,
- make the smallest compatible realization,
- preserve unrelated concurrent work,
- prefer additive migration over destructive replacement,
- verify touched surfaces,
- append receipts,
- reflect through Session Mycology,
- and invoke Fork Tax only when explicitly requested.

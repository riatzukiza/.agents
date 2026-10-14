---
name: receipt-river
description: Use an append-only receipts.edn (Receipt River) to externalize execution state; tail it regularly during work.
---

# Skill: Receipt River (append-only receipts.edn)

## Goal
Make long agent runs smarter and more recoverable by maintaining an **append-only** `receipts.edn` ledger.

## Canonical file
The canonical receipt file is `receipts.edn` in the project root. If the project keeps meta-state under `.ημ/`, the bb scripts will prefer `.ημ/receipts.edn` when it already exists, but the default bootstrap location remains the project root for backward compatibility.

## Canonical line format
- required keys: `ts kind origin owner dod pi host manifest refs`
- optional keys: `note tests decisions drift`

## Use This Skill When
- You are doing multi-step work (refactor, PR, migration, Π).
- You need traceability of decisions/tests/builds.

## Standard: segmented, merge-validated ledgers
Receipt River ledgers are stored as **immutable segments**, not one shared
append file. This lets forks, branches and parallel agents diverge and merge
without ledger conflicts.

Scope: this applies to Receipt River receipts now. Other event ledgers adopt it
only once every reader and writer of that ledger is partition-aware (Clio
segment directories); do not split a ledger whose consumers read one file.

### Modes
- **Legacy mode** (`.ημ/receipts/` absent): the single `receipts.edn` is the
  ledger; the bb scripts below and the append-only rules apply unchanged.
- **Entering segmented mode**: in its own PR, identify the **actual** canonical
  legacy file first (`rr-init.bb` defaults to `<project>/receipts.edn`; prefer
  `.ημ/receipts.edn` only when it already exists), freeze that file in place
  (do not invent `.ημ/receipts.edn` if the ledger lives at the project root),
  create `.ημ/receipts/` with a `README.md` describing the convention and naming
  the frozen legacy path, and add the repository's merged-ledger check (see
  `riatzukiza/rheos` or `riatzukiza/foresight`). The merged check must bind that
  same legacy path.
- **Segmented mode** (`.ημ/receipts/` exists): every new record goes in its own
  segment file `.ημ/receipts/<UTC timestamp>-<id>.edn` (e.g.
  `2026-10-13T164500123Z-3f9a1c2b7d4e.edn`) holding exactly one EDN record,
  newline-terminated. **Do not append to the frozen legacy file and do not use
  any of `rr-append.bb`, `rr-tail.bb`, or `rr-last.bb`** until those scripts
  gain a union-aware reader (they still resolve only the monolith and would
  silently omit segment history). Write new segments by hand or via Clio once
  `append-segment` / directory canonicalize ship (see Clio note below).

### Rules in segmented mode
- The frozen legacy file (whichever path was canonical at migration) is the
  **first segment**: keep it, never rewrite it, never append to it.
- Never edit, rename or delete a committed segment. Corrections are new records.
- Validity is judged on the **merged ledger**: load the legacy file plus every
  segment and validate the union — no base segment changed or removed, no base
  record dropped, no record duplicated, every new record valid.
- Record identity:
  - `#:event` / Clio records: use `:event/id` (a reused ID with different data
    is corruption, matching Clio's laws). Content hashing must not override ID.
  - Legacy ID-less `:ts` receipts: SHA-256 of the record's canonical EDN form
    (map entries and set members sorted).
- Order: until receipts are Clio events, there is no canonical order; the
  repository verifiers sort only to make validation deterministic, and that
  sort is not a recency order. For display and context recovery ("the last
  ~20 records"), order by the record's **effective time**: `:ts` when present,
  otherwise `:event/recorded-at` (mixed ledgers hold `#:event` records that have
  no top-level `:ts`), then record identity as the tie-break. Records with
  neither field sort first and must not displace timestamped records from the
  newest window. Once wrapped as Clio events, canonical order is Clio's
  causal/stream order (`:event/causes`, then
  `[:event/stream :event/seq :event/id]`); timestamps never carry ordering
  authority.
- **Clio today** (`open-hax/clio` main): the shipped CLI is `clio append` and
  `clio canonicalize` over ledger files. Segment helpers
  (`append-segment` / `canonicalize-dir`) are proposed on `riatzukiza/clio#5`
  and are **not** available until that lands and is promoted. Until then, write
  segment files directly and run the repository's merged-ledger verifier
  (foresight `verify-receipts` or rheos `bb test:receipts`). Clio has no git
  dependency: the check that committed segments were never edited or removed
  belongs to eta-mu's receipt/ledger commands once those exist.
- When receipt-river moves onto Clio, wrap legacy receipts as Clio events with
  **writer/branch-specific streams** (not one stream per repository). A single
  shared stream recreates concurrent-write conflicts on `[:event/stream
  :event/seq]`; partition by writer or branch, or document a serial allocator.

## Rules (both modes)
These rules and the minimal workflow below apply in both modes. In segmented
mode (`.ημ/receipts/` exists) the section above takes precedence for the
mechanics: every "append" means "write a new segment file", "tail" means "read
the newest ~20 records of the merged ledger by effective time", and none of
`rr-append.bb` / `rr-tail.bb` / `rr-last.bb` may be used until they are
union-aware.

- Legacy mode: if `receipts.edn` exists, **never edit past lines**; if missing,
  create it at the start of non-trivial work.
- Segmented mode: never edit, rename or delete a committed segment or the
  frozen legacy file.
- Check it regularly: regain the newest ~20 records before major decisions.
- Never log secrets (tokens, Authorization headers, private keys).

## Minimal workflow
1. Append `:observation` at start of work.
2. Append `:test-run` / `:build` after verification.
3. Append `:decision` when you choose a path.
4. Append `:push-truth` / `:catalog` at handoff.

## Canonical line format
Each line is EDN with these keys:
- `:ts` — ISO-8601 timestamp
- `:kind` — `:observation`, `:test-run`, `:build`, `:decision`, `:push-truth`, `:catalog`, etc.
- `:origin` — path or task reference
- `:owner` — agent/user identifier
- `:dod` — definition of done or acceptance criteria affected
- `:pi` — related pi/interpreter context
- `:host` — host environment
- `:manifest` — list of changed/created files
- `:refs` — related commit hashes, issue numbers, or session ids
- `:note` — human-readable summary
- `:tests` — test command(s) and result summary
- `:decisions` — decision records
- `:drift` — observed deviation from plan

In legacy mode, append the EDN line directly to `receipts.edn`; in segmented mode, write a new segment file instead.

## bb scripts
The skill ships with Babashka scripts in `scripts/` for harness-agnostic interaction.

- `rr-init.bb` — create an empty `receipts.edn` (project root by default). Use `--eta-mu` to place it under `.ημ/receipts.edn`.
- `rr-append.bb` — append one EDN receipt line to the monolith (**legacy mode only**).
- `rr-tail.bb` / `rr-last.bb` — read the monolith (**legacy mode only** until a union-aware reader exists).

In **segmented mode**, do not use `rr-append.bb`, `rr-tail.bb`, or `rr-last.bb`;
they omit `.ημ/receipts/` history. Prefer the repository verifier / Clio once
segment commands ship.

Examples:

```bash
# from inside the project
~/.agents/skills/receipt-river/scripts/rr-init.bb
~/.agents/skills/receipt-river/scripts/rr-append.bb \
  --kind :observation \
  --origin "session-mycology-refactor" \
  --note "Starting refactor of session-mycology to project-local .ημ/ paths"
~/.agents/skills/receipt-river/scripts/rr-append.bb \
  --kind :test-run \
  --origin "session-mycology-refactor" \
  --tests "bb script smoke tests in /tmp/opencode/sm-test" \
  --note "All four sm-*.bb and four rr-*.bb scripts parse and run"
~/.agents/skills/receipt-river/scripts/rr-tail.bb --limit 10
~/.agents/skills/receipt-river/scripts/rr-last.bb --kind :test-run
```

## Project discovery
The bb scripts walk up from cwd looking for `.ημ/` or `.git/` to identify the project root. Run them from anywhere inside the project.

## Migration note
Legacy `receipts.log` files (pipe-delimited) are not written by these scripts. New projects should use `receipts.edn`. Existing `receipts.log` files may be read manually or converted to EDN; do not mix formats in the same file.

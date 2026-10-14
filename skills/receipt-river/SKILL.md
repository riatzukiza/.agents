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
  any of `rr-init.bb`, `rr-append.bb`, `rr-tail.bb`, or `rr-last.bb`** until those scripts
  gain a union-aware reader (they still resolve only the monolith and would
  silently omit segment history; `rr-init.bb --eta-mu` would create an empty
  `.ημ/receipts.edn` that they then prefer over a frozen project-root ledger). The same holds for the `receipt_river`
  runtime tool, which operates on the per-repo `receipts.edn`: do not use it in
  segmented mode until it accepts segment targets, and never point it at the
  frozen legacy file. Write new segments by hand or via Clio once
  `append-segment` / directory canonicalize ship (see Clio note below).

### Rules in segmented mode
- The frozen legacy file (whichever path was canonical at migration) is the
  **first segment**: keep it, never rewrite it, never append to it. After the
  all-Clio cutover (below) it becomes an archived source outside the active
  union, still byte-for-byte unchanged.
- Never edit, rename or delete a committed segment. Corrections are new records.
- Validity is judged on the **merged ledger**: load the legacy file plus every
  segment and validate the union — no base segment changed or removed, no base
  record dropped, no record duplicated, every new record valid.
- Grandfathered legacy records: records in the frozen legacy file are checked
  only for EDN parsing, record identity (no duplicates) and byte-for-byte
  immutability, **not** for the record shape below. Historical receipts may
  predate today's required keys (this repository's `.ημ/receipts.edn` lines 8–9
  lack `:dod` and `:pi`) and freezing forbids repairing them in place; correct
  such a record with a new `:adjudication` record instead.
- Record shape (what "every new record valid" checks on **active** `.ημ/receipts/`
  segments; cutover wraps of legacy/plain/envelope sources stay grandfathered for
  payload keys, but must still be well-formed full Clio envelopes):
  - Plain receipts: a flat map holding the required keys above.
  - Lightweight Receipt River envelopes (`#:event` with `:event/type
    :receipt-river/receipt-recorded` and `:event/recorded-at` / `:event/payload`):
    the envelope needs `:event/id`, `:event/type`, `:event/recorded-at` and
    `:event/payload`, and the receipt required keys are validated on
    `:event/payload`. Valid **before cutover** (and as pre-cutover sources).
  - Full Clio events: valid **before cutover** too (the mixed ledger may already
    hold them; cutover promotes them verbatim), and **required during/after
    cutover** for new writes and for verbatim-promoted or cutover-wrapped
    records: `:event/id`, `:event/schema`, `:event/type
    :receipt-river/receipt-recorded`, `:event/stream`, `:event/seq`,
    `:event/causes`, `:event/actor`, `:event/subject`, `:event/at`, and
    `:event/data` (receipt under `:receipt-river/receipt`). Do **not** require the
    lightweight `:event/recorded-at` / `:event/payload` keys on full Clio events.
    For every **newly written** full Clio event (before or after cutover) the
    receipt required keys above are validated on `(:receipt-river/receipt
    (:event/data e))`; only cutover wraps keep that map grandfathered like their
    source record.
  - Any other `:event/type` is invalid in a receipt ledger.
  - `CONTRACT.edn` `event-envelope` (pre-cutover) and `full-clio-event` /
    `clio-cutover` (cutover/post-cutover) state the same rules for machines.
- Record identity:
  - `#:event` / Clio records: use `:event/id` (a reused ID with different data
    is corruption, matching Clio's laws). Content hashing must not override ID.
  - ID-less plain receipts (legacy file **or** a plain segment): SHA-256 of the
    record's **canonical EDN bytes**, defined as:
    1. Parse the record to EDN data.
    2. Canonicalize bottom-up: first compute the canonical text of every map
       key, map value and set member (recursively, by this same rule), **then**
       sort. A map emits `{k v k v}` with entries sorted by the canonical text of
       the key; a set emits `#{m m}` (it stays a set, never a vector) with members
       sorted by their canonical text; vectors `[...]` and lists `(...)` keep
       their order and bracket type; tagged literals emit `#tag <canonical-value>`;
       numbers use Clojure/`pr-str` defaults (no custom exponent forms);
       strings/chars/keywords/symbols/booleans/nil use standard `pr-str`. Sorting
       compares canonical texts as Unicode code-point strings.
    3. Join tokens with single spaces (no pretty-print newlines, no comma
       separators), so `#{:a :b}` and `[:a :b]` stay distinct bytes.
    4. UTF-8 encode that string; SHA-256 the bytes; hex-digest in lowercase with
       no separators.
    Independent verifiers must match these bytes. The same rule applies to every
    ID-less plain receipt so duplicate detection, mixed-ledger tie-breaking, and
    cutover UUID derivation stay consistent across sources.
- Order: one authoritative order governs every merged ledger that still holds
  any non-Clio record — a legacy-only ledger, or the migration state where frozen
  legacy records and Clio event segments coexist. In that state there is no
  cross-kind precedence: interleave all records, legacy and `#:event` alike, by
  the record's **effective time** — first present of `:ts`, then
  `:event/recorded-at` (lightweight envelopes), then `:event/at` (full Clio
  events; they have neither `:ts` nor `:event/recorded-at`) — then record
  identity (above) as the tie-break. Records with no effective-time field sort
  first and must not displace timestamped records from the newest window. This
  same order is used for display and context recovery ("the newest ~20 records")
  **while any non-Clio record remains in the active union**. The
  repository verifiers sort only to make validation deterministic; that sort is
  not a recency order. Clio order becomes canonical only once **every** record
  in the active merged ledger is a Clio event, i.e. after the cutover below;
  from then on timestamps never carry ordering authority. Clio order is a
  deterministic **topological** order of the causal DAG (every event in
  `:event/causes` precedes its effects); `[:event/stream :event/seq :event/id]`
  only breaks ties between causally incomparable events. It is not a lexical
  sort with `:event/causes` as a key.
- Cutover to all-Clio order (the reachable transition), in one dedicated PR:
  - Convert **every active non-Clio record** — frozen legacy lines **and** any
    plain **or** lightweight Receipt-River-envelope segments already under
    `.ημ/receipts/` — into a canonical Clio event in its own segment. Leaving
    those sources in the active union would keep `every active record is a Clio
    event` false.
  - Each wrap is a **full Clio envelope** (not the lighter Receipt River
    `:event/recorded-at` / `:event/payload` shape used during mixed-ledger
    migration). Required fields match [Clio's event shape](https://github.com/open-hax/clio#event-shape):
    `:event/id`, `:event/schema` (`:schema/root`, `:schema/id`, `:schema/hash`
    for `:receipt-river/receipt-recorded`), `:event/type
    :receipt-river/receipt-recorded`, `:event/stream`, `:event/seq`,
    `:event/causes`, `:event/actor` (cutover actor, e.g. `receipt-river/cutover`),
    `:event/subject` (stable receipt subject, e.g. `receipt:<record-identity>`),
    `:event/at` (source `:ts` when present, else `:event/recorded-at` for
    lightweight envelopes, else the cutover time), and `:event/data` holding the
    source receipt map verbatim under `:receipt-river/receipt` (for a lightweight
    envelope source, the payload map; for a plain receipt, the receipt itself).
  - `:event/id` rules:
    - A source that is **already** a Clio event (has `:event/id` and the full
      Clio envelope) is **promoted verbatim** — keep its `:event/id` and do not
      re-wrap, so `:event/causes` edges that name it stay resolvable.
    - A **lightweight Receipt River envelope** whose `:event/id` is a UUID
      (string or `#uuid`, any letter case) keeps that ID, written in canonical
      lowercase hyphenated spelling, so `:event/causes` edges from promoted Clio
      events that name it stay resolvable. The archived source is outside the
      active union, so the shared ID is not a reuse.
    - Every other source gets a deterministic **UUID version 5** (RFC 9562
      §6.5 / RFC 4122 name-based SHA-1) whose namespace UUID is the Receipt
      River cutover namespace `6ba7b810-9dad-11d1-80b4-00c04fd430c8` (DNS
      namespace). Name bytes depend on the source identity kind:
      - **ID-less plain receipt**: UTF-8 encoding of the lowercase hex SHA-256
        canonical-EDN identity (above), no separators.
      - **Lightweight envelope with a non-UUID `:event/id`** (e.g. `"evt-1"`):
        UTF-8 encoding of `receipt-river/event-id:` followed by the canonical
        EDN text (above) of the `:event/id` value. The prefix keeps these names
        disjoint from 64-hex plain identities. Do **not** re-hash the envelope
        into SHA-256 for the name input.
      Implementations must reproduce this exact ID; "name-based UUID" alone is
      not enough.
    - Causal edges: a verbatim-promoted Clio event keeps its `:event/causes`
      byte-for-byte. If any of those causes names a non-UUID envelope ID (whose
      wrap gets a new UUIDv5) or no active event at all, the cutover **fails
      closed**: verbatim promotion and remapping cannot both hold, so stop and
      record the conflict for an explicit decision instead of rewriting.
  - All new wraps go in one cutover-only stream `<repo>/legacy`. `:event/seq`
    is the wrap's **one-based ordinal among the wraps only** (1, 2, 3, …),
    ranked by the pre-cutover active-union order; promoted Clio events are not
    counted. Each wrap after the first names the previous wrap in
    `:event/causes`; the stream is written once, serially, so no stream slot
    conflicts. Verbatim-promoted Clio events keep their original
    stream/seq and are not rewritten into `<repo>/legacy`.
  - Declare the cutover in `.ημ/receipts/README.md` and write an exclusion
    manifest `.ημ/receipts/.cutover-excluded.edn` (EDN vector of relative
    segment filenames that were converted). The frozen legacy file **and** every
    converted plain/envelope source segment become **archived sources**: kept in
    place byte-for-byte (never rewritten, renamed, or deleted) but **excluded
    from the active union**. Post-cutover readers load only active `.ημ/receipts/`
    segments that are **not** listed in `.cutover-excluded.edn` (plus README /
    manifest markers); they must not re-merge the archived legacy file or the
    excluded source segments. This is how `every active record is a Clio event`
    becomes true without violating the committed-segment immutability rule.
  - The merged-ledger check then verifies a bijection: **every** archived
    legacy record (plain, envelope **or already-full-Clio**) **and every
    pre-cutover non-Clio source segment** (plain **and** lightweight Receipt
    River envelopes listed in `.cutover-excluded.edn`) has exactly one active
    Clio event that corresponds to it, and every cutover wrap has a source. A
    wrap corresponds when its `:event/data` and derived `:event/id` match; an
    already-full-Clio archived record corresponds only to an active event that
    is byte-for-byte the same event (same `:event/id` and data). An omitted
    envelope source, or an archived full Clio record with no verbatim active
    copy, must fail the check. Wrap payloads stay grandfathered exactly like their source records.
  - Once that PR merges, the active union holds only Clio events and Clio order
    applies.
- **Clio today** (`open-hax/clio` main): the shipped CLI is `clio append` and
  `clio canonicalize` over ledger files. Segment helpers
  (`append-segment` / `canonicalize-dir`) are proposed on `riatzukiza/clio#5`
  and are **not** available until that lands and is promoted. Until then, write
  segment files directly and run the repository's merged-ledger verifier
  (foresight `verify-receipts` or rheos `bb test:receipts`). Clio has no git
  dependency: the check that committed segments were never edited or removed
  belongs to eta-mu's receipt/ledger commands once those exist.
- When receipt-river moves onto Clio, legacy receipts are wrapped once by the
  serial cutover above (one `<repo>/legacy` stream); every **new** receipt event
  uses a stream keyed by **both writer and branch** (e.g.
  `<repo>/<writer>/<branch>`), or a per-run stream identity, never one shared
  stream per repository. A shared stream for ongoing writes, a writer-only
  stream (one agent on two branches) or a branch-only stream (two agents on one
  branch) recreates concurrent-write conflicts on `[:event/stream :event/seq]`;
  use writer-and-branch or per-run streams unless a documented serial allocator
  hands out the slots.

## Rules (both modes)
These rules and the minimal workflow below apply in both modes. In segmented
mode (`.ημ/receipts/` exists) the section above takes precedence for the
mechanics: every "append" means "write a new segment file"; "tail" / context
recovery means a **bounded** read of the newest ~20 records of the **active**
merged ledger — by effective time **before** cutover, and by the tail of
canonical Clio topological order **after** cutover (timestamps must not drive
the post-cutover window); and none of `rr-init.bb` / `rr-append.bb` /
`rr-tail.bb` / `rr-last.bb` nor the `receipt_river` runtime tool may be used
until they are union-aware. Do not inspect every immutable segment on each
decision; use the bounded union-aware window.

- Legacy mode: if `receipts.edn` exists, **never edit past lines**; if missing,
  create it at the start of non-trivial work.
- Segmented mode: never edit, rename or delete a committed segment or the
  frozen legacy file.
- Check it regularly: regain the newest ~20 records of the active union before
  major decisions (effective-time order pre-cutover, including legacy-only
  mode, where `rr-tail.bb`'s physical tail is only an approximation; Clio
  topological tail post-cutover). Do not re-read every historical segment.
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
  They print **physical file order**, which is not the authoritative
  effective-time order: after branch merges an older-timestamped line can sit
  after newer ones. Use them for a quick look, but recover decision context in
  legacy mode by reading the newest ~20 records by effective time (parse the
  file, sort by `:ts` then record identity, take the last ~20). Making them
  effective-time aware is part of the later union-aware `rr-*` update.

In **segmented mode**, do not use `rr-init.bb`, `rr-append.bb`, `rr-tail.bb`, or `rr-last.bb`;
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

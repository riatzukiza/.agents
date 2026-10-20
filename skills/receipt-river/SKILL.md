---
name: receipt-river
description: Use an append-only Receipt River ledger to externalize execution state — receipts.edn in legacy mode, one immutable .ημ/receipts/ segment per receipt in segmented mode (the standard); read a bounded newest window of the active ledger regularly during work.
---

# Skill: Receipt River (append-only receipts.edn)

## Goal
Make long agent runs smarter and more recoverable by maintaining an **append-only** `receipts.edn` ledger.

## Canonical file
Mode first: when `.ημ/receipts/` exists the repository is in **segmented mode**
— the legacy file that was canonical at migration (project-root `receipts.edn`
or `.ημ/receipts.edn`, whichever applied; never invent `.ημ/receipts.edn` if the
ledger lived at the project root) is frozen, each new receipt is its own
segment, and readers use the active-union window (see "Standard: segmented,
merge-validated ledgers" below). Otherwise (legacy mode): the canonical receipt
file is `receipts.edn` in the project root, or `.ημ/receipts.edn` when that file
already exists (bb scripts prefer it). The default bootstrap location remains
the project root for backward compatibility.

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
  frozen legacy file. This includes the globally published copy that
  `skills/muse-ecosystem/SKILL.md` recommends to consumer repos (eta-mu-actors
  `receipt_river` append/tail): it is legacy-mode only. The same prohibition applies to **`pr-flow`'s `pr.cljs`**
  (`skills/pr-flow/scripts/pr.cljs`): its actionability path still reads and
  `appendFileSync`s only `.ημ/receipts.edn`. In a segmented repository, refuse
  any `pr.cljs` status/settle/gate/request path that would append to or treat
  the frozen monolith as the live ledger — do not run those writes until a
  follow-up PR teaches pr-flow segment read/write (union-aware history and
  append). Routine PR review comments and non-ledger GitHub mechanics remain
  fine. The same refusal applies to **ultra-code** wave receipt appends
  (`skills/ultra-code/SKILL.md`): in segmented mode write a segment (or skip
  the receipt write) rather than appending `receipts.edn`. Write new segments by hand or via Clio once
  `append-segment` / directory canonicalize ship (see Clio note below).

### Rules in segmented mode
- The frozen legacy file (whichever path was canonical at migration) is the
  **first segment**: keep it, never rewrite it, never append to it. After the
  all-Clio cutover (below) it becomes an archived source outside the active
  union, still byte-for-byte unchanged.
- Never edit, rename or delete a committed segment. Corrections are new records.
- Validity is judged on the **merged ledger**. Before cutover: load the legacy
  file plus every segment and validate the union — no base segment changed or
  removed, no base record dropped, no record duplicated, every new record
  valid. After cutover: validate only the **active** union (segments not listed
  in `.cutover-excluded.edn`); archived sources are not re-merged and are
  checked separately by the cutover bijection and byte-immutability rules below.
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
    records: `:event/id`, `:event/schema` (**must** be a map with `:schema/root`,
    `:schema/id`, and `:schema/hash`, naming a revision already published in the
    repository schema history — an empty `{}` or missing nested keys is invalid),
    `:event/type :receipt-river/receipt-recorded`, `:event/stream`, `:event/seq`,
    `:event/causes`, `:event/actor`, `:event/subject`, `:event/at`, and
    `:event/data` (receipt under `:receipt-river/receipt`). Do **not** require the
    lightweight `:event/recorded-at` / `:event/payload` keys on full Clio events.
    For every **newly written** full Clio event (before or after cutover) the
    receipt required keys above are validated on `(:receipt-river/receipt
    (:event/data e))`; only cutover wraps keep that map grandfathered like their
    source record.
  - Any other `:event/type` is invalid in a receipt ledger.
  - **Disjoint shapes.** Classification is by keys, with no overlap: a record
    holding `:event/type` together with **either** lightweight key
    (`:event/recorded-at`, `:event/payload`) **and any** full-Clio-only key
    (`:event/schema`, `:event/stream`, `:event/seq`, `:event/causes`,
    `:event/actor`, `:event/subject`, `:event/at`, `:event/data`) is a
    **hybrid** and is invalid. New writes must not produce one; a hybrid found
    in a frozen legacy record or segment makes the cutover **fail closed** for
    an explicit decision, so no two converters can classify it differently
    (one wrapping it as an envelope, another promoting it verbatim). The same
    holds for a frozen record with an unrecognized `:event/type`.
  - `CONTRACT.edn` `event-envelope` (pre-cutover) and `full-clio-event` /
    `clio-cutover` (cutover/post-cutover) state the same rules for machines.
- Record identity:
  - Valid `#:event` envelopes / full Clio events (by the disjoint shapes
    above): use `:event/id`; invalid event records (hybrid or other
    `:event/type`) get no identity (a reused ID with different data
    is corruption, matching Clio's laws). Content hashing must not override ID.
  - A **plain receipt** is classified by shape (a map with no `:event/type`),
    not by key presence. A plain receipt that also carries `:event/id` (any
    value) is still a plain receipt: that key is ordinary data, and the record
    is treated as **ID-less** for identity, the `sha256:` identity key,
    duplicate detection and the cutover UUIDv5 name (the hash covers the whole
    record, `:event/id` included). Only `#:event` envelopes and full Clio events
    use `:event/id` as identity.
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
  identity (above) as the tie-break, compared through one **identity key**
  string: `event-id:` + the canonical EDN text of `:event/id` (so a string
  and a `#uuid` stay distinct) for records with an ID, `sha256:` + the
  lowercase hex identity for ID-less plain receipts; keys compare as Unicode
  code-point strings. Never compare raw heterogeneous ID values. **Effective-time
  comparison:** accept only an ISO-8601 string (with offset or `Z`) or an EDN
  `#inst`; parse each to a UTC instant (milliseconds since epoch). Invalid or
  unparseable values are treated as missing effective time (sort first) and must
  not displace timestamped records. Never compare raw strings lexicographically
  across mixed offsets, and never compare heterogeneous Clojure types directly.
  Records with no effective-time field sort first and must not displace
  timestamped records from the newest window. This same order is used for
  display and context recovery ("the newest ~20 records") **while any non-Clio
  record remains in the active union** — including **legacy-only** mode (do not
  use physical `rr-tail.bb` order for authoritative recovery). The repository
  verifiers sort only to make validation deterministic; that sort is not a
  recency order. Clio order becomes canonical only once **every** record in the
  active merged ledger is a Clio event, i.e. after the cutover below; from then
  on timestamps never carry ordering authority over replay (context recovery
  ranks only the frozen cutover cohort by effective time; see Rules). Clio order is a deterministic
  **topological** order of the causal DAG (every event in `:event/causes`
  precedes its effects); the post-cutover tie-break among causally incomparable
  events is `[:event/stream :event/seq :event/id]` where `:event/id` is compared
  through the same **identity-key** string (`event-id:` + canonical EDN text),
  never as a raw heterogeneous ID. It is not a lexical sort with `:event/causes`
  as a key.
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
    `:event/subject` (stable receipt subject: the literal prefix `receipt:`
    followed by the source record's **identity key** string defined under
    Order below — `event-id:` + canonical EDN text of its `:event/id`, or
    `sha256:` + lowercase hex identity for an ID-less plain receipt. So
    `#uuid "0f…"` gives `receipt:event-id:#uuid "0f…"`, the string id `"evt-1"`
    gives `receipt:event-id:"evt-1"`, and a plain receipt gives
    `receipt:sha256:<64 hex>`. Never a bare UUID or other converter-local
    rendering; the bijection checks this exact string),
    `:event/at` as a nonempty ISO-8601 UTC string with **exactly three
    fractional digits and a `Z` suffix** (e.g. `2026-10-15T14:43:45.000Z`):
    serialize an EDN `#inst` or offset timestamp to that UTC millisecond form
    (floor to ms); if the source time is missing/invalid use the **pinned
    cutover instant** declared in `.ημ/receipts/README.md` (same spelling),
    and `:event/data` holding the
    source receipt map verbatim under `:receipt-river/receipt` (for a lightweight
    envelope source, the payload map; for a plain receipt, the receipt itself).
  - **Clio-inadmissible legacy values (lossless text wrap):** a source can parse
    as EDN yet hold a value outside Clio's canonical value domain (e.g. a
    `#inst` before 1582, which Clio rejects at canonical admission). Such a
    source is never coerced, re-spelled or dropped. Its wrap's `:event/data` is
    instead `{:receipt-river/receipt-edn <string> :receipt-river/receipt-sha256
    <lowercase hex>}`: the string is the **exact archived source text**
    (the legacy line's bytes without its trailing newline, or the whole
    segment file's bytes), decoded as UTF-8; the hash is SHA-256 of those
    UTF-8 bytes. Use this form **only** when the map form above is rejected by
    Clio admission at the Clio revision pinned in the cutover declaration
    (`clio-rev:` in `.ημ/receipts/README.md`); otherwise use the map form. The
    two forms are exclusive, so independent converters agree. Identity, subject,
    `:event/at` and causes are still derived from the parsed source as above
    (an inadmissible effective time is treated as invalid and uses the pinned
    cutover instant). The bijection accepts a text wrap only when its string's
    bytes equal the archived source bytes, its hash matches, and the map form is
    Clio-inadmissible at the pinned revision; the archived source itself stays
    byte-for-byte in place.
  - Schema revision: Clio validates `:event/schema` against a **persisted**
    schema-history revision before canonicalizing, so the cutover PR first
    publishes the Receipt River catalog into the repository's Clio schema
    history (e.g. `.ημ/receipts/schemas/`, written with Clio's schema store) and
    every wrap and later write names that revision's `:schema/root` /
    `:schema/hash`. The catalog defines `:receipt-river/receipt-recorded` with
    `:event/data` `[:or [:map [:receipt-river/receipt [:map]]] [:map
    [:receipt-river/receipt-edn :string] [:receipt-river/receipt-sha256
    :string]]]` for cutover wraps (grandfathered payloads: any map, or the
    lossless text form above for Clio-inadmissible sources) and the nine required receipt keys for new
    writes (a separate revision or event-type version, never an edit of a
    published one). The cutover PR records the resulting root/hash in
    `.ημ/receipts/README.md`; wrappers that name an unpublished revision are
    invalid.
  - `:event/id` rules:
    - A source that is **already** a Clio event (has `:event/id` and the full
      Clio envelope) is **promoted verbatim** — keep its `:event/id` and do not
      re-wrap, so `:event/causes` edges that name it stay resolvable.
    - A **lightweight Receipt River envelope** whose `:event/id` is already a
      string in canonical lowercase hyphenated UUID spelling keeps that exact
      string, so `:event/causes` edges from promoted Clio events that name it
      stay resolvable. The archived source is outside the active union, so the
      shared ID is not a reuse. IDs are never re-spelled: an uppercase or
      braced UUID string, a `#uuid` value, or any other ID is a **non-canonical
      ID** and follows the UUIDv5 rule below (Clio segment filenames use the
      canonical lowercase spelling).
    - Every other source gets a deterministic **UUID version 5** (RFC 9562
      §6.5 / RFC 4122 name-based SHA-1) whose namespace UUID is the Receipt
      River cutover namespace `6ba7b810-9dad-11d1-80b4-00c04fd430c8` (DNS
      namespace). Name bytes depend on the source identity kind:
      - **ID-less plain receipt**: UTF-8 encoding of the lowercase hex SHA-256
        canonical-EDN identity (above), no separators.
      - **Lightweight envelope with a non-canonical `:event/id`** (e.g.
        `"evt-1"`, `"AB12…"` uppercase, `#uuid "…"`):
        UTF-8 encoding of `receipt-river/event-id:` followed by the canonical
        EDN text (above) of the `:event/id` value. The prefix keeps these names
        disjoint from 64-hex plain identities. Do **not** re-hash the envelope
        into SHA-256 for the name input.
      Implementations must reproduce this exact ID; "name-based UUID" alone is
      not enough.
    - Causal edges: a verbatim-promoted Clio event keeps its `:event/causes`
      byte-for-byte. If any of those causes names a non-canonical envelope ID
      (whose wrap gets a new UUIDv5) or no active event at all, the cutover
      **fails closed**: verbatim promotion and remapping cannot both hold, so stop and
      record the conflict for an explicit decision instead of rewriting.
  - **Stream components.** `<repo>` is not a placeholder for any local
    rendering: the cutover declaration pins `stream-repo:` as one string (the
    repository's GitHub `owner/name` as written in the declaration, e.g.
    `riatzukiza/.agents`; forks and promoted org copies keep the **same**
    declared value). Every stream component (`<repo>`, `<writer>`,
    `<branch>`, `<run-id>`) is written **percent-encoded**: UTF-8 bytes,
    RFC 3986 unreserved characters (`A–Z a–z 0–9 - . _ ~`) kept, every other
    byte (including `/`) as `%XX` with uppercase hex; components are joined
    with `/`. So the reserved stream for `riatzukiza/.agents` is exactly
    `riatzukiza%2F.agents/legacy`, and a branch `docs/x` is `docs%2Fx`. The
    bijection and the reservation check compare these exact strings.
  - **One cutover per lineage.** Because forks share `stream-repo`, the
    cutover runs **once**, on the repository's canonical lineage (the org
    repository's default branch, or the fork that owns it by agreement), and
    every fork or branch adopts it only by merging that cutover commit. A fork
    or branch never performs its own cutover: two independent converters would
    both write `<repo>/legacy` from seq 1 and collide on
    `[:event/stream :event/seq]`. The merged-ledger check **fails closed** if
    the union holds more than one cutover declaration or exclusion manifest, or
    any `<repo>/legacy` event that is not part of the declared cutover. A
    branch that diverged before cutover and merges it must not carry non-Clio
    segments added after the cutover; the check fails such segments closed for
    an explicit decision rather than silently re-admitting them.
  - All new wraps go in one cutover-only stream `<repo>/legacy`, which is
    **reserved**: no pre-cutover or post-cutover write may use it, and the
    cutover **fails closed** if the pre-cutover union already holds any event in
    `<repo>/legacy` (Clio treats a reused `[:event/stream :event/seq]` slot as a
    conflict). `:event/seq`
    is the wrap's **one-based ordinal among the wraps only** (1, 2, 3, …).
    Rank wraps in a **causal topological** order of the mapped source parents
    (every mapped `:event/caused-by` / `:caused-by` parent edge precedes its effect), using the
    pre-cutover effective-time + identity-key order only as a tie-break among
    causally incomparable wraps; `:event/seq` is the one-based ordinal in that
    ranking (not a separate effective-time-only sort). Promoted Clio events are
    not counted. If a
    source parent edge would create a cycle with the serial wrap chain (a
    forward causal edge relative to that ranking), the cutover **fails closed**
    before emitting segments. Each wrap after the first names the previous wrap
    in `:event/causes`, and also every parent its source named, each mapped to that
    parent's active cutover ID by the `:event/id` rules; if any named parent
    has no active event the cutover **fails closed**. Read parents from the
    key **as parsed**: a lightweight envelope written as `#:event{... :caused-by
    [...]}` expands to the qualified key **`:event/caused-by`** (see this
    repository's `.ημ/receipts.edn`), never the bare `:caused-by`; a plain
    receipt's parents are its unqualified `:caused-by`. Converters and the
    verifier must use these exact keys, and a source holding any other
    caused-by spelling (or both) fails closed rather than yielding an empty
    edge set. The source caused-by value **must be an ordered vector** (or a
    list that preserves written order) of parent identities; a set, map, or
    other unordered collection **fails closed** — there is no canonical
    ordering of unordered inputs before dedupe. **Each element** is resolved
    to exactly one source record through the identity-key strings defined
    under Order: an element that is a string starting with `event-id:` or
    `sha256:` is read as a **tagged identity key** (`sha256:` + 64 lowercase
    hex names an ID-less plain receipt; `event-id:` + canonical EDN text names
    an `:event/id`); any other element is a **raw `:event/id` value** and maps
    to the key `event-id:` + its canonical EDN text (so this repository's
    `"dda66847-…"` strings name envelopes by ID). A bare 64-hex digest without
    `sha256:` is a raw ID, not a plain-receipt reference. Collect the candidate
    records under **both** readings; exactly one candidate is required. Zero
    candidates is an unresolvable parent and an element matching two records
    (e.g. an envelope whose `:event/id` string equals a tagged key that also
    names another record) is **ambiguous**; both **fail closed**. New
    pre-cutover writes should spell parents as tagged identity-key strings. Deduplicate the combined
    cause IDs in deterministic order (serial previous-wrap first, then mapped
    source parents in that vector's source order, unique) — Clio rejects
    duplicate cause IDs.
    The stream is written once, serially, so no stream slot conflicts. The
    stream name and `:event/seq` give **no recency guarantee**: Clio uses the
    stream tie-break only among causally incomparable events, so a promoted
    Clio event in a stream that sorts before `<repo>/legacy` can precede every
    wrap in Clio order even when it is the newest pre-cutover decision. Context
    recovery therefore uses the cutover-cohort recovery order under Rules, not
    the raw Clio tail.
    Verbatim-promoted Clio events keep their original stream/seq and are not
    rewritten into `<repo>/legacy`.
  - Declare the cutover in `.ημ/receipts/README.md` and write an exclusion
    manifest `.ημ/receipts/.cutover-excluded.edn` (EDN vector of relative
    segment filenames that were converted). The README declaration **must pin
    one cutover instant** as a UTC ISO-8601 string with exactly three
    fractional digits and `Z` (e.g. `cutover-at: 2026-10-15T14:43:45.000Z`),
    the `stream-repo:` string (above) and the Clio revision `clio-rev:` (a
    full commit SHA) whose admission decides the text-wrap form.
    That exact string is the substitute `:event/at` for every wrap whose source
    time is missing/invalid, and every normalized source `:event/at` uses the
    same precision so independent converters and the bijection agree. The frozen legacy file **and** every
    converted plain/envelope source segment become **archived sources**: kept in
    place byte-for-byte (never rewritten, renamed, or deleted) but **excluded
    from the active union**. Post-cutover readers load only active `.ημ/receipts/`
    segments that are **not** listed in `.cutover-excluded.edn` (plus README /
    manifest markers); they must not re-merge the archived legacy file or the
    excluded source segments. This is how `every active record is a Clio event`
    becomes true without violating the committed-segment immutability rule.
  - After the cutover PR merges, `.cutover-excluded.edn` and the cutover
    declaration in `.ημ/receipts/README.md` are **frozen** like committed
    segments: never edited, renamed or deleted. The merged-ledger check verifies
    both byte-for-byte against the cutover commit, so active-union membership
    cannot change by editing the manifest.
  - The merged-ledger check then verifies a bijection: **every** archived
    legacy record (plain, envelope **or already-full-Clio**) **and every
    pre-cutover non-Clio source segment** (plain **and** lightweight Receipt
    River envelopes listed in `.cutover-excluded.edn`) has exactly one active
    Clio event that corresponds to it, and every cutover wrap has a source. A
    wrap corresponds only when **every prescribed wrapper field** equals the
    value the cutover rules derive for its source: `:event/id`, `:event/schema`
    (the published wrap revision), `:event/type`, `:event/stream`
    (`<repo>/legacy`), `:event/seq` (its causal-topo ordinal), `:event/causes`
    (serial previous-wrap edge when applicable, plus every mapped source parent
    ID, deduplicated, in the prescribed order), `:event/actor`
    (`receipt-river/cutover`), `:event/subject` (`receipt:` + identity-key
    string), `:event/at` (UTC ISO-8601 with exactly three fractional digits and
    `Z`, using the pinned cutover instant when the source time is missing or
    invalid) and `:event/data`, and the wrap has no other keys.
    Clio validity alone cannot catch a wrong stream, seq, actor or timestamp;
    any mismatch fails the check. An
    already-full-Clio archived record corresponds only to an active event that
    is **byte-for-byte identical** to the archived event (complete map: id,
    schema, type, stream, seq, causes, actor, subject, at, data — not merely
    id+data). An omitted envelope source, a wrap whose causes omit or remiss a
    mapped parent, or an archived full Clio record with no verbatim active copy,
    must fail the check. Wrap payloads stay grandfathered exactly like their
    source records.
  - **Exclusion-manifest exactness:** `.cutover-excluded.edn` membership must be
    **exactly** the set of converted plain/envelope source segment filenames
    (plus nothing else). Extra entries — including an existing full-Clio segment
    that was never a cutover source — **fail** the check so the frozen manifest
    cannot silently drop unrelated Clio history from the active union. The
    frozen legacy file is archived by the cutover declaration, not by listing it
    in the manifest.
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
  uses a **per-run** stream that also names writer and branch (e.g.
  `<repo>/<writer>/<branch>/<run-id>`, each component percent-encoded as under
  Stream components, where `<run-id>` is unique per session
  or process, such as a fresh UUID), never one shared stream per repository.
  Writer-and-branch alone is **not** enough: two concurrent sessions of the same
  writer on the same branch (e.g. two `grok-bot` runs on one PR) would both
  derive the same next `:event/seq`. A shared stream, a writer-only stream, a
  branch-only stream or a writer-and-branch stream without a run identity all
  recreate concurrent-write conflicts on `[:event/stream :event/seq]`; the only
  alternative to a per-run stream is a documented serial allocator that hands
  out the slots.

## Rules (both modes)
These rules and the minimal workflow below apply in both modes. In segmented
mode (`.ημ/receipts/` exists) the section above takes precedence for the
mechanics: every "append" means "write a new segment file"; "tail" / context
recovery means a **bounded** read of the newest ~20 records of the **active**
merged ledger — by effective time **before** cutover, and by the tail of the
**post-cutover recovery order** **after** cutover. The recovery order is: the
**cutover cohort** (every active event present in the cutover commit's tree —
all `<repo>/legacy` wraps and every promoted Clio event) ordered by effective
time (`:event/at` parsed to a UTC instant, then identity key; an invalid or
unparseable `:event/at` on a verbatim-promoted event is treated as missing and
sorts first, exactly as in the pre-cutover order), **followed by** every
later active event in canonical Clio topological order. Replay, validation
and hashing keep canonical Clio order; the recovery order only selects and
displays the window, so a recent promoted event can never fall behind 20 older
wraps because of its stream name. Timestamps never order post-cutover writes.
Post-cutover events can follow the whole cohort because every post-cutover
write is **frontier-linked**: the **first** event of each new per-run stream
names in `:event/causes` **every current frontier tip** of the active union it
read (each active event that no other active event names as a cause — right
after cutover, the last `<repo>/legacy` wrap plus any promoted Clio tips), and
each later event of that run names the run's previous event. A new run is then
causally after everything it observed, so the topological tie-break can only
interleave it with genuinely concurrent runs (other branches/sessions written
since that read), never place it before older history. The read is made
**observable**: a run reads the frontier from one committed tree, never from
loose working-tree files, and its first event records that commit as
`:receipt-river/read-commit` (full 40-hex SHA) in `:event/data` beside
`:receipt-river/receipt`. The merged-ledger check recomputes the frontier tips
of the active union at that commit and fails the event when the field is
missing, the commit does not contain the cutover declaration, the commit is
not an ancestor of the commit that adds the event's segment, or any tip at that
commit is absent from the event's `:event/causes`. Extra causes are allowed
when they resolve. Tips created concurrently after the read commit are thus
distinguishable from omitted observed tips, so an omission is detectable and
invalid. The snapshot must also be **fresh**: the event's segment is added in
a non-merge commit, and the active ledger at that commit's parent must equal
the active ledger at `read-commit` (normally the parent *is* `read-commit`).
Any active-ledger change between the read and the segment's commit (another
segment added, or a merge or pull that brought receipts in) fails the event,
so a run can never omit tips already in its own history; re-read the frontier
and write a new run instead. Later merges of other branches remain fine:
those writes are genuinely concurrent. This `read-commit` proof applies only
to **post-cutover** runs: pre-cutover full-Clio writes and cutover wraps carry
no `read-commit` (no cutover declaration exists yet for them to name).
None of `rr-init.bb` / `rr-append.bb` /
`rr-tail.bb` / `rr-last.bb` nor the `receipt_river` runtime tool may be used
until they are union-aware. Do not inspect every immutable segment on each
decision; use the bounded union-aware window.

- Legacy mode: if `receipts.edn` exists, **never edit past lines**; if missing,
  create it at the start of non-trivial work.
- Segmented mode: never edit, rename or delete a committed segment or the
  frozen legacy file.
- Check it regularly: regain the newest ~20 records of the active union before
  major decisions (effective-time order pre-cutover, including legacy-only
  mode, where `rr-tail.bb`'s physical tail is only an approximation; the tail
  of the post-cutover recovery order — cutover cohort by effective time, then
  later events in Clio topological order — post-cutover, which is recent
  because writes are frontier-linked as above). Do not re-read every historical segment.
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
  **effective-time window**: after branch merges an older-timestamped line can
  sit after newer ones. Use them for a quick look only. Authoritative legacy-mode
  context recovery is the newest ~20 records of the **effective-time window**
  over `receipts.edn` (parse each `:ts` as ISO-8601 / `#inst` to a UTC instant;
  missing/invalid sort first; then the record-identity key; take the last ~20) —
  the same rule as CONTRACT `mixed-ledger-order`, never physical `rr-tail.bb`
  order. Making the scripts themselves emit that window is part of the later
  union-aware `rr-*` update.

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

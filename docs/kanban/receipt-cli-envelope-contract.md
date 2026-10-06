---
uuid: "4f9a9cf7-fb06-47cc-a2c2-96794e6a6819"
title: "Emit schema-valid receipt helper envelopes"
status: "incoming"
priority: "P1"
points: 2
labels: receipt-river, schema, cli
---

# Emit schema-valid receipt helper envelopes

## Context

[Issue #19](https://github.com/riatzukiza/.agents/issues/19) records that
`rr-append.bb` persists `manifest` and `refs` as scalar CLI strings, including
literal `none` when omitted. The portable Foresight envelope requires vectors
of nonblank strings. Historical receipts must remain untouched.

## Outcome

Callers append a valid envelope directly through the canonical helper. They
need no repository-local normalizer or historical correction.

## Scope

- Add and document a bounded collection parser in Receipt River's helper seam.
- Omitted collections become empty vectors. Support existing comma-separated
  values (trim separator whitespace, preserve spaces within paths), and
  explicitly encoded EDN string vectors for values containing commas.
- Validate vector entries and reject malformed input before any append.
- Retain every required scalar envelope key. Review whether missing `dod` and
  `pi` must be rejected or given explicitly documented unspecified defaults;
  never invent a success, acceptance criterion, interpreter, or test result.
- Add Babashka unit and CLI fixtures, and invoke them from an existing hosted
  helper/policy job after the reviewed repair is implemented.

## Non-goals

Rewrite historical ledgers, promote fixture results into runtime evidence,
change review policy, add repository-specific schema normalizers, install
skills globally, or change root discovery (which belongs to issue #20).

## Acceptance criteria

- Omitted manifest/refs values produce `[]`; populated values produce vectors
  of nonblank strings, including filenames containing spaces and commas.
- Every required scalar field is present and compatible with the receipt
  envelope contract. Invalid input returns nonzero and appends no bytes.
- Existing receipt bytes remain an exact prefix after each successful append.
- A second append adds exactly one EDN value on one new line.
- Tests exercise the actual helper, not a copied writer or normalizer.

## Verification

Disposable `.ημ/receipts.edn` fixtures execute the actual helper and parse its
last EDN value. Cover omitted, comma-separated, EDN vector, malformed vector,
blank entry, missing scalar context, and historical-prefix cases. Compare
against a portable envelope predicate and run the bounded Babashka suite in CI.

## Risks

Literal filename `none` needs explicit EDN encoding if retained as a legacy
empty-list sentinel. Rejecting missing scalar context can break formerly
accepted calls; an unspecified default must be explicit and cannot supply
acceptance evidence. Planning review must settle that compatibility choice.

## Planning boundary

Hand-authored incoming Markdown input. No operational admission, reviewed-ready
state, implementation claim, or synthetic event ID is asserted. Canonical
PR Flow planning review and lawful Rheos readiness precede helper changes.

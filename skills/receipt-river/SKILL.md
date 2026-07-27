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

## Rules
- If `receipts.edn` exists: **never edit past lines**.
- If missing: create it at the start of non-trivial work.
- Check it regularly: tail last ~20 lines before major decisions.
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

Append the EDN line directly to `receipts.edn`.

## bb scripts
The skill ships with Babashka scripts in `scripts/` for harness-agnostic interaction.

- `rr-init.bb` — create an empty `receipts.edn`. Use `--eta-mu` to place it under `.ημ/receipts.edn`.
- `rr-append.bb` — append one EDN receipt line.
- `rr-tail.bb` — print the last N receipt lines.
- `rr-last.bb` — print the last receipt(s), optionally filtered by `--kind`.

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

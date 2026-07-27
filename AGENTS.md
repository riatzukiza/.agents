# Agent Instructions

This repository is the canonical source for Err's reusable agent skills, contracts, scripts, and cross-harness operating principles.

These instructions apply to the entire repository.

## Authority and discovery

1. Obey the active harness and user instructions.
2. Read [`PRINCIPLE.edn`](PRINCIPLE.edn) before substantive work in this repository.
3. Read this file completely.
4. Inspect `.ημ/receipts.edn` before major decisions when it exists.
5. Discover the smallest relevant set of skills under `skills/` and read their `SKILL.md` files before acting.
6. Read a skill's `CONTRACT.edn` when changing its activation, governance, scoring, or non-override behavior.

Do not load the entire skill corpus by default. Prefer targeted discovery from task language, referenced files, and explicit skill invocations.

## Core skills

The following skills are part of the repository's operating substrate:

- `receipt-river` — mandatory for non-trivial, multi-step, migration, PR, or handoff work.
- `session-mycology` — use after substantive work to record friction and incubate reusable patterns.
- `fork-tax` — use only when the user invokes Π, fork tax, full dump, snapshot, or deterministic handoff.
- `grok-intention` — use when intent is dense, symbolic, compressed, or recoverable from repository context.
- `sing-the-songs-of-your-people` — use for truthful corpus-native synthesis, not decorative mystification.
- `skill-authoring` — use when creating or materially revising a reusable skill.

Explicit invocation wins. Otherwise activate skills only when their gates match.

## Repository invariants

- `~/.agents` is the canonical global root on local machines.
- `skills/<name>/SKILL.md` is the human-operational skill definition.
- `skills/<name>/CONTRACT.edn` is the machine-readable contract when one exists.
- Skill folder names and frontmatter `name` values must match and use lowercase kebab-case.
- Preserve provenance for imported, generated, or promoted skills.
- Prefer harness-neutral instructions. Put harness-specific assumptions in clearly named compatibility sections or adapters.
- Do not silently rewrite imported third-party skills as if they were native.
- Never commit secrets, tokens, authorization headers, private keys, session cookies, or private user data.

## Skill changes

When adding or materially revising a skill:

1. Read `skills/skill-authoring/SKILL.md`.
2. Search for overlapping skills before creating a new one.
3. Keep activation and anti-activation gates explicit.
4. Keep procedures actionable and testable.
5. Reference global principles instead of duplicating them.
6. Add or update `CONTRACT.edn` when the skill participates in automated activation or governance.
7. Preserve the repository license contract: libraries are LGPL-3.0-or-later; services and standalone applications are GPL-3.0-or-later.
8. Update documentation or indexes that would otherwise drift.

## Receipt River

For non-trivial work, maintain `.ημ/receipts.edn` as an append-only ledger.

- Never modify or delete prior receipt lines.
- Append an observation near the start.
- Append decisions when choosing among meaningful alternatives.
- Append verification results after checks.
- Append handoff or push truth before completion.
- Record the actual host and tool boundary. A GitHub connector is not a local checkout.
- When local scripts are unavailable, preserve the same append-only semantics through repository APIs.

Read `skills/receipt-river/SKILL.md` for the canonical schema.

## Session Mycology

After substantive work:

1. Read the relevant receipt tail.
2. Score efficiency, friction, and skill-candidate probability.
3. Append one entry to `.ημ/session-mycology/ledger.md`.
4. Create at most one spore in `.ημ/session-mycology/spores/` when `p-skill-candidate >= 0.7` and the pattern generalizes.
5. Never promote a spore in the same session that created it.

Promotions must preserve provenance and be reviewed before becoming a production skill.

## Git and concurrent work

- Assume shared or concurrent work unless exclusivity is explicit.
- Use a branch and pull request for repository-wide governance changes.
- Never destroy unrelated work with repo-wide reset, restore, clean, or blanket staging.
- Scope changes to owned paths.
- Use Fork Tax only when explicitly triggered.

## Validation

Use the smallest relevant checks available in the active harness:

- Re-read every touched file after writing it.
- Verify Markdown links and referenced paths.
- Verify `SKILL.md` frontmatter and folder-name agreement.
- Verify edited s-expressions for balanced delimiters and canonical uncertainty code points.
- Run shipped scripts or parsers when a local execution environment is actually available.
- Record skipped checks and the reason; never claim a check ran when it did not.

## Handoff

Report:

- files changed,
- why the change is safe,
- verification performed or skipped,
- receipt and mycology artifacts added,
- branch, commit, and pull-request references.

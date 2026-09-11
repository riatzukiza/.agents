# PROCESS — charter for the skill catalog

This is the epiphany-modeled charter for this repository's skill catalog. The full epiphany charter lives at [`../spaces/foresight/epiphany/PROCESS.md`](../spaces/foresight/epiphany/PROCESS.md) (verified present on this host).

## Purpose

Version the reusable agent skills of this device in one canonical place, and promote session-mycology spores into reviewed skills lawfully — so that every harness that discovers `~/.agents/skills` gets the same, verified operating substrate.

## Scope and non-goals

In scope:

- the skill catalog under `skills/`,
- per-skill `SKILL.md` and `CONTRACT.edn` definitions,
- the learning loop (receipts → mycology → spores → promotion).

Device branches may also carry a `skills.disabled/` parking lot (this checkout, on `device/stealth`, does not); when present, it is in scope as described under "Constitutional commitments" below.

Non-goals:

- this repository does **not** govern harness configurations; harness config lives elsewhere (for example, `.config/opencode`),
- it does not mandate which skills a harness loads on a given turn — discovery remains targeted,
- it does not govern project-local skills; those live in their own repositories and are consulted first.

## Authority order

1. root contract (`PRINCIPLE.edn`, mirrored in `AGENTS.md` grammar) — mission, directives, safety, license,
2. `PRINCIPLE.edn` — the global intent contract,
3. a skill's own `SKILL.md` / `CONTRACT.edn` — may specialize execution,
4. harness copies and adapters — may specialize discovery or tool syntax, but must point back here and may not silently override immutable layers.

## Constitutional commitments

- A skill is **canonical** only when its `SKILL.md` exists, its `CONTRACT.edn` exists where automation needs one, and the skill was verified by invocation.
- Where a device branch carries `skills.disabled/`, it is **parking, not deletion**. Moving a skill there is a decision and requires a receipt note naming what was parked and why.
- Spores promote **only via review** in a later session; never in the session that created them.
- Provenance of imported skills is preserved (see `skills/.skill-lock.json`); third-party material is never silently rewritten as native.
- No secrets in skills, contracts, or receipts.

## Lifecycle

```text
draft SKILL.md (+ CONTRACT.edn when automation needs it)
  -> invoke the skill in a live session to verify
  -> canonical under skills/<name>/
  -> if unused by any harness (on branches that keep one): park in skills.disabled/ (with a receipt)
  -> a parked skill may return to skills/ by the same review path
  -> spores from .ημ/session-mycology/spores/ enter only through review-promotion
```

## Evidence

| Claim | Evidence |
|---|---|
| Skill definitions are human-operational + machine-contractual | `skills/receipt-river/SKILL.md` + `CONTRACT.edn` |
| Parking is a recorded decision | `.ημ/receipts.edn` (append-only) |
| Promotion requires review | `AGENTS.md` — Session Mycology; `PRINCIPLE.edn` — learning rules |
| Imported-skill provenance | `skills/.skill-lock.json` |

## Exceptions

- Explicit user invocation wins over activation gates and parking state.
- Harness-specific adapters may add discovery or tool syntax but may not override the immutable layers (mission, directives, safety, license, output shape).
- When a required capability is unavailable in the active harness, record the skip truthfully instead of simulating verification.

## Amendments

Changes to this charter follow the same governance as repository-wide changes: a branch and pull request, receipts for decisions, and re-verification of anything the amendment touches. The root contract's immutable sections cannot be amended by this document.

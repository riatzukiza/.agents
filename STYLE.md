# STYLE — skill authoring conventions

Conventions for `SKILL.md` and `CONTRACT.edn` files in this catalog, derived from the existing corpus (sampled on this checkout: `fork-tax`, `receipt-river`, plus the core substrate skills).

## SKILL.md

### Frontmatter

Native skills use YAML frontmatter with two fields:

```yaml
---
name: fork-tax
description: Persist the full working state into git (commit + tag + push + manifest artifacts) as a deterministic handoff snapshot. Use when the user requests Π / fork tax / full dump.
---
```

- `name` — lowercase kebab-case, **must match the folder name** (`skills/<name>/`).
- `description` — activation-oriented: written so a harness can decide from it alone. Prefer "Use when …" phrasing over feature lists.

### Body shape

The recurring section shape is:

1. `# Skill: <Name> (<one-line essence>)` — title with a parenthetical essence.
2. `## Goal` — why the skill exists, one to three sentences.
3. `## Use This Skill When` — activation gates, bullet list.
4. `## Do Not Use This Skill When` (or equivalent anti-activation notes) — explicit anti-gates.
5. `## Steps` / `## Rules` / `## Minimal workflow` — numbered, actionable, testable steps.
6. Optional sections (`## Multi-Agent Guardrails`, `## Canonical file/format`, `## bb scripts`, `## Migration note`, `## Project discovery`) — only when they carry real content.

Rules of thumb:

- Keep procedures actionable and testable; a step should be executable or checkable.
- Reference global principles instead of duplicating them.
- Stay harness-neutral; put harness-specific assumptions in clearly named compatibility sections or adapters.
- Reference sibling files with relative links (`scripts/handoff.bb`, `CONTRACT.edn`).

## CONTRACT.edn

Machine-readable contracts are EDN, one top-level `skill-contract` form per skill:

```edn
(skill-contract
  (name "receipt-river")
  (v "ημ.skill/receipt-river@0.2.0")
  (intent "…")
  (activation
    (priority 62)
    (explicit ["skill:receipt-river"])
    (triggers ["receipt river" "…"]))
  (governance
    (touch-layer :mutable)
    (non-override [:mission :directives :safety :license :output-shape])
    (requires-user-approval false))
  (effects
    (writes true)
    (network false)
    (commits false))
  (protocol
    (workflow ["…" "…"])))
```

Conventions:

- `name` — matches the folder and frontmatter `name` exactly.
- `v` — `ημ.skill/<name>@MAJOR.MINOR.PATCH`.
- `intent` — one sentence, mirrors the frontmatter `description`.
- `activation` — integer `priority`, `explicit` invocation tokens, `triggers` phrase list.
- `governance` — `touch-layer` keyword plus a `non-override` vector that always includes the immutable layers.
- `effects` — boolean side-effect declaration (`writes`, `network`, `commits`).
- `fulfillment-score` — optional scoring function; include only when automated activation scoring is real (as in `receipt-river`).
- `protocol`/`workflow` — ordered vector of strings.
- A skill may add a second top-level form (for example `receipts-contract`) when it defines a domain protocol.
- Balanced delimiters; keywords with leading `:`; no comments required but `;;` line comments are fine.

## Doc formatting

- Markdown, GitHub-flavored; fenced code blocks with language tags (`bash`, `edn`, `yaml`, `text`).
- Tables with `|---|` separator rows.
- Relative links for in-repo references; absolute paths only for device-level facts.
- No emoji unless the skill's domain calls for it.

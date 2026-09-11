# Style guide — authoring `SKILL.md` in this catalog

Conventions verified against the actual skills under `skills/` (121 skills,
2026-09-11; 78 carry a `CONTRACT.edn`).

## Frontmatter (required)

Every skill starts with YAML frontmatter whose `name` matches the folder
name exactly, lowercase kebab-case:

```yaml
---
name: receipt-river
description: One sentence stating when and why to load this skill.
---
```

- `name` — required, kebab-case, identical to `skills/<name>/`.
- `description` — required, a single sentence with concrete activation
  triggers; harnesses use it for skill selection.

## Body structure (conventional sections)

The native pattern, seen across the catalog:

```markdown
# Skill: <Human Title>

## Goal
What the skill accomplishes, in one or two sentences.

## Use This Skill When
Bullet list of activation gates — task shapes, trigger phrases, situations.

## <Workflow / Rules / Canonical ...>
The operational core: numbered steps, rules, or canonical file formats.

## (optional) References / Scripts
Pointers into the skill's own `scripts/` and `references/`.
```

- Activation gates must be explicit — include both when to use and, where
  relevant, when **not** to use (anti-activation).
- Procedures must be actionable and testable by an agent with file tools.
- Prefer harness-neutral instructions; put harness-specific syntax in a
  clearly named compatibility section.
- Reference `PRINCIPLE.edn` instead of duplicating global law.

## CONTRACT.edn (optional, machine-readable)

Add one when the skill participates in automated activation or governance.
Canonical shape (EDN, one top-level `(skill-contract ...)` form):

```clojure
(skill-contract
  (name "receipt-river")
  (v "ημ.skill/receipt-river@0.2.0")
  (intent "...")
  (activation (priority 62)
              (explicit ["skill:receipt-river"])
              (triggers ["..."]))
  (governance (touch-layer :mutable)
              (non-override [:mission :directives :safety :license :output-shape])
              (requires-user-approval false))
  (effects (writes true) (network false)))
```

## Directory layout

```text
skills/<name>/
├── SKILL.md       # required — human-operational source
├── CONTRACT.edn   # optional — machine-readable contract
├── scripts/       # optional — harness-neutral helpers
└── references/    # optional — supporting material
```

Imported skills may deviate from this layout; preserve their structure and
provenance rather than reshaping them to fit.

## Known deviation

`skills/webhook-fullstack/` nests its skill under a `skill/` subdirectory
(`skill/SKILL.md`). It is the catalog's single exception; do not copy the
pattern.

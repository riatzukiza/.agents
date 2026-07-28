---
name: skill-authoring
description: Create or revise scoped, capability-adaptive skills in the canonical .agents repository while preserving provenance and generating harness projections only when needed.
license: LGPL-3.0-or-later
metadata:
  audience: agents
  workflow: skill-authoring
  version: "2"
---

# Skill: Skill Authoring

## Goal

Create or revise reusable skills whose activation, authority, procedure, capability assumptions, evidence, and provenance remain legible across agent harnesses.

## Use This Skill When

- The user explicitly asks to create, revise, extract, or install a skill.
- A workflow repeats across sessions or repositories and deserves a reviewed procedure.
- An existing skill has stale paths, unsafe assumptions, missing gates, or harness lock-in.
- A project-local skill should be generalized into the canonical global catalog.
- A host adapter must project an existing skill into a harness-specific location or format.

## Do Not Use This Skill When

- The change is a one-off instruction with no reusable procedure.
- A Session Mycology spore was created in this same session and the user has not directly requested promotion.
- The real need is a process policy, architecture decision, or repository instruction rather than a selectable skill.
- An upstream imported skill should remain untouched; write an adapter or extension instead.

## Authority and locations

Canonical global skills live at:

```text
~/.agents/skills/<name>/
```

Inside this repository, that path is:

```text
skills/<name>/
```

Project-local skills normally live at:

```text
<project>/.agents/skills/<name>/
```

Harness-specific directories such as `.opencode/skill`, `.claude/skills`, or `.codex/skills` are projections or compatibility surfaces. Create them only when the harness requires them; do not make them the sole source.

## Inputs

- The user's requested outcome and trigger language.
- Applicable `PRINCIPLE.edn`, `PROCESS.md`, `STYLE.md`, and `AGENTS.md`.
- Existing overlapping skills and contracts.
- Environment classification and target harnesses.
- Source receipts, mycology spores, repository examples, or upstream provenance.

## Required shape

A native skill normally contains:

```text
skills/<name>/
├── SKILL.md
├── CONTRACT.edn      # when activation/governance is machine-consumed
├── scripts/          # optional executable helpers
└── references/       # optional evidence or detailed guidance
```

`SKILL.md` is the human-operational source. `CONTRACT.edn` is the machine-readable activation/governance source when one exists.

## Frontmatter contract

- `SKILL.md` begins with YAML frontmatter.
- `name` matches the folder and `^[a-z0-9]+(-[a-z0-9]+)*$`.
- `description` states both the capability and meaningful activation context.
- Use only portable fields accepted by the target ecosystems: `name`, `description`, `license`, `compatibility`, `metadata`, unless a declared adapter owns additional fields.
- Declare the applicable LGPL/GPL license or preserve the upstream license.
- Record provenance for imported or promoted material.

## Content contract

Include, when applicable:

1. **Goal** — one operational outcome.
2. **Use This Skill When** — concrete activation gates.
3. **Do Not Use This Skill When** — anti-activation and authority limits.
4. **Authority / Inputs** — canonical sources and required context.
5. **Environment first** — capability assumptions and degraded behavior.
6. **Workflow** — ordered, testable steps.
7. **Failure and stop conditions** — unavailable, blocked, unsafe, or insufficient evidence.
8. **Outputs** — artifacts and truthful state transitions.
9. **References** — related skills, contracts, sources, or adapters.

Keep durable procedure in the skill and volatile commands, version notes, or long examples in references when practical.

## Contract guidance

Add or revise `CONTRACT.edn` when:

- automatic activation consumes triggers or priority,
- tools need declared write/network/commit effects,
- governance requires non-override rules,
- a scorer or registry consumes the skill,
- or multiple harnesses compile the contract.

A contract should declare:

- name and version,
- intent,
- explicit and inferred activation,
- governance/non-override boundaries,
- effects,
- artifacts,
- and a concise protocol.

Do not claim an effect is available merely because the skill describes it. Runtime capability is established by `environment-classifier`.

## Workflow

1. **Recover intent**
   - Use `grok-intention` when the request is compressed.
   - State the reusable invariant, not just the latest implementation.
2. **Search for overlap**
   - Prefer revision, composition, or an adapter over a near-duplicate skill.
3. **Classify authority**
   - Separate constitution, policy, repository instructions, skill procedure, and generated harness projection.
4. **Inspect evidence**
   - Read representative repositories, receipts, spores, failures, and current implementations.
5. **Design activation**
   - Write positive gates, negative gates, and explicit invocation aliases.
6. **Design capability adaptation**
   - Define full, restricted, connector-only, and file-only behavior where relevant.
7. **Write the skill and contract**
   - Keep names, paths, versions, effects, and references consistent.
8. **Project only as required**
   - Generate or link harness-native copies through declared tooling; do not independently hand-maintain copies.
9. **Verify**
   - frontmatter parses,
   - folder and name agree,
   - s-expressions balance,
   - referenced paths exist,
   - scripts parse/run when the environment supports them,
   - and activation does not override higher authority.
10. **Record provenance**
   - Append Receipt River state and Session Mycology reflection.

## Promotion rules

- A direct user request to create a skill authorizes immediate skill work.
- An automatically incubated Session Mycology spore is not promoted during the same session that created it.
- Promotion preserves source session/task, evidence, recurrence, and the reviewed disposition.
- Rejected or superseded spores remain discoverable.

## Strong hints

- Skills are intent modules, not essays.
- Prefer one strong procedure over a vague catalog of possibilities.
- Keep tools optional unless the skill's purpose is specifically to operate that tool.
- Make absence and degradation explicit.
- Do not duplicate global principles; link to them.
- Do not silently rewrite third-party skills as native work.
- Generated host files are projections, not independent authority.

## Output

- new or revised canonical skill files,
- contract/provenance changes where applicable,
- any deliberate harness projections,
- verification results and unavailable checks,
- and one next action.

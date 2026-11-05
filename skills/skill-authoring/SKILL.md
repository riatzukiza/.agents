---
name: skill-authoring
description: Create or revise skills that load correctly in OpenCode and Codex.
---

# Skill: Skill Authoring

## Goal
Create or revise repo skills so they are reusable, scoped, and load correctly in OpenCode and Codex.

## Use This Skill When
- A task repeats across issues or repos and needs a dedicated skill.
- You are asked to "create a skill" or "add guidance" for agent behavior.
- The workflow needs stronger guardrails or a consistent checklist.

## Do Not Use This Skill When
- The change is a one-off edit or quick fix.
- You only need to update a single file without new workflow guidance.

## Inputs
- The user request and target workflow context.
- Existing agent instructions (`AGENTS.md`) and any `.opencode/` docs.

## Required Output
1. A new or updated skill in the intended canonical catalog, with YAML frontmatter. For this repository that is `skills/<name>/SKILL.md`, installed globally as `~/.agents/skills/<name>/SKILL.md`.
2. Verify the target harness discovers that location. OpenCode directly discovers `~/.agents/skills` and project `.agents/skills`; it also supports `.opencode/skills`. A separate link is unnecessary when the canonical location is already discovered. Check the installed OpenCode version and effective configuration before adding an adapter.
3. Update an index or `AGENTS.md` only where it would otherwise drift; do not put every optional skill in always-loaded rules.

## OpenCode Compatibility Checklist
- `SKILL.md` begins with YAML frontmatter.
- `name` matches folder and matches `^[a-z0-9]+(-[a-z0-9]+)*$`.
- `description` is 1–1024 characters and specific.
- Frontmatter fields are only: `name`, `description`, `license`, `compatibility`, `metadata`.

## Strong Hints
- Prefer 1-2 pages of concise instructions; avoid essays.
- Include explicit "Use This Skill When" and "Do Not Use" gates.
- Keep steps actionable and testable; avoid vague advice. Test representative positive and negative triggers and relevant outcomes before promoting a skill.
- Avoid duplicating global rules; reference them instead.

## Suggested Template
```
# Skill: <Name>

## Goal
<single sentence>

## Use This Skill When
- <trigger>

## Do Not Use This Skill When
- <anti-trigger>

## Inputs
- <inputs>

## Steps
1. <step>

## Output
- <deliverable>
```

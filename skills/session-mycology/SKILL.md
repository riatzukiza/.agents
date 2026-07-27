---
name: session-mycology
description: "Turn hard turns into reusable skill spores using only file-based records; works with receipt-river and periodic review agents."
---

# Skill: Session Mycology

## Goal
Convert painful or repetitive turns into incubated skill spores without depending on any plugin, extension, or runtime-specific harness.

## Use This Skill When
- A turn felt harder than it should have.
- You notice a repeated pattern, trap, or workaround.
- You want future agents to handle this situation with less friction.
- You are finishing substantive work and have a receipts.edn to draw from.

## Do Not Use This Skill When
- The interaction is tiny, casual, or clearly one-off.
- The friction is caused only by missing permissions, secrets, or external outages.
- You need a finished production skill immediately instead of an incubated draft.

## Inputs
- The current task and turn outcome.
- Tool usage and visible friction points.
- Existing spores in the project's `.ημ/session-mycology/spores/`.
- The workspace `receipts.edn` (use the `receipt-river` skill).

## Runtime
- No plugin, extension, or special runtime is required.
- All state lives in plain files under the project's `.ημ/session-mycology/`.
- Works the same in OpenCode, Codex, or any file-writing harness.

## Project discovery
The bb scripts and skill text assume a project root that contains either `.ημ/` or `.git/`. Scripts walk up from the current working directory until they find one. If neither exists, they fail with a clear message. To bootstrap a new project, create an empty `.ημ/` directory first.

## Spore files

### Ledger
Append every sensed turn to `.ημ/session-mycology/ledger.md`:

```markdown
- ts: <ISO-8601>
  session: <session-id or cwd>
  task: <one-line summary>
  p-efficiency: <0..1>
  p-friction: <0..1>
  p-skill-candidate: <0..1>
  spore: <filename or none>
  receipt-refs: <receipt line hashes or none>
  note: <one-line reflection>
```

### Candidate spore
If `p-skill-candidate >= 0.7`, create `.ημ/session-mycology/spores/YYYYMMDD-HHMMSS-<slug>.md`:

```markdown
---
status: incubating
created: <ISO-8601>
source-session: <session-id or cwd>
source-task: <one-line summary>
p-efficiency: <0..1>
p-friction: <0..1>
p-skill-candidate: <0..1>
promoted-to: ""
rejected-reason: ""
---

## Problem
What was harder than expected?

## Pattern
What repeated or could repeat?

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
What should the next agent do differently?

## Receipt refs
- <receipt line hash or ts>
```

## bb scripts
The skill ships with Babashka scripts in `scripts/`. They are harness-agnostic: run them from anywhere inside the project.

- `sm-init.bb` — create `.ημ/session-mycology/` and `spores/`.
- `sm-log.bb` — append a ledger entry.
- `sm-spore.bb` — create a candidate spore file.
- `sm-list.bb` — list recent ledger entries and/or spores.

Examples:

```bash
# from inside the project
~/.agents/skills/session-mycology/scripts/sm-init.bb
~/.agents/skills/session-mycology/scripts/sm-log.bb \
  --task "Refactored receipt-river scripts" \
  --efficiency 0.8 \
  --friction 0.3 \
  --candidate 0.75 \
  --note "bb scripts reduced manual EDN formatting"
~/.agents/skills/session-mycology/scripts/sm-spore.bb \
  --slug "bb-receipt-scripts" \
  --task "Refactored receipt-river scripts" \
  --problem "Formatting receipts.edn by hand is error-prone" \
  --pattern "Every skill that writes project state needs small helper scripts" \
  --better-path "Ship bb scripts with each file-writing skill" \
  --candidate 0.8
~/.agents/skills/session-mycology/scripts/sm-list.bb --limit 10
```

## Per-turn workflow
1. **Sense** the last turn.
   - What was harder than expected?
   - What pattern repeated?
2. **Score** the turn.
   - `p-efficiency`: confidence the path was near-minimal.
   - `p-friction`: confidence the work was harder than it should have been.
   - `p-skill-candidate`: confidence a reusable skill/protocol would shrink future effort.
3. **Read receipts**.
   - Tail the workspace `receipts.edn`.
   - Note observations, decisions, tests, or drift entries related to the friction.
4. **Spore** the pattern.
   - If `p-skill-candidate >= 0.7` and the pattern generalizes, create a candidate spore file.
5. **Log** the reflection.
   - Append a line to `.ημ/session-mycology/ledger.md`.

## Promotion path
- Do not promote spores yourself during the same session that created them.
- Worthiness is decided by the `spore-reviewer` actor using the `skill-spore-review` skill.
- The actor lives at `.ημ/actors/spore-reviewer/` and is dispatched by the `eta-mu-actor-agent` skill.
- A promoted spore becomes a new skill in `~/.agents/skills/<name>/SKILL.md`.
- The actor scans each project root for `.ημ/session-mycology/spores/`; it no longer relies on a global spores directory.

## Backward compatibility
Older spores stored in the legacy global directory `~/.config/opencode/spores/` are still readable, but new spores should always be written to the project-local `.ημ/session-mycology/`.

## Operating rules
- Keep the retrospective mostly silent.
- Surface only the parts that help the user.
- Prefer tiny, durable lessons over grandiose self-mythology.
- Never log secrets.

## Output
- One appended line in `.ημ/session-mycology/ledger.md`.
- Zero or one new candidate spore file in `.ημ/session-mycology/spores/`.
- A short better-path note for the next similar task.

## References
- `receipt-river` skill for receipts.edn format and workflow.
- `skill-spore-review` skill for the periodic promotion review.
- `eta-mu-actor-agent` skill for the actor model behind the spore-reviewer.
- `.ημ/actors/spore-reviewer/` for the actor definition and runtime.

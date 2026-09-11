---
name: skill-spore-review
description: "Periodically review incubated skill spores and promote worthy ones to full skills. Implemented as the spore-reviewer actor under .eta-mu/actors/."
---

# Skill: Skill Spore Review

## Goal
Periodically review incubated skill spores, decide which ones deserve promotion, and turn them into real skills.

## Runtime home

This is **not** a one-shot sub-agent task. It is an **actor** that lives at:

```
.eta-mu/actors/spore-reviewer/
```

The actor's prompt is compiled from:
- `goals/` — what it wants (review, promote, reject)
- `methods/` — how it operates (read sources, score, use template)
- `responsibilities/` — constraints (no self-promotion, receipts, no secrets)
- `schedules/` and `triggers/` — when it runs
- `runtime/` — how it is backgrounded

## Use This Skill When
- The user asks you to review incubated spores.
- You are setting up the periodic review schedule.
- A scheduled review timer fires and dispatches the actor.
- A spore has been incubating for a while and needs a promotion decision.

## Do Not Use This Skill When
- You are in the same session that created the spore (avoid immediate self-promotion).
- The spore is still vague, one-off, or ungeneralizable.

## How to start the actor

### Option 1: systemd timer (recommended)

Install the actor's units:

```bash
cp .eta-mu/actors/spore-reviewer/runtime/systemd.service ~/.config/systemd/user/skill-spore-reviewer.service
cp .eta-mu/actors/spore-reviewer/runtime/systemd.timer   ~/.config/systemd/user/skill-spore-review.timer
systemctl --user daemon-reload
systemctl --user enable --now skill-spore-review.timer
```

### Option 2: manual tmux dispatch

```bash
~/.eta-mu/actors/spore-reviewer/runtime/tmux-start.sh
```

### Option 3: one-shot dispatch

```bash
~/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh spore-reviewer
```

## Review routine (for the actor)

When the `spore-reviewer` actor is dispatched, it must:

1. **Discover project roots** by scanning known workspaces for `.ημ/session-mycology/` directories.
2. **List spores** in each project's `.ημ/session-mycology/spores/`.
3. **Filter** to `status: incubating` spores.
4. **Read the ledger** in `.ημ/session-mycology/ledger.md` to see how often each pattern has appeared.
4. **Read receipts** referenced in each spore to verify the friction was real.
5. **Score each spore**:
   - `p-recurrence`: has the pattern appeared more than once?
   - `p-generalizable`: would a skill help across multiple tasks?
   - `p-worth-promoting`: combined confidence the spore should become a skill.
6. **Decide**:
   - `promote` if `p-worth-promoting >= 0.8`.
   - `reject` if it is too narrow, too vague, or no longer relevant.
   - `incubate` otherwise (no change).
7. **For promoted spores**:
   - Pick a kebab-case skill name.
   - Create `~/.agents/skills/<name>/SKILL.md` with proper frontmatter.
   - Update the spore frontmatter:
     ```yaml
     status: promoted
     promoted-to: ~/.agents/skills/<name>/SKILL.md
     promoted-at: <ISO-8601>
     ```
8. **For rejected spores**:
   - Update the spore frontmatter:
     ```yaml
     status: rejected
     rejected-reason: "<why it did not qualify>"
     rejected-at: <ISO-8601>
     ```
9. **Write a review receipt** to the project's `.ημ/session-mycology/review-receipts.edn`:
    - ts, kind `:spore-review`, spores reviewed, promoted, rejected, and notes.

## Promotion criteria

Promote a spore only when it meets most of these:
- The friction has recurred or is clearly going to recur.
- A concrete, repeatable protocol would reduce future effort.
- The outline fits the standard skill template (Goal, Use When, Do Not Use, Steps, Output).
- It does not duplicate an existing skill.

## Output
- Updated spore frontmatter (`promoted` or `rejected`).
- Zero or more new skills in `~/.agents/skills/`.
- A review receipt.
- A recorded session under `.eta-mu/actors/spore-reviewer/sessions/`.

## Troubleshooting
- **ProviderModelNotFoundError**: The default model in `opencode.jsonc` is not available. Specify a working model with `--model provider/model` in the dispatch command, or fix the default model.
- **Connection refused**: Start `opencode-server.service` first.
- **401 Unauthorized**: `OPENCODE_SERVER_PASSWORD` is missing or wrong in the dispatch shell.

## References
- `eta-mu-actor-agent` skill for the actor model and dispatch scripts.
- `opencode-server` skill for the headless server and password handling.
- `session-mycology` skill for creating spores.
- `receipt-river` skill for receipt format and rules.
- `skill-authoring` skill for creating valid skills.
- `.eta-mu/actors/spore-reviewer/` for the actor definition, prompt parts, and runtime files.

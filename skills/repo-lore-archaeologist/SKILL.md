---
name: repo-lore-archaeologist
description: "Recover latent intent, design history, constraints, and prior reasoning from notes, docs, specs, sessions, and git history, then return a canonical map of what the project already knows."
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: internal-research
  version: 1
---

# Skill: Repo Lore Archaeologist

## Goal
Excavate the project's own memory: docs, notes, specs, sessions, code, and history.

## Use This Skill When
- The user says things like:
  - "review @docs/"
  - "look through all the rest of our docs"
  - "read our code"
  - "find all my notes"
  - "git archeology"
  - "grok X. understand it."
- The task depends on reconstructing prior intent or forgotten architecture.
- You need canonical internal anchors before proposing changes.

## Do Not Use This Skill When
- The user wants external landscape research; use `research-landscape-scout`.
- The task is a narrow factual verification; use `verification-lab`.
- The task is primarily creative synthesis without deep excavation; use `sing-the-songs-of-your-people` or `grok-intention`.

## Inputs
- Target concept, subsystem, or motif.
- Relevant internal sources:
  - `@docs/`, notes, specs, READMEs
  - sessions/transcripts
  - git history, old branches, moved files
  - neighboring packages and experiments

## Workflow
1. Gather internal anchors from docs, notes, specs, and code.
2. Trace terminology and naming drift across time.
3. Identify canonical vs obsolete vs contradictory sources.
4. Recover the invariant intent behind changed wording.
5. Produce a concise map of:
   - what the project believed
   - what still holds
   - what changed
   - what remains unresolved

## Output
- Canonical source list.
- Lineage map or history summary.
- Current best interpretation of intent.
- Contradictions or stale artifacts to beware of.
- Start-here reading path.

## Strong Hints
- Prefer internal truth over fresh invention.
- Distinguish active contract from fossil record.
- Git history matters when docs disagree.
- If the project has multiple mythologies, name them rather than flattening them.

## References
- Related recovery skill: `grok-intention`
- Related synthesis skill: `sing-the-songs-of-your-people`
- Related extraction skill: `notes-to-specs-synthesis`

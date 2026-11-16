---
name: pr-muse-connect
description: Ambient first stage of pr-flow. While the user thinks aloud, connect what they say to existing documents, code, programs, cards, issues and PRs; record the connections with epistemic tiers; and hand a link map to sprint planning.
license: GPL-3.0-or-later
metadata:
  tier: provisional
  origin: pr-flow skill graph, 2026-10-01
---

# Muse and connect

## Use this skill when

- The user muses, reflects, or "thinks out loud" about the work, at any point in a PR's life. This includes mid-review asides such as "as I am writing this… this is what epiphany is about."
- A request names something by memory ("that old package I built called clobber") and the thing must be found before it can be used.

## Do not use this skill when

- The user gives a precise, already-grounded instruction. Act on it.

## Procedure

1. **Recover before asking.** Use `grok-intention` and `repo-lore-archaeologist` first.
   - Search the live checkouts and git history: `git log --all -- <path>`, then `git ls-tree <rev>^ <path>`, then `git archive`.
   - Search sibling checkouts (`~/devel`, `~/spaces`, Foresight submodules), receipts, spores and session logs.
   - Deleted things are usually recoverable from the commit before their removal.
2. **Label every connection** on the epiphany ladder:
   - *observed* means a file, commit, command output or quote, with its locator.
   - *derived* means a similarity, summary or inference, with its method.
   - *provisional* means a proposed relation or plan.
   - Nothing is *accepted* until the user says so. Similar names are not identity.
3. **Write the link map.** Write it as a short note in the project's inbox: `docs/inbox/` or `docs/notes/` by repository convention, timestamped. Keep the user's own words verbatim where they carry intent. Link to paths, PRs, issues and card uuids so each one is addressable.
4. **Capture standing context.** Durable vision, preferences or rules go into the harness memory and are linked, not repeated.
5. **Hand off.** Once an outcome and at least one card can be named, go to `pr-sprint-planning`. The user can reopen muse from any stage.

## Output

- A link map: observed and derived connections, open questions, and a proposed outcome.
- An inbox note.
- Optionally a memory entry.

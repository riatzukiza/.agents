---
name: pr-sprint-planning
description: pr-flow stages plan, planning-review and card-ready. Turn a link map into an epic and substories (Rheos Markdown cards), open a PR carrying them (ready, auto-merge off), ask CodeRabbit and other agents to review the agile artifacts like a sprint-planning session, settle every comment, then move the stories to ready through Rheos.
license: GPL-3.0-or-later
metadata:
  tier: provisional
  origin: pr-flow skill graph, 2026-10-01
---

# Sprint planning in a PR

## Use this skill when

- Work is big enough for an epic, or for any card that will be implemented through a PR.
- Existing cards need splitting, re-estimating or re-linking after review.

## Do not use this skill when

- It is a one-line fix with no card. Go straight to `pr-red-green`, and still review it in a PR.

## Plan

1. **Find the board.** Read the repository's `openhax.kanban.json` (`tasksDir`, `fsm`). Cards are Markdown with frontmatter. Hand-authoring a card is a supported Rheos entry point; status changes are not.
2. **Write the epic and stories.** Each card has an explicit `uuid:`, `title`, `priority`, `points` and `labels`, plus `epic:`, `parent:` and `blocked_by:` as uuids. Omit empty fields.
   - The body has these sections: Context, Outcome, Scope, Non-goals, Acceptance criteria (testable), Verification, Risks.
   - Follow the repository's existing card style. Look at two neighbours before writing.
3. **Branch and commit.** Use a branch such as `plan/<epic-slug>`. Stage only the cards. If the cards depend on cards in an open PR, base the branch on that PR's head (a stacked PR) and say so in the body.

## Planning review

4. **Open the PR ready for review, with auto-merge off.** The user decided this on 2026-10-01: drafts are ignored by Codex and other review agents. Then request the planning review explicitly, because CodeRabbit does not auto-review stacked PRs on non-default bases:
   `pr.cljs request REPO N planning`.
   The brief asks for:
   - outcome and scope clarity;
   - testable acceptance criteria;
   - fair points, or whether a card should split;
   - complete and correct dependency links;
   - missing risks and non-goals;
   - a P0–P3 label on every finding.
   Ask the other configured agents too (Codex, Kimi/OpenCode workflows) when the repository wires them in. If a reviewer leaves a brief question unanswered (CodeRabbit often skips the estimate question), apply the board's own rule and say so in the `Handled:` comment.
   Read the repository's card guide first (shx: `docs/kanban/writing-cards.md`). Create cards with `rheos create` when the repository asks for it, and never hand-write frontmatter there. A 13-point card that is not an epic means breakdown did not happen: make it an epic with children.
5. **Wait, then settle.** Run `pr.cljs wait`, then follow `pr-review-settlement`. Splits and re-estimates are real edits to the cards, made in new commits. Loop up to `:review/max-loops` rounds (5, user-confirmed 2026-10-01). If blockers remain after that, stop and ask the user. Don't run a sixth round.

## Ready

6. **Move each reviewed story to ready** through Rheos: `eta-mu kanban …`. Never edit `status:` by hand. A harness without Rheos may stop here and report the transition it could not perform.
7. **Hand off** each ready story to `pr-red-green`. The planning PR may merge on its own through the merge gate, or carry on as the implementation PR.

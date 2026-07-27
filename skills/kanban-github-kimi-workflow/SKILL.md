---
name: kanban-github-kimi-workflow
description: Use when syncing markdown Kanban cards to GitHub issues and coordinating Kimi/OpenCode issue triage, PR review, CodeRabbit, Discord visibility, and merge gates.
license: GPL-3.0-or-later
---

# Kanban → GitHub → Kimi Workflow

## Goal
Keep local markdown Kanban cards, GitHub issues, Kimi automation, CodeRabbit review, Discord visibility, and merge gates aligned.

## Use This Skill When
- The user asks to sync Kanban cards to GitHub issues.
- The user asks why Kimi, CodeRabbit, Discord, or GitHub labels are behaving a certain way.
- You are opening/fixing a PR linked to a Kanban/GitHub issue.
- You see `openhax-kanban-sync` markers, `kanban`, `status:*`, or `priority:*` labels.

## Workflow
1. Locate the repo's Kanban directory from `/home/err/devel/AGENTS.md`.
2. Dry-run before writing:
   ```bash
   eta-mu kanban sync github --tasks-dir <kanban-dir> --repo <owner/repo> --dry-run
   ```
3. For live writes, throttle to avoid GitHub secondary content-creation limits:
   ```bash
   eta-mu kanban sync github --tasks-dir <kanban-dir> --repo <owner/repo> --max-writes 25 --write-delay-ms 5000
   ```
4. Treat `<!-- openhax-kanban-sync uuid="..." -->` as idempotent identity; never duplicate an issue with the same task UUID.
5. Preserve sync labels:
   - `kanban`
   - `status:<status>`
   - `priority:<priority>`
   - normalized task frontmatter labels
6. Kimi issue agent should use synced issues as task intent and avoid closing ambiguous issues.
7. Kimi PR review should submit concrete findings as inline GitHub review comments.
8. CodeRabbit and Kimi inline review threads must be resolved before merge wherever GitHub branch protection supports required conversation resolution.
9. Discord mirrors GitHub events and review comments through `DISCORD_REVIEW_WEBHOOK_URL`; never print webhook URLs or tokens.

## Checks
- `eta-mu kanban sync github ... --dry-run` reports expected creates/updates.
- `gh issue list --repo <owner/repo> --label kanban` shows synced issues.
- PR checks include Kimi/OpenCode and CodeRabbit where configured.
- Review comments are resolved before merge.

## Failure Modes
- GitHub secondary rate limit: stop bulk writes; resume later with smaller `--max-writes` and larger `--write-delay-ms`.
- Missing npm command: use the repo source command `node packages/kanban/dist/cli.js sync github ...` after building `@open-hax/kanban-legacy`.
- Private repo branch protection blocked: GitHub may require Pro/public visibility for required conversation resolution.

---
name: github-discord-review-visibility
description: Use when configuring or debugging Discord visibility for GitHub events, Kimi/OpenCode review comments, CodeRabbit review threads, and workflow-run notifications.
license: GPL-3.0-or-later
---

# GitHub Discord Review Visibility

## Goal
Ensure GitHub repository activity and inline code review comments are visible in Discord without leaking secrets.

## Use This Skill When
- The user asks for GitHub events to appear in Discord.
- Inline PR review comments are not appearing in Discord.
- A Discord webhook secret needs rotation.
- You need to audit event mirror workflows across repos.

## Canonical Files
- `.github/workflows/github-events-discord.yml` — broad GitHub event mirror.
- `.github/workflows/code-review-comments-discord.yml` — inline PR review comment bridge.
- `.github/workflows/opencode-code-review.yml` — Kimi PR review plus new-inline-comment Discord summary.
- `.config/discord.bot.json` — local private config; if it contains `webhookUrl`, use that as the source of truth.

## Secret Rules
- Repository secret: `DISCORD_REVIEW_WEBHOOK_URL`.
- Never print the webhook URL or bot token.
- Validate webhook metadata only by printing non-secret fields such as name/id/channel/guild.

## Audit Commands
```bash
gh secret list --repo <owner/repo> | grep DISCORD_REVIEW_WEBHOOK_URL
gh api repos/<owner>/<repo>/contents/.github/workflows/github-events-discord.yml?ref=<branch> --jq .sha
gh api repos/<owner>/<repo>/contents/.github/workflows/code-review-comments-discord.yml?ref=<branch> --jq .sha
```

## Workflow Expectations
- GitHub events mirrored: issues, issue comments, PR lifecycle, PR reviews, releases, pushes, selected workflow runs.
- Inline review comments mirrored: any `pull_request_review_comment.created` event.
- Kimi review should prefer inline review comments for concrete findings.
- Branch protection should require conversation resolution where GitHub permits it.

## Failure Modes
- Secret absent: workflows skip because `DISCORD_REVIEW_WEBHOOK_URL` is not set.
- Wrong channel: update `.config/discord.bot.json.webhookUrl`, then roll out secret to repos.
- GitHub plan limit: private repos may not support branch protection required conversation resolution.

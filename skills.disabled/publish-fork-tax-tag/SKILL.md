---
name: publish-fork-tax-tag
description: "Announce fork tax tags (commits, releases, snapshots) to social platforms. Use after paying the fork tax to share the milestone with team or community."
compatibility: opencode
metadata:
  domain: social
  workflow: fork-tax-announce
  version: 1
---

# Skill: Publish Fork Tax Tag

## Goal
Announce fork tax tags (Π mode commits, releases, milestones) to Discord and/or Bluesky after persistence is complete.

## Use This Skill When
- Fork tax has been paid (commit + tag + push complete).
- A release or milestone should be announced.
- The user asks to "announce the release" or "share the tag".
- After `fork-tax` skill completes successfully.

## Do Not Use This Skill When
- The fork tax hasn't been paid yet.
- The commit/tag is for private or WIP work.
- The user says to keep it quiet.

## Fork Tax Context

The `fork-tax` skill persists working state into git:
- Creates a commit with all changes.
- Tags the commit (e.g., `v1.2.3` or `snapshot-2024-01-15`).
- Pushes to remote.
- May create manifest artifacts.

After this completes, use this skill to announce the tag.

## Announcement Format

### For Discord
```
🏷️ **Tag: v1.2.3**

Brief description of what changed or why this matters.

Commit: abc123
Changes: +42 -8
```

### For Bluesky
```
🏷️ v1.2.3 released

Brief description. Link to commit/release.
#dev #release
```

## Steps

1. **Verify fork tax complete**:
   - Confirm tag exists: `git tag -l | tail -5`
   - Confirm push: `git log --oneline -3`

2. **Gather tag info**:
   ```
   git show <tag> --stat
   git log <previous-tag>..<tag> --oneline
   ```

3. **Prepare announcement**:
   - Tag name.
   - Brief summary of changes.
   - Commit SHA (short).
   - Optional: diff stats, notable changes.

4. **Post to platforms**:
   ```
   # Discord team channel
   discord action=send channelId=<id> content="🏷️ **Tag: v1.2.3**\n\n..."

   # Bluesky public
   bluesky action=post text="🏷️ v1.2.3 released\n\n..."
   ```

## Platform Defaults

| Tag Type | Discord | Bluesky |
|----------|---------|---------|
| Release (vX.Y.Z) | ✓ Announce | ✓ Public |
| Snapshot | ✓ Dev channel | Optional |
| WIP/scratch | Optional | ✗ |
| Major milestone | ✓ Announce | ✓ Public |

## Guardrails
- **Don't announce private work** - check repo visibility.
- **Keep it concise** - especially for Bluesky.
- **Include actionable info** - links, commit SHAs.
- **Don't duplicate** - check if already announced.

## Integration with Fork Tax

This skill is designed to follow `fork-tax`:

```
User: pay the fork tax

1. fork-tax skill runs → commit + tag + push
2. fork-tax reports success with tag name
3. Ask: "Announce this tag? (Discord/Bluesky/Both)"
4. If yes → this skill runs
```

## Example Workflow

```
User: Pay the fork tax and announce it

1. fork-tax:
   - Commit: "feat: add user dashboard"
   - Tag: v2.1.0
   - Push: origin/main

2. publish-fork-tax-tag:
   - Gather: tag=v2.1.0, commit=abc123, +156 -23
   - Discord: "🏷️ **Tag: v2.1.0**\n\nUser dashboard with analytics..."
   - Bluesky: "🏷️ v2.1.0 released\n\nUser dashboard with analytics..."
```

## Output
- Confirmation of posts made.
- Links/URIs for reference.
- Summary of platforms posted to.

## References
- `fork-tax` skill for persistence workflow.
- `discord-publish-tool` for Discord usage.
- `bluesky-publish-tool` for Bluesky usage.

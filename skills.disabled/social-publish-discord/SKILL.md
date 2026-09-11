---
name: social-publish-discord
description: "Publish Hormuz clock updates into Discord channels with sanitized mentions, optional images, and dry-run-first behavior"
compatibility: opencode
metadata:
  domain: hormuz-clock
  platform: discord
  version: 2
  tools: ["discord"]
---

# Skill: Social Publish Discord

## Goal
Send a compact Hormuz clock status update to a Discord channel without leaking secrets or generating surprise mentions.

## Use This Skill When
- The user asks to post a daily risk clock update into Discord.
- A material state change needs a one-shot alert in a Discord server.
- You need to cross-post a markdown brief into an internal or public channel.

## Do Not Use This Skill When
- The task only prepares a report without distribution.
- The target platform is not Discord.
- The work is only a generic text rewrite with no Discord payload or channel target.

## Prerequisites
- `OPENHAX_DISCORD_TOKEN` or `DISCORD_BOT_TOKEN` environment variable.
- Bot must have access to target channels.

## Inputs
- Latest snapshot markdown (from `reports/v4_snapshot.md` or similar).
- Rendered clock image path or URL (from `assets/hormuz_risk_clock_v4.png` or similar).
- Target channel ID (use `discord action=list-channels` to discover).
- Optional embed for richer formatting.

## Steps

1. **Check credentials**: Verify `OPENHAX_DISCORD_TOKEN` is set.

2. **Discover channels** (if needed):
   ```
   discord action=list-channels
   ```

3. **Build a Discord-safe payload** from the latest snapshot:
   - Extract key probabilities and signals.
   - Keep under 2000 characters.
   - No mentions by default.

4. **Post the message**:
   ```
   discord action=send channelId=<id> content=" Hormuz Risk Clock Update..."
   ```

5. **With image**:
   ```
   discord action=send-image channelId=<id> content="..." imageUrl="https://..."
   ```

## Guardrails
- Never hardcode bot tokens.
- Default to no mentions (no @everyone, no role pings).
- Respect Discord's 2000 character limit.
- If the image is missing, fall back to plain text.
- Default to dry-run (show what would be posted) unless user explicitly confirms.

## Output
- Discord-safe message content.
- Publish status with message ID.
- Any missing channel, token, or verification notes.

## Example

```
User: Post the latest clock update to Discord

1. Read reports/v4_snapshot.md
2. Extract: "Strait closure probability: 23% (+5%), Timeline: 2-4 weeks"
3. discord action=send-image channelId=1234567890 content=" Hormuz Risk Clock Update\n\nStrait closure: 23% (+5%)\nTimeline: 2-4 weeks\n\n#hormuz #risk" imageUrl="https://..."
```

## References
- `discord-publish-tool` skill for general Discord usage.
- `hormuz-risk-clock` skill for clock updates.

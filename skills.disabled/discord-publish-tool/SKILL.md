---
name: discord-publish-tool
description: "Post messages and images to Discord channels using the discord tool. Use when you want to share analysis results, screenshots, or updates with a Discord community."
compatibility: opencode
metadata:
  domain: social
  platform: discord
  version: 1
---

# Skill: Discord Publish Tool

## Goal
Post content to Discord channels via the `discord` tool with proper channel discovery and message formatting.

## Use This Skill When
- You have analysis results or screenshots worth sharing.
- Fork tax tags should be announced to a team channel.
- You want to cross-post content to Discord from within pi.
- The user asks to "post to Discord" or "share on Discord".

## Do Not Use This Skill When
- The user only wants Bluesky posting (use `bluesky-publish-tool`).
- The user wants to post to all platforms at once (use `social_publish` tool directly).
- Discord credentials are not configured and the user won't provide them.

## Prerequisites
- `OPENHAX_DISCORD_TOKEN` or `DISCORD_BOT_TOKEN` environment variable must be set.
- Bot must have access to target channels (Message Content Intent enabled).

## Inputs
- `content`: Text message to post.
- `channelId`: Target channel ID (use `list-channels` to discover).
- `imageUrl`: Optional image URL to attach.
- `embed`: Optional embed object for richer formatting.

## Steps

1. **Check credentials**: Verify `OPENHAX_DISCORD_TOKEN` is set. If not, inform the user.

2. **Discover channels** (if needed):
   ```
   discord action=list-channels
   ```
   Returns available text channels with guild names.

3. **Send message**:
   ```
   discord action=send channelId=<id> content="Message text"
   ```

4. **Send with image**:
   ```
   discord action=send-image channelId=<id> content="Caption" imageUrl="https://..."
   ```

## Guardrails
- Never hardcode bot tokens in messages or code.
- Use `list-channels` first if channel ID is unknown.
- Respect Discord's 2000 character limit for messages.
- For long content, split into multiple messages or use embeds.

## Output
- Confirmation with message ID.
- Error details if posting failed.

## Example Workflow

```
User: Post the latest screenshot to the dev channel

1. discord action=list-channels
   → Found #dev (1234567890)

2. discord action=send-image channelId=1234567890 content="Latest UI work" imageUrl="https://..."
   → Posted to Discord: Latest UI work [with image]
```

## References
- Discord API docs: https://discord.com/developers/docs/reference

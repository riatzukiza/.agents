---
name: bluesky-publish-tool
description: "Post messages and images to Bluesky using the bluesky tool. Use when you want to share content publicly on Bluesky."
compatibility: opencode
metadata:
  domain: social
  platform: bluesky
  version: 1
---

# Skill: Bluesky Publish Tool

## Goal
Post content to Bluesky via the `bluesky` tool with proper text length handling and image support.

## Use This Skill When
- You want to publicly share analysis results or findings.
- Fork tax tags should be announced publicly.
- The user asks to "post to Bluesky" or "share on Bluesky".
- You have an image from analysis that deserves wider visibility.

## Do Not Use This Skill When
- The user only wants Discord posting (use `discord-publish-tool`).
- The user wants to post to all platforms at once (use `social_publish` tool).
- Bluesky credentials are not configured and the user won't provide them.

## Prerequisites
- `BLUESKY_IDENTIFIER` environment variable (handle, e.g., `user.bsky.social`).
- `BLUESKY_APP_PASSWORD` environment variable (app password from Bluesky settings).

## Inputs
- `text`: Post text content (max 300 characters - will be truncated if longer).
- `imageUrl`: Optional image URL to attach (will be downloaded and uploaded).
- `imageAlt`: Optional alt text for accessibility.
- `replyToUri`/`replyToCid`: Optional reply target.

## Steps

1. **Check credentials**: Verify both `BLUESKY_IDENTIFIER` and `BLUESKY_APP_PASSWORD` are set.

2. **Check profile** (optional):
   ```
   bluesky action=profile
   ```
   Returns current user info.

3. **Post text**:
   ```
   bluesky action=post text="Your message here"
   ```

4. **Post with image**:
   ```
   bluesky action=post-image text="Caption" imageUrl="https://..." imageAlt="Description"
   ```

## Guardrails
- Never hardcode app passwords.
- Respect 300 character limit - truncate gracefully with "..." if needed.
- Provide meaningful alt text for images when possible.
- Use reply parameters for threaded conversations.

## Output
- Confirmation with post URI.
- Error details if posting failed.

## Example Workflow

```
User: Post this analysis to Bluesky

1. Check text length, truncate if needed

2. bluesky action=post text="Analysis complete: 95% confidence the UI change improved UX"
   → Posted to Bluesky: Analysis complete...
   URI: at://did:plc:xxx/app.bsky.feed.post/xxx
```

## References
- Bluesky API docs: https://docs.bsky.app/docs/api/

---
name: publish-image-analysis
description: "Publish image analysis results to social platforms (Discord, Bluesky). Use when image analysis produces good results that should be shared with team or publicly."
compatibility: opencode
metadata:
  domain: social
  workflow: image-analysis-publish
  version: 1
---

# Skill: Publish Image Analysis

## Goal
Share image analysis results - especially UI screenshots, charts, or visual findings - to appropriate social platforms.

## Use This Skill When
- Image analysis (via `analyze_image` or `vision`) produces interesting results.
- UI work looks good and should be shared with the team.
- A chart or diagram from analysis deserves wider visibility.
- The user says "post this" or "share this" after image analysis.
- The user explicitly approves posting after you ask.

## Do Not Use This Skill When
- Image analysis contains sensitive or private information.
- The user hasn't confirmed they want to share publicly.
- The image shows errors, failures, or things that shouldn't be broadcast.
- No credentials are configured for any platform.

## Workflow

### After Image Analysis

1. **Assess shareability**:
   - Is this interesting UI work? → Good for Discord team channel.
   - Is this a public-facing update? → Good for Bluesky.
   - Does it contain sensitive data? → Do NOT share.

2. **Ask for confirmation**:
   ```
   "The UI screenshot looks good. Should I post this to:
   - Discord (#dev channel)?
   - Bluesky (public)?
   - Both?"
   ```

3. **Prepare content**:
   - Summarize what the image shows.
   - Add context (what changed, why it matters).
   - Keep it concise for Bluesky (300 char limit).

4. **Post using appropriate tool**:
   ```
   # For Discord team channel
   discord action=send-image channelId=<id> content="..." imageUrl="..."

   # For Bluesky public post
   bluesky action=post-image text="..." imageUrl="..." imageAlt="..."
   ```

## Platform Selection Guide

| Content Type | Discord | Bluesky |
|-------------|---------|---------|
| Internal UI work | ✓ Team channel | ✗ |
| Public feature preview | ✓ Announce channel | ✓ |
| Bug screenshots | ✓ Debug channel | ✗ |
| Release visuals | ✓ Announce channel | ✓ |
| Personal projects | Optional | ✓ |

## Guardrails
- **Always ask before posting** unless the user explicitly requested it.
- **Never post sensitive data**: credentials, private repos, personal info.
- **Respect character limits**: 300 for Bluesky, 2000 for Discord.
- **Provide alt text** for images on Bluesky.
- **Check credentials first** to avoid partial failures.

## Integration with Fork Tax

When posting fork tax tags:
- Include the tag name and brief description.
- Link to the commit or release if relevant.
- Use consistent formatting across platforms.

## Example Prompts

User: "That UI looks good, post it"
→ Ask: "Which platform(s)?"
→ Post with appropriate context.

User: "Share the analysis results"
→ Summarize key findings.
→ Ask which platform(s).
→ Post with summary as caption.

## Output
- Confirmation of posts made.
- URIs/message IDs for reference.
- Summary of any skipped platforms.

## References
- `discord-publish-tool` for Discord-specific usage.
- `bluesky-publish-tool` for Bluesky-specific usage.
- `fork-tax` skill for understanding fork tax context.

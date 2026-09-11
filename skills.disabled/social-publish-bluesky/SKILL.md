---
name: social-publish-bluesky
description: "Turn the latest Hormuz clock snapshot into a concise Bluesky post or thread with dry-run-first behavior"
compatibility: opencode
metadata:
  domain: hormuz-clock
  platform: bluesky
  version: 2
  tools: ["bluesky"]
---

# Skill: Social Publish Bluesky

## Goal
Convert the current Hormuz snapshot into a Bluesky-ready post that stays concise, source-aware, and explicit about uncertainty.

## Use This Skill When
- The user asks to publish a daily clock update to Bluesky.
- You need a short thread covering updated probabilities or signals.
- A longer report needs to be compressed into Bluesky-sized posts.

## Do Not Use This Skill When
- The task is only to generate or update the underlying clock.
- The request targets a different platform.
- Credentials or target content are missing and only a generic social draft is needed.

## Prerequisites
- `BLUESKY_IDENTIFIER` environment variable (handle, e.g., `user.bsky.social`).
- `BLUESKY_APP_PASSWORD` environment variable.

## Inputs
- `reports/v4_snapshot.md` or another markdown brief.
- `assets/hormuz_risk_clock_v4.png` or a newer image.
- Optional tone such as `clinical`, `public explainer`, or `ops/status`.

## Steps

1. **Check credentials**: Verify `BLUESKY_IDENTIFIER` and `BLUESKY_APP_PASSWORD` are set.

2. **Read the latest snapshot**:
   - Extract key probabilities and signals.
   - Keep under 300 characters for Bluesky.

3. **Build the post**:
   - One strong primary post.
   - Optional continuation as reply if more nuance needed.

4. **Post to Bluesky**:
   ```
   bluesky action=post text=" Hormuz Risk Clock: Strait closure 23% (+5%). Timeline 2-4 weeks. #hormuz"
   ```

5. **With image**:
   ```
   bluesky action=post-image text="..." imageUrl="https://..." imageAlt="Hormuz risk clock visualization"
   ```

## Guardrails
- Separate observed facts from model choices.
- Do not present branch probabilities as certainty.
- Prefer one strong post plus image over a thread unless nuance requires.
- Keep under 300 characters.
- Back off on rate limits (429 responses).

## Output
- Bluesky-ready post text.
- Publish status with post URI.
- Any missing-env or verification notes.

## Example

```
User: Post the clock update to Bluesky

1. Read reports/v4_snapshot.md
2. Compress: " Hormuz Risk Clock: Strait closure 23% (+5%). Key signal: tanker traffic -12%. #hormuz #geopolitics"
3. bluesky action=post-image text="..." imageUrl="https://..." imageAlt="Risk clock chart"
```

## References
- `bluesky-publish-tool` skill for general Bluesky usage.
- `hormuz-risk-clock` skill for clock updates.

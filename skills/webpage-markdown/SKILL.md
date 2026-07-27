---
name: webpage-markdown
description: Fetch a URL using the `webfetch` tool (cleaned static HTML, text, JSON, PDFs). Use `agent-browser` for JS-rendered/auth pages.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  version: 1
---

# Skill: Web Fetch (Reader)

## Use This Skill When
- You have a URL and need to **read/quote/summarize** its contents.
- You notice the anti-pattern: *“I can’t read web pages directly…”* despite tools being available.

## Primary Path (Static Pages)
1. Call the tool:

   - `webfetch { url: "https://…" }`

2. If the output is truncated, the tool returns an `outputPath` in its details (and a note in the text output). Use `read` to open it.

## Fallbacks
- **JS-rendered sites / SPAs / pages behind login**: use the `agent-browser` skill.
  - `agent-browser open <url>`
  - `agent-browser get text body > page.txt`

## Notes
- `webfetch` cleans static HTML before `pandoc` conversion and extracts PDFs via `pdftotext`.
- `webpage_markdown` remains as a deprecated compatibility alias.
- Prefer citing the returned `finalUrl` (after redirects) when referencing the source.

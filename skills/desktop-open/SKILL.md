---
name: desktop-open
description: Open URLs and files for the user via the `desktop_open` tool (Firefox for URLs, xdg-open for files).
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  version: 1
---

# Skill: Desktop Open (Launcher)

## Use This Skill When
- The user asks to open a URL in Firefox.
- The user asks to open a local file (PDF/image/etc.) in the default desktop app.

## Examples

### Open a URL in Firefox
- `desktop_open { target: "https://github.com/..." }`

### Open a file in the default app
- `desktop_open { target: "~/Downloads/report.pdf", app: "xdg-open" }`

## Notes
- The tool detaches the app so the agent doesn’t hang waiting for the GUI process.

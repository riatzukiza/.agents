---
name: desktop-screenshot
description: Take desktop screenshots via the `desktop_screenshot` tool (Spectacle on KDE), then view via `render_image` (opens external viewer in Alacritty).
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  version: 1
---

# Skill: Desktop Screenshot (KDE/i3)

## Use This Skill When
- The user asks for a screenshot (fullscreen, active window, region).
- You need an OS-level capture for UI debugging.

## Core Workflow
1. Capture:
   - `desktop_screenshot { mode: "activewindow" }`
   - `desktop_screenshot { mode: "region" }`
   - `desktop_screenshot { mode: "fullscreen" }`

2. Show to user / inspect:
   - `render_image { source: "<path from screenshot>" }`
   - In Alacritty, `render_image` will also open an external viewer.

## Tips
- Use `mode=region` when you only need a portion (faster to analyze, smaller file).
- If you need a persistent artifact, pass an explicit `path`.

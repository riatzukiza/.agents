---
name: i3-window-manager
description: "Control i3 (and i3-resurrect) via the `i3` and `i3_resurrect` tools: move/focus windows, query workspaces, and save/restore workspace state."
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  version: 1
---

# Skill: i3 Window Manager Ops

## Use This Skill When
- The user asks to **move/focus windows**, **switch workspaces**, or **inspect i3 state**.
- The user asks to **save/restore workspace state** (layout + programs).

## Core Tools

### 1) Situational awareness
- `i3 { action: "workspaces" }`
- `i3 { action: "windows" }`
- `i3 { action: "focused" }`

### 2) Actuation
- `i3 { action: "msg", command: "focus left" }`
- `i3 { action: "msg", command: "move container to workspace 2" }`
- `i3 { action: "msg", command: "workspace 1" }`

### 3) Save/restore
- `i3_resurrect { action: "save" }` (current workspace)
- `i3_resurrect { action: "restore" }` (current workspace)
- For named snapshots, use the `directory` parameter.

## Notes
- Prefer querying `windows` before acting if ambiguity exists.
- If you need *full* i3 JSON state, use `i3 { action: "tree" }` and read the saved `outputPath` if truncated.

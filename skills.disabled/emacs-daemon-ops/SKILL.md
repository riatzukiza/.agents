---
name: emacs-daemon-ops
description: Interact with the running Emacs daemon via `emacs_eval` and `emacs_open` (emacsclient).
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  version: 1
---

# Skill: Emacs Daemon Ops

## Use This Skill When
- The user asks you to manipulate Emacs state (open a file, run org-capture, query buffers).
- You need an automation bridge into the existing `emacs.service` daemon.

## Tools

### Evaluate elisp
```json
{ "elisp": "(emacs-version)" }
```

Call:
- `emacs_eval { elisp: "(emacs-version)" }`

### Open a file
- `emacs_open { path: "~/devel/foo/bar.md" }`
- `emacs_open { path: "~/devel/foo/bar.md", line: 120, column: 0 }`
- `emacs_open { path: "~/devel/foo/bar.md", newFrame: true }`

## Notes
- Tools use `--alternate-editor=` so they fail fast if the daemon is down (instead of spawning a new Emacs instance).

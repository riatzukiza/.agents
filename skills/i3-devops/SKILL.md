---
name: i3-devops
description: Debug i3 config issues with the bb toolkit in ~/.config/i3/devops (config->EDN IR, static lint, live check) and the version-matched Docker sandbox in ~/.config/i3/sandbox (boot i3 4.23 headless, synthetic keypress probes, snapshots, screenshots). Use when an i3 binding is silently dead, a reload shows parse errors, or a config change is risky.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  version: 1
---

# Skill: i3 DevOps Kit

## Use This Skill When
- An i3 binding is **silently dead** or a reload surfaces parse errors.
- The user wants to change i3 config and verify it **before** it touches the live session.
- You need to reproduce an i3 behavior in a throwaway environment (screenshots, IPC snapshots, synthetic input).

## Canonical Bug Classes (learned 2026-08-09, codified as lint rules)

1. **Commas are command separators in i3's binding-command parser.** Quoting
   does NOT protect them unless the quote wraps the ENTIRE command.
   - `bindsym $mod+x exec rofi -modi "combi,window,drun"` → PARSE ERROR
   - `bindsym $mod+x exec "rofi -modi combi,window,drun"` → OK
   - `bindsym $mod+x exec "i3-nagbar -B 'Yes, exit' 'i3-msg exit'"` → OK (stock nagbar)
   - Mid-command quotes WITHOUT commas are fine (`curl -s "http://...?delta=5"`).
2. **`~` mid-word is not expanded by sh.** `web:~/.local/bin/x` passes a literal
   tilde to the program. Use `$HOME`. (`~` at word start is fine.)
3. **`i3 -C` and `i3-msg reload` are blind to both.** Binding commands are parsed
   at KEY PRESS time; errors surface in the errorlog/nagbar only when pressed.
   Static lint or a synthetic keypress probe is the only way to catch them.

## Toolkit (all under `~/.config/i3`, repo `i3-config`)

### Static: `cd ~/.config/i3/devops`
- `bb lint [config]` — parses config (following `include` globs) into an EDN IR
  and runs rules over it. Rules are pure data (`src/i3/lint.clj` `regex-rules`:
  `:pattern`, optional `:when-pattern`/`:unless-pattern`). Exit 1 on errors.
- `bb parse [config]` — dump the IR as EDN. Pipe to scripts; the IR is the
  testable-in-clojure stand-in for the hard-to-test live WM.
- `bb check` — lint + `i3 -C` + live reload + scan of `i3-dump-log` for ERRORs.
- `bb test` — clojure.test suite (12 tests incl. the full empirical comma matrix).

### Dynamic: the sandbox (`~/.config/i3/sandbox`)
Docker image `i3-config-sandbox`: **ubuntu:24.04 = i3 4.23 = host version**
(version fidelity is deliberate; trixie's 4.24 differs). Xvfb :99, no host X
socket, config mounted read-only, artifacts to `sandbox/workspace/.artifacts/`.

- `bb sandbox build|up|down|shell`
- `bb sandbox smoke` — boot + reload + assert errorlog empty (config-load errors)
- `bb sandbox probe Super+space ...` — xdotool synthetic keypresses, assert no
  binding parse errors. **The only automated detector of bug class 1.**
- `bb sandbox snapshot` — get_tree/get_workspaces JSON artifacts
- `bb sandbox shot` — root-window PNG screenshot
- `bb sandbox regression` — boots `fixtures/broken-quotes`, probes, asserts
  failure. Run after changing the harness itself.

### Cross-repo map
- `~/.config/i3` (git: i3-config) — the kit (`devops/`, `sandbox/`) + config under test
- `~/.agents` (git) — this skill
- `~/.spacemacs.d` — emacs-side `agent-sandbox` layer + the ancestor of the
  sandbox (`.agents/skills/agent-i3-sandbox/`); the i3-only core was extracted
  here, emacs targets stay there
- `~/.config/opencode` — consumes the skill via the harness

## Workflow for a config change
1. `bb lint` (static; catches commas, tildes, unresolved vars, missing paths)
2. Risky? `bb sandbox up && bb sandbox smoke && bb sandbox probe <affected-keys>`
3. Live: `bb check` (reload + log scan)
4. If a new bug class is found: add a lint rule (data) + a regression fixture.

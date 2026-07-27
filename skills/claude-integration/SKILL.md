---
name: claude-integration
description: "Set up Claude Code integration with muse projects: the Claude target compiles the same DSL plugins into MCP tools + native hooks. Use when configuring how Claude Code interacts with a muse-powered repository, or when building Claude-specific hooks and tool exposure."
---

# Skill: Claude Integration

## Goal
Claude Code is a third target of the muse DSL, alongside OpenCode and MCP. The same `deftool`/`defplugin` definitions compile to Claude-native artifacts:
- **MCP tools** (`.mcp.json` + `.claude/dist/`): capabilities the agent can invoke
- **Native hooks** (`.claude/settings.json`): deterministic lifecycle gates

## Use This Skill When
- Setting up a new muse project for Claude Code
- Building the Claude target (`shadow-cljs release claude-server`)
- Customizing the generated `.claude/settings.json` hooks
- Adding project-specific hook scripts
- Debugging Claude + muse integration

## Do Not Use This Skill When
- Building MCP servers for other clients (use `mcp-server-authoring`)
- Writing OpenCode plugins (use `muse-plugin-authoring`)
- Configuring the eta-mu daemon

## Architecture

Claude is a target of the data-as-interpreter, same as OpenCode and MCP:

```
DSL definitions (.ημ/plugins/*.cljs)
  ↓ deftool/defplugin (host-agnostic)
  ↓ config/apply-exposure → profile → compile-adapter
  ↓
  ├─ OpenCode boundary → Hooks API → OpenCode
  ├─ MCP boundary → McpServer (stdio) → Codex, etc.
  └─ Claude boundary → McpServer + settings.json hooks → Claude Code
```

The Claude target (`:claude-server` in shadow-cljs.edn) produces:
- `.claude/dist/claude-server.js` — MCP server (same as MCP target)
- `.claude/settings.json` — native Claude Code hooks
- `.claude/hooks/*.sh` — hook wrapper scripts
- `.mcp.json` — server discovery for Claude Code

## Config Tree

Claude config lives at `.ημ/config/claude/`:

```
.ημ/config/claude/
├── root.edn              # :info, :imports, :build, :publish
├── profiles.edn          # allow/deny per environment
├── permissions/
│   └── default.edn       # permission grants
└── plugins/
    ├── receipt-river.edn
    ├── session-mycology.edn
    └── websearch.edn
```

Same structure as opencode and mcp — the config system is target-agnostic.

## Steps

### 1. Build the Claude target

```bash
shadow-cljs release claude-server
```

This generates all artifacts under `.claude/` and `.mcp.json`.

### 2. Verify the build

Check the generated entrypoint:
```bash
cat src/gen/eta_mu/gen/claude_server.cljs
```

Check the generated config:
```bash
cat .claude/settings.json   # hooks
cat .mcp.json               # MCP server discovery
```

### 3. Start the server (for testing)

```bash
node .claude/dist/claude-server.js
```

This starts an MCP server over stdio. Claude Code connects via `.mcp.json`.

### 4. Customize hooks

The generated `.claude/settings.json` provides baseline hooks. Edit directly or extend via `.claude/settings.local.json` (gitignored).

Available hook events:
| Event | When it fires |
|-------|---------------|
| `PreToolUse` | Before tool call — can block/deny |
| `PostToolUse` | After tool call succeeds |
| `SessionStart` | Session begins/resumes |
| `Stop` | Claude finishes responding |
| `Notification` | Claude Code sends notification |
| `CwdChanged` | Working directory changes |

### 5. Add project-specific hooks

Create scripts in `.claude/hooks/`:

```bash
#!/bin/bash
# .claude/hooks/custom-validator.sh
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path')
# your validation logic here
```

Then reference in `.claude/settings.json` or `.claude/settings.local.json`.

### 6. Hook-to-MCP bridging

Hooks can call MCP tools via the `mcp_tool` type:

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "mcp_tool",
            "server": "eta-mu-claude",
            "tool": "receipt_river",
            "input": {"action": "append", "kind": "edit"}
          }
        ]
      }
    ]
  }
}
```

This lets deterministic hooks compose with DSL-defined tool logic.

## Reference: Build Output

| Artifact | Purpose |
|----------|---------|
| `.claude/dist/claude-server.js` | MCP server bundle |
| `.claude/settings.json` | Native Claude Code hooks |
| `.claude/hooks/*.sh` | Hook wrapper scripts |
| `.mcp.json` | MCP server discovery |

## Reference: Daemon Integration

The eta-mu daemon now discovers all target trees (opencode, mcp, claude) per `.ημ` dir. When `.ημ/config/claude/` changes, the daemon runs `shadow-cljs release claude-server` automatically.

## Strong Hints

- Same plugin source, third target — write once, serve Claude + OpenCode + MCP
- `.claude/settings.json` is a build output — don't hand-edit (will be overwritten)
- Use `.claude/settings.local.json` for personal overrides (gitignored)
- Hook scripts must be executable: `chmod +x .claude/hooks/*.sh`
- The MCP server is the same binary as the MCP target — Claude connects via `.mcp.json`
- Hooks are deterministic — use for gates, not judgment
- `CLAUDE.md` is a context file, not a build output — safe to hand-edit
- The daemon skips `.claude/` during directory discovery (it's a build output dir)

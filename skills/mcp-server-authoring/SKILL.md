---
name: mcp-server-authoring
description: "Create MCP servers from muse plugins for Claude Code, Codex, and other MCP clients. Use when an agent needs to expose tools via the Model Context Protocol, set up .mcp.json auto-discovery, or build an MCP server from existing plugin code."
---

# Skill: MCP Server Authoring

## Goal
Build MCP (Model Context Protocol) servers from muse plugins. The same `deftool`/`defplugin` source serves both OpenCode and MCP targets — the DSL is host-agnostic, boundaries differ.

## Use This Skill When
- Exposing muse tools to Claude Code, Codex, or other MCP clients
- Setting up `.mcp.json` for auto-discovery
- Adding new tools to an existing MCP server
- Building a standalone MCP server from scratch

## Do Not Use This Skill When
- Building OpenCode plugins (use `muse-plugin-authoring`)
- Configuring OpenCode-specific exposure (use `muse-config-authoring`)
- Writing CLAUDE.md context files (use `claude-integration`)

## Architecture

MCP and OpenCode share the same plugin source. The difference is the boundary:

```
Plugin source (.ημ/plugins/*.cljs)
  ↓ deftool/defplugin (DSL)
  ↓ config/apply-exposure → profile → compile-adapter
  ↓
  ├─ OpenCode boundary → Hooks API → OpenCode
  └─ MCP boundary → McpServer (stdio) → Claude Code / Codex / etc.
```

Key differences from OpenCode:
- **No hooks**: MCP has no host lifecycle to patch into; hooks are ignored
- **Zod schemas**: Malli `:args` auto-convert to zod via `eta-mu.dsl.zod`
- **Stdio transport**: MCP server communicates over stdin/stdout
- **No session ctx**: Handler `ctx` always has `{:directory cwd :worktree cwd}`

## Config Tree

MCP config lives at `.ημ/config/mcp/` (same structure as opencode):

```
.ημ/config/mcp/
├── root.edn              # :info, :imports, :build, :publish
├── profiles.edn          # allow/deny per environment
├── permissions/
│   └── default.edn       # permission grants
└── plugins/
    └── receipt-river.edn # exposure fragments (same format)
```

## Steps

### 1. Create the MCP config tree

If it doesn't exist, create `.ημ/config/mcp/root.edn`:

```clojure
{:eta-mu/mcp-version 1
 :id                 :eta-mu/my-server

 ;; Passed to McpServer constructor
 :info {:name "my-mcp-server" :version "1.0.0"}

 :imports
 ["profiles.edn"
  "permissions/default.edn"
  "plugins/my-plugin.edn"]

 :profile :dev

 ;; Build command (from repo root)
 :build ["shadow-cljs" "release" "mcp-server"]

 ;; Writes .mcp.json for Claude Code auto-discovery
 :publish {:mcp-config ".mcp.json"}}
```

### 2. Create plugin exposure fragments

Same format as OpenCode. File: `.ημ/config/mcp/plugins/<name>.edn`

```clojure
{:resource plugins.my-plugin/plugin
 :expose   [:my_ns/*]}
```

The `:resource` points to the same `defplugin` var used by the OpenCode target.

### 3. Create profiles

File: `.ημ/config/mcp/profiles.edn`

```clojure
{:dev  {:allow #{:my_ns/*} :audit :verbose}
 :prod {:allow #{:my_ns/*} :audit :full}}
```

### 4. Write plugin source (if new)

Same as OpenCode — see `muse-plugin-authoring` skill. Plugin files live in `.ημ/plugins/` and are shared between targets.

### 5. Verify the build

```bash
shadow-cljs release mcp-server
```

Check the generated entrypoint:

```bash
cat src/gen/eta_mu/gen/mcp_server.cljs
```

Expected: requires your plugin namespace, runs the adapter pipeline, calls `host/serve!`.

### 6. Test the server

```bash
# List tools (sends initialize + tools/list over stdio)
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"capabilities":{}}}' | node .mcp/dist/receipt-river.js

# Or use the Claude Code MCP inspector
npx @modelcontextprotocol/inspector node .mcp/dist/receipt-river.js
```

### 7. Auto-discovery for Claude Code

When `:publish {:mcp-config ".mcp.json"}` is set, the build writes:

```json
{
  "mcpServers": {
    "my-mcp-server": {
      "command": "node",
      "args": [".mcp/dist/receipt-river.js"]
    }
  }
}
```

Claude Code reads `.mcp.json` from the repo root and auto-connects.

## Reference: MCP Boundary (`eta-mu.boundaries.mcp`)

| Function | Purpose |
|----------|---------|
| `serve!` | Start MCP server over stdio, returns connect promise |
| `render-server` | Compiled adapter → connected McpServer instance |
| `register-tool!` | Register one tool with the server (name, description, zod schema, handler) |

## Reference: Malli → Zod Conversion

The `eta-mu.dsl.zod` namespace auto-converts Malli schemas to zod:

| Malli | Zod |
|-------|-----|
| `:string` | `z.string()` |
| `:int`, `:number` | `z.number()` |
| `:boolean` | `z.boolean()` |
| `[:enum "a" "b"]` | `z.enum(["a","b"])` |
| `[:vector :string]` | `z.array(z.string())` |
| `[:map [:k :string]]` | `z.object({k: z.string()})` |
| `[:maybe :string]` | `z.nullable(z.string())` |

## Strong Hints

- Plugin source is shared — write once, serve both OpenCode and MCP
- Hooks are ignored on MCP target (no host lifecycle to intercept)
- MCP tools carry no session context; `:directory` and `:worktree` both resolve to process cwd
- The `:build` command must be `["shadow-cljs" "release" "mcp-server"]`
- Build output goes to `.mcp/dist/`, not `.opencode/dist/`
- `.mcp.json` is a build output — don't hand-edit it
- The daemon watches `.ημ/config/mcp/` just like `.ημ/config/opencode/`

---
name: muse-config-authoring
description: "Create or modify .ημ/config/opencode/ config trees: root.edn, plugin exposure fragments, profiles, and permissions. Use when an agent needs to register new plugins, adjust tool exposure, change build commands, or set up a new project's eta-mu config."
---

# Skill: Muse Config Authoring

## Goal
Create and modify `.ημ/config/opencode/` configuration trees that control which plugins are loaded, which tools are exposed, and how the daemon builds and renders config.

## Use This Skill When
- Registering a new plugin in a project's config
- Disabling or enabling specific tools via exposure patterns
- Adding a new project to the daemon's watch scope
- Changing build commands or publish targets
- Creating profiles that filter tools by environment (dev/ci/prod)

## Do Not Use This Skill When
- Writing plugin source code (use `muse-plugin-authoring` instead)
- Modifying the global `~/.config/opencode/opencode.jsonc` directly
- Working with OpenCode skills (SKILL.md files)

## Architecture

The canonical config tree (as in `/home/err/spaces/muse`) splits **shared fragments** from **per-target roots**:

```
.ημ/config/
├── shared/                    # canonical fragments, imported by every target
│   ├── profiles.edn           # {:dev ... :ci ... :prod ...} allow/deny/deny-effects
│   ├── permissions/default.edn
│   └── plugins/*.edn          # one exposure fragment per plugin resource
├── opencode/root.edn          # :imports ../shared/* ; :build → :opencode-plugin ; :publish ~/.config/opencode
├── claude/root.edn            # :build → :claude-server ; :publish {:mcp-config ".mcp.json"}
└── mcp/root.edn               # :build → :mcp-server ; :publish {:mcp-config ".mcp.json"}
```

Each target directory also has its own (currently empty) `plugins/` and `permissions/` dirs — that's where target-specific overrides go. A standalone project tree may instead keep its fragments directly inside the single target dir (e.g. `.ημ/config/opencode/plugins/foo.edn` next to its `root.edn`) — same mechanism, `:imports` paths are relative to the root.edn.

The daemon watches these files. EDN changes trigger the root's `:build` command (e.g. `shadow-cljs release opencode-plugin`). Settings changes re-render settings files. See `muse-ecosystem` for the full pipeline and the three naming layers (slash DSL ids ↔ underscore host names ↔ effects).

## Steps

### 1. Read the existing config tree

```bash
cat .ημ/config/opencode/root.edn
ls .ημ/config/opencode/plugins/
```

Understand what's already registered before adding anything.

### 2. Create or edit the plugin exposure fragment

File: `.ημ/config/opencode/plugins/<name>.edn`

```clojure
{:resource plugins.<namespace>/plugin
 :expose   [:tool_ns/*]           ;; glob patterns — empty = expose ALL
 :overrides {:tool_ns/tool {:name "friendly_name"}}}  ;; optional rename
```

**Critical**: `:expose []` means expose **everything** (empty = match all). To expose specific tools, list their namespace patterns. To expose nothing, remove the import from root.edn entirely.

Pattern matching rules (`eta-mu.dsl.profile`):
- `:receipt/*` matches `:receipt/river`, `:receipt/status`, etc.
- `:receipt/river` matches only `:receipt/river`
- Patterns are keywords, not strings

### 3. Register the import in root.edn

Add the plugin's `.edn` fragment to the `:imports` vector in `root.edn`:

```clojure
:imports
["profiles.edn"
 "permissions/default.edn"
 "plugins/actors.edn"
 "plugins/<new-plugin>.edn"    ;; <-- add here
 "plugins/receipt-river.edn"]
```

Order matters — later imports can override earlier ones in settings merges.

### 4. Update profiles (optional)

If the new plugin's tools should be filtered by environment, add patterns to `profiles.edn`:

```clojure
{:dev  {:allow #{:new_plugin/* ...}}
 :ci   {:allow #{:safe_tool/*} :deny #{:dangerous/*}}
 :prod {:allow #{:new_plugin/* ...}}}
```

### 5. Update permissions (optional)

If the new plugin needs OpenCode permission grants, add to `permissions/default.edn`:

```clojure
{:permissions
 [{:id      :default/allow-new-plugin
   :applies #{:new_plugin_tool_name}
   :policy  :allow}]}
```

### 6. Trigger a rebuild

The daemon auto-detects EDN changes and runs the `:build` command. Verify:

```bash
tail -f ~/.pm2/logs/eta-mu-daemon-out.log
```

Look for `:build-ok` with `:code 0`. If the build fails, check the generated entrypoint:

```bash
cat src/gen/eta_mu/gen/opencode_plugin.cljs
```

### 7. Restart OpenCode

The compiled plugin is loaded at OpenCode startup. Restart to pick up changes.

## Reference: root.edn Keys

| Key | Purpose |
|-----|---------|
| `:imports` | Vector of EDN fragment paths (relative to config dir) |
| `:build` | Command vector run from repo root on config/source changes |
| `:emit` | `{:path "~/.config/opencode/opencode.jsonc"}` — renders settings |
| `:publish` | `{:plugins-dir "..." :agents-dir "..."}` — global publish targets |
| `:profile` | Which profile is active (`:dev`, `:ci`, `:prod`) |

## Reference: Exposure Fragment Keys

| Key | Purpose |
|-----|---------|
| `:resource` | Qualified symbol of the `defplugin` value (e.g., `plugins.actors/plugin`) |
| `:expose` | Vector of keyword patterns — filters which tools/hooks are exposed |
| `:overrides` | Map of tool id → merge overrides (e.g., rename with `:name`) |

## Strong Hints

- The daemon scans `~` (depth 1), `~/spaces` (depth 3), `~/devel` (depth 4) for `.ημ/` dirs
- Empty `:expose` = expose ALL tools from that resource (not none)
- To disable a plugin entirely, comment out its import in root.edn — don't set `:expose []`
- The generated entrypoint at `src/gen/eta_mu/gen/opencode_plugin.cljs` is the source of truth for what's compiled
- Build output goes to `.opencode/dist/`, published shims go to `~/.config/opencode/plugins/`
- Permissions fragments match host (underscore) tool names like `:websearch_openhax` — including any `:overrides` rename — not slash DSL ids
- `:claude-server` and `:mcp-server` builds both write `.mcp.json`; last build wins

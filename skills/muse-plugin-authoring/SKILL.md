---
name: muse-plugin-authoring
description: "Create new muse plugins using the eta-mu DSL: deftool, defhook, defplugin macros, malli arg schemas, handler functions, and boundary access. Use when an agent needs to author a new tool, hook, or plugin bundle for the OpenCode target."
---

# Skill: Muse Plugin Authoring

## Goal
Write new muse plugins — bundles of tools and hooks — using the eta-mu DSL. Plugins live in `.ημ/plugins/` as `.cljs` files and are compiled into the OpenCode plugin bundle.

## Use This Skill When
- Creating a new tool that agents can call
- Adding lifecycle hooks (before/after tool execution)
- Wrapping a service or API as an agent-callable tool
- Building a plugin from scratch for a new domain

## Do Not Use This Skill When
- Configuring which existing plugins are exposed (use `muse-config-authoring`)
- Writing OpenCode SKILL.md files
- Modifying the daemon or build system

## Plugin File Location

All plugin source files go in `.ημ/plugins/` at the repo root:

```
.ημ/plugins/
├── actors.cljs
├── receipt_river.cljs
├── websearch.cljs
└── my_new_plugin.cljs    <-- your new file
```

The namespace must match the file path: `plugins.my_new_plugin` → `.ημ/plugins/my_new_plugin.cljs`.

## Steps

### 1. Create the plugin file

```clojure
(ns plugins.my-plugin
  "One-line description of what this plugin does."
  (:require [eta-mu.dsl :refer [deftool defplugin]]
            ;; add boundary requires as needed
            ))
```

Common requires:
| Need | Require |
|------|---------|
| Filesystem | `[eta-mu.boundaries.node.fs :as bfs]` |
| HTTP | `[eta-mu.boundaries.fetch :as http]` |
| Actor system | `[eta-mu.actor :as actor]` `[eta-mu.actor.backend :as backend]` |
| Async | `[promesa.core :as p]` |

### 2. Define tools with `deftool`

```clojure
(deftool my-tool
  {:id          :my_ns/tool_name        ;; keyword ID — namespace matches plugin
   :name        "tool_display_name"     ;; host-facing name (optional, defaults to ns_name)
   :description "Clear description of what this tool does."
   :args        [:map                  ;; Malli schema
                 [:param1 :string]
                 [:param2 {:optional true} :int]]
   :tags        #{:my_ns :utility}     ;; for profile filtering
   :effects     #{:network/search}}     ;; optional: effects this tool produces
  [{:keys [param1 param2]} ctx]        ;; handler: [params-map context-map]
  ;; body — return a value or promise
  {:result "ok" :param1 param1})
```

**Handler signature**: `[params ctx]`
- `params` — map of args matching the `:args` schema
- `ctx` — session context with `:worktree`, `:directory`, `:session/id`, `:agent`

**Return**: plain map or promise of a map (use `p/let` for async).

### 3. Define hooks with `defhook` (optional)

```clojure
(defhook my-guard
  {:id       :policy/my-guard
   :event    :tool/requested        ;; CANONICAL event name (see table)
   :priority 50}                    ;; higher = runs first; first :reject/:patch short-circuits
  [input output]                    ;; host payloads, keywordized (camelCase keys survive)
  ;; input  ≈ {:tool ... :sessionID ... :callID ...}
  ;; output ≈ {:args ...}  (mutable host output; :patch merges into it)
  (when (some-> output :args :secret (= "forbidden"))
    {:effect :reject :message "Cannot use forbidden value"}))
```

**Handler signature**: `[input output]` — NOT the `[params ctx]` tool signature. Return the effect algebra:

| Return | Meaning |
|--------|---------|
| `nil` | allow / no-op |
| `{:effect :reject :message "..."}` | block (host sees a rejected promise) |
| `{:effect :patch :output {...}}` | merge keys into the host output object |

Canonical events (`eta-mu.dsl.events`; unknown names throw at render time):

| Canonical | OpenCode | Claude |
|-----------|----------|--------|
| `:session/open` | `:session.created` | `"SessionStart"` |
| `:session/closed` | `:session.deleted` | `"SessionEnd"` |
| `:tool/requested` | `:tool.execute.before` | `"PreToolUse"` |
| `:tool/succeeded` | `:tool.execute.after` | `"PostToolUse"` |
| `:permission/requested` | `:permission.asked` | `"PermissionRequest"` |
| `:permission/resolved` | `:permission.replied` | `"PermissionDenied"` |
| `:context/compacting` | `:experimental.session.compacting` | `"PreCompact"` |
| `:context/compacted` | `:session.compacted` | `"PostCompact"` |
| `:file/changed` | `:file.watcher.updated` | `"FileChanged"` |

### 4. Bundle with `defplugin`

```clojure
(defplugin plugin {:id :eta-mu/my-plugin}
  my-tool
  my-other-tool
  my-guard)
```

The `plugin` var is what config EDN references as `:resource plugins.my-plugin/plugin`.

Optional `:init` key for side-effecting startup:

```clojure
(defn init! []
  (backend/ensure!))

(defplugin plugin {:id :eta-mu/my-plugin :init init!}
  my-tool)
```

### 5. Register in config

After writing the plugin, register it in `.ημ/config/opencode/`:

1. Create `plugins/my-plugin.edn`:
   ```clojure
   {:resource plugins.my-plugin/plugin
    :expose   [:my_ns/*]}
   ```

2. Add to `root.edn` imports:
   ```clojure
   "plugins/my-plugin.edn"
   ```

See `muse-config-authoring` skill for full config details.

### 6. Test the build

```bash
shadow-cljs release opencode-plugin
```

Check the generated entrypoint to verify your plugin is included:

```bash
cat src/gen/eta_mu/gen/opencode_plugin.cljs
```

## Reference: Malli Arg Schemas

```clojure
;; Required string
[:name :string]

;; Optional int with default
[:limit {:optional true} :int]

;; Enum
[:action [:enum "create" "read" "update" "delete"]]

;; Vector of strings
[:tags {:optional true} [:vector :string]]

;; Nested map
[:config {:optional true} [:map [:key :string]]]

;; Number (float)
[:score {:optional true} :number]
```

## Reference: Available Boundaries

| Boundary | Require | Provides |
|----------|---------|----------|
| Filesystem | `eta-mu.boundaries.node.fs` | `read-text`, `write-text!`, `exists?`, `join`, `home-dir`, `now-iso`, `append-line!`, `append-jsonl!`, `read-lines`, `tail-lines`, `read-jsonl`, `state-dir`, `env`, `find-dirs` |
| HTTP | `eta-mu.boundaries.fetch` | `post-json!`, `get-json!`, `env` |
| Actor | `eta-mu.actor` | `spawn!`, `tell!`, `ask!`, `recv`, `actors`, `registry` |
| Actor backend | `eta-mu.actor.backend` | `ensure!`, `watch-once` |
| Process | `eta-mu.boundaries.node.proc` | `run!` |
| Watch | `eta-mu.boundaries.node.watch` | `watch-dir!` |

## Strong Hints

- Plugin namespace must match file path: `plugins.foo-bar` → `.ημ/plugins/foo_bar.cljs`
- Use `promesa.core/p/let` for async composition — never inline `^:async` in macro bodies
- Tool IDs are keywords namespaced by plugin: `:my_ns/tool_name`
- Tags are sets of keywords — used by profiles for allow/deny filtering
- Effects declare side effects — profiles can deny-effect specific tags
- The `ctx` map is read-only; never mutate it
- Return plain maps from handlers — they get serialized to the host (`{:output ...}` = structured ToolResult, strings pass through, anything else is JSON-stringified)
- Keep handlers pure where possible; push I/O to boundaries
- Hooks use canonical event names (`:tool/requested`), never host-native ones (`:tool.execute.before`) — the boundary translates, and unknown canonical names throw at render time
- See `muse-ecosystem` for the whole-machine map (daemon, build pipeline, targets, shipped plugins)

---
name: muse-ecosystem
description: Whole-machine map of the muse workspace (/home/err/spaces/muse) — the ClojureScript plugin factory that authors agent tools as pure EDN/CLJC data and publishes them to every OpenCode/Claude/MCP session on the host. Covers the daemon, build pipeline, .ημ tree anatomy, shipped plugins/tools, boundaries, actor ledger system, env vars, multi-repo participation, and known drift. Use before leveraging, extending, or debugging any eta-mu plugin machinery.
---

# Skill: Muse Ecosystem (whole-machine map)

The muse (`/home/err/spaces/muse`) is a ClojureScript workspace that authors agent-environment extensions as **pure data** (an EDN/CLJC DSL: `deftool`/`defhook`/`defplugin`), compiles them per host target via shadow-cljs, and runs a filesystem daemon that keeps them built and **published globally**. One source tree feeds OpenCode plugins, Claude Code MCP servers + hooks, and generic MCP servers.

## Live state on this host

| Piece | Where |
|---|---|
| Daemon (pm2) | `eta-mu-daemon` running `dist-daemon/daemon.js`; log: `~/.eta-mu/state/daemon/log.jsonl` |
| Global OpenCode plugin | `~/.config/opencode/plugins/eta-mu-actors.js` → imports muse repo dist by **absolute path** (repo moves require rebuild) |
| Global agents | `~/.config/opencode/agents/{muse.md, phase-discover.md}` (published by the build) |
| Repo-local artifacts | `.opencode/dist/eta-mu-actors.js`, `.opencode/plugins/` shim, `.opencode/opencode.json` (permissions), `.claude/dist/claude-server.js`, `.mcp/dist/receipt-river.js`, `.mcp.json` |
| Tests | `shadow-cljs compile test` (node-test, autoruns; mongo test self-skips without mongod) |

Sibling skills for specific jobs: `muse-plugin-authoring` (write tools/hooks), `muse-config-authoring` (register/expose/permissions), `mcp-server-authoring` (MCP target), `claude-integration` (Claude target). This skill is the map they all hang off.

## The pipeline (EDN → registry → boundary → host)

1. `.ημ/config/<target>/root.edn` lists `:imports` — EDN fragments (usually from `.ημ/config/shared/`): plugin exposures `{:resource plugins.foo/plugin :expose [:ns/*] :overrides {...}}`, permissions, profiles, settings.
2. shadow-cljs `:configure` hook (`src/clj/eta_mu/opencode/build.clj` `generate-entrypoint`) reads root.edn and regenerates `src/gen/eta_mu/gen/opencode_plugin.cljs`, requiring each plugin namespace and piping the registry through `apply-exposure → apply-profile → validate-registry! → compile-adapter`. Config is embedded as a literal, so config edits bust the build cache.
3. `eta-mu.boundaries.opencode/activate!` runs plugin `:init`s and renders the adapter into OpenCode `Hooks` (Malli → zod happens **only** here).
4. `:flush` hook (`emit-host-config`) writes `.opencode/dist/` bundle, `.opencode/plugins/` function-only shim (OpenCode 1.17 requires every plugin-file export to be a function), `.opencode/opencode.json` permissions, `.opencode/package.json`, and agent markdown.
5. If root.edn declares `:publish {:plugins-dir ... :agents-dir ...}`, shims + agents are also written to `~/.config/opencode/` — every OpenCode session on the machine gets the tools.

## The daemon

Filesystem watcher, not a server (binds no ports).

- Scans for `.ημ/` dirs under `~` (depth 1), `~/spaces` (3), `~/devel` (4); rescans every 5 min.
- Watches each tree's `config/` and `plugins/`; on `.edn/.cljc/.cljs/.clj` change (400 ms debounce) reads `config/<target>/root.edn` for targets `["opencode" "mcp" "claude"]` and executes the plan: `:render` (write merged settings) or `:exec` (spawn the tree's `:build` command in the repo root, serialized per-cwd).
- Boot convergence renders but does not build.
- Ops: `pm2 logs eta-mu-daemon`, `tail ~/.eta-mu/state/daemon/log.jsonl`.

## `.ημ/` tree anatomy (muse repo)

```
.ημ/
├── plugins/*.cljs            # DSL plugin sources (ns plugins.*)
├── config/
│   ├── shared/               # canonical fragments, imported by every target
│   │   ├── profiles.edn      #   {:dev {:allow #{...} :audit :verbose} :ci {...} :prod {...}}
│   │   ├── permissions/default.edn
│   │   └── plugins/*.edn     #   {:resource plugins.x/plugin :expose [:x/*] :overrides {...}}
│   ├── opencode/root.edn     # :build ["shadow-cljs" "release" "opencode-plugin"], :publish {plugins/agents dirs}
│   ├── claude/root.edn       # :build → :claude-server, :publish {:mcp-config ".mcp.json"}
│   └── mcp/root.edn          # :build → :mcp-server, :publish {:mcp-config ".mcp.json"}
├── ledgers/                  # file-backed actor ledgers (if ETA_MU_LEDGER_ROOT points here)
└── PRINCIPLE.edn             # ημΠ contract snapshot (see Known drift below)
```

**Shared vs per-target**: all three targets currently import the same 7 shared fragments; per-target `plugins/`/`permissions/` dirs exist but are empty — that's the override point when a target needs to diverge.

**Three naming layers** (keep straight):
- DSL ids: slash keywords (`:web/search`) — used in `deftool :id`, `:expose`, profiles.
- Host tool names: underscore strings (`"websearch_openhax"`) — derived (`:muse/spawn` → `muse_spawn`) or set by `:overrides ... :name`; used in permissions fragments and OpenCode permission config.
- Effects: slash keywords (`:network/search`) — declared per-tool, denied via profile `:deny-effects`.

## Shipped plugins (`.ημ/plugins/`) → 22 tools

| Plugin | Tools (host names) | Purpose | Pure logic in |
|---|---|---|---|
| `actors.cljs` | `muse_spawn`, `muse_phases`, `muse_influence`, `phase_spawn`, `phase_record`, `phase_list_active`, `phase_list_idle`, `phase_tail`, `phase_head`, `phase_filter`, `phase_observations`, `phase_conclusions`, `actor_tell`, `actor_list` | Muse/phase actor system: event-sourced agent mailboxes; a Muse influences and observes, never orchestrates | `eta-mu.actor*` |
| `apifany.cljs` | `apifany_spawn_agent_actor`, `apifany_list_known_agents`, `apifany_send_agent_actor_message` (tell/ask), `apifany_read_mailbox`, `apifany_monitor_mailbox` (blocking, ≤300 s) | Agent-to-agent async messaging over the ledger | `eta-mu.actor*` |
| `receipt_river.cljs` | `receipt_river` (status/bootstrap/append/tail/validate) | Append-only per-repo `receipts.edn` execution ledger. Flagship/reference implementation | `eta-mu.domain.receipts`, `eta-mu.domain.repo` |
| `session_mycology.cljs` | `session_mycology` (reflect/list_recent/promote) | Per-turn p-scores; incubates skill spores; promotes recurring ones to `~/.eta-mu/agent/skills/<slug>/` | `eta-mu.domain.mycology` |
| `websearch.cljs` | `websearch_openhax` (renamed via `:overrides`) | Web search via Open Hax OAuth proxy (`http://127.0.0.1:8789`) | `eta-mu.domain.websearch` |

## DSL essence (canonical events — read before writing hooks)

`deftool`/`defhook`/`defplugin` emit plain maps tagged `:ημ/kind`. Tool handlers are `[params ctx]`; ctx = `{:session/id :message/id :agent :directory :worktree}` (OpenCode; MCP/Claude only provide `:directory`/`:worktree`). **Hook handlers are `[input output]`** — keywordized host payloads (camelCase keys, e.g. `{:tool ... :sessionID ...}` / `{:args ...}`), returning the effect algebra `nil | {:effect :reject :message ...} | {:effect :patch :output {...}}`.

Hooks are authored against **canonical** events; each boundary translates to host-native (`eta-mu.dsl.events/for-host`, throws on unknown):

| Canonical | OpenCode | Claude |
|---|---|---|
| `:session/open` | `:session.created` | `"SessionStart"` |
| `:session/closed` | `:session.deleted` | `"SessionEnd"` |
| `:tool/requested` | `:tool.execute.before` | `"PreToolUse"` |
| `:tool/succeeded` | `:tool.execute.after` | `"PostToolUse"` |
| `:permission/requested` | `:permission.asked` | `"PermissionRequest"` |
| `:permission/resolved` | `:permission.replied` | `"PermissionDenied"` |
| `:context/compacting` | `:experimental.session.compacting` | `"PreCompact"` |
| `:context/compacted` | `:session.compacted` | `"PostCompact"` |
| `:file/changed` | `:file.watcher.updated` | `"FileChanged"` |

Multiple hooks per event compose by `:priority` descending; first `:reject`/`:patch` short-circuits.

## Architecture layers (where things live in `src/cljs/eta_mu/`)

- `dsl.cljc` + `dsl/{normalize,profile,schema,compile,events,zod}.cljc` — the law/shape layer: IR schemas (Malli), fragment merge, exposure link, profile filter (`:allow`/`:deny`/`:deny-effects`; empty allow = allow all), adapter compile. Pure.
- `domain/*.cljc` — pure decisions: `daemon` (plan-actions, watch rules), `receipts`, `repo` (git-root), `mycology` (spore thresholds: promote ≥0.72, friction ≥0.68), `websearch`.
- `boundaries/**` — the ONLY I/O: `opencode.cljs`, `mcp.cljs`, `claude.cljs`, `fetch.cljs`, `node/{fs,proc,watch,import,ledger}.cljs`, `mongo/{client,ledger}.cljs`. Plugins require boundaries directly and inject boundary fns into pure domain fns.
- `actor{.cljc,/store,/memory,/backend,/envelope,/muse}` — `IActorStore` protocol (spawn/send/recv/registry/mailbox/clear) with memory (reference), file (`.eta-mu/ledgers/*.edn`, fs.watch), and mongo (event-ledger wire format, TTL, lazy driver load) backends. `muse.cljc`: phases id'd `<muse-id>.<phase-type>.N`; influence appends `muse.influence.<type>`; conclusions without evidence are claims.
- `src/clj/eta_mu/{opencode,mcp,claude}/build.clj` — JVM shadow hooks: entrypoint generation + host config emission.

Ledger backend env vars: `ETA_MU_LEDGER_BACKEND` (`file`|`mongo`|`memory`, default `file`), `ETA_MU_LEDGER_ROOT` (default `.eta-mu/ledgers`), `ETA_MU_MONGO_URI`, `ETA_MU_MONGO_DB` (default `eta_mu`).

## Build targets (`shadow-cljs.edn`)

| Build | Output | Notes |
|---|---|---|
| `:opencode-plugin` | `.opencode/dist/eta-mu-actors.js` + shims + agents + permissions + global publish | the one you usually rebuild |
| `:daemon` | `dist-daemon/daemon.js` | pm2 `eta-mu-daemon` |
| `:claude-server` | `.claude/dist/claude-server.js` + `.mcp.json` (server `eta-mu-claude`) + `.claude/settings.json` hooks | |
| `:mcp-server` | `.mcp/dist/receipt-river.js` + `.mcp.json` (server `eta-mu-receipt-river`) | |
| `:test` | `target/test/test.cjs` (autorun) | `shadow-cljs compile test` |
| `:app`/`:server*` | `dist*/` | sol web app from the katamorph dep |

**Collision caveat**: `:claude-server` and `:mcp-server` both write `.mcp.json` — last build wins.

## Extending agent environments (recipes)

**Add a tool globally** (most common): new `deftool` in an existing or new `.ημ/plugins/*.cljs` → expose it in `config/shared/plugins/<name>.edn` → `shadow-cljs release opencode-plugin` (or let the daemon see the change) → restart OpenCode. Detailed walkthrough: `muse-plugin-authoring` + `muse-config-authoring`.

**Bring a second repo into the ecosystem**: create `.ημ/` in that repo (the daemon auto-discovers it at the next 5-min rescan if it's under the scan roots). Two participation levels:
1. **Consumer** (recommended per DEPLOY.md "Serving other worlds"): no local plugins; rely on the globally published `eta-mu-actors.js` — the repo's `receipts.edn` stays local, the implementation is centralized in the muse. Don't re-expose a competing local receipt plugin or it shadows the global one.
2. **Producer**: your own `config/opencode/root.edn` with `:build` (needs a shadow-cljs `:opencode-plugin` build with muse's hooks — today that means depending on the muse repo's `src/clj` build namespaces; there is no standalone library packaging of the hooks yet).

`/home/err/spaces/eta-mu/.ημ/config/opencode/` is a real but **vestigial** second-world tree (`:id :knoxx/opencode`, capability-era fragment shapes, host-native event names, no `:build`/`:publish`) — the daemon reads it but it plans no actions. Don't copy its shape; copy the muse's.

**Bootstrap a fresh machine**: `scripts/bootstrap.sh` (checks node/npm/java/clojure/shadow-cljs/pm2, npm install, builds daemon+opencode-plugin+test, `pm2 startOrRestart` + `pm2 save`). Full runbook: `docs/DEPLOY.md` (its "known breaks" #1/#2 are stale — deps are git refs now, not sibling paths).

## Known drift / anomaly log (observed 2026-07-17)

- `.ημ/PRINCIPLE.edn` is a **stale snapshot**: its skill registry roots at dead `~/.pi/agent/skills` (live: `~/.agents/skills`), and its §9 renders the hard-uncertainty operator as `לג` (U+05DC+U+05D2) instead of canonical `לா` (U+05DC+U+0BBE) — an in-the-wild specimen of the look-alike drift its own §3b warns about. Live contract: eta-mu repo `operation-mindfuck/ημΠ.dev.*.edn`.
- `.claude/settings.local.json` enables MCP server `eta-mu-receipt-river` but current `.mcp.json` declares `eta-mu-claude` (claude target built last).
- `.claude/settings.json` references hook scripts (`.claude/hooks/session-start.sh` etc.) that are never emitted — the claude build hook is invoked with an empty adapter, so only the hand-written hooks (`audit-mcp-call.sh`, `post-edit.sh`, `reinject-context.sh`) exist.
- All three root.edn files hardcode `:profile :dev`; `:ci` and `:prod` profiles are unreachable without an edit.
- `test/js/eta_mu_cli_test_stub.cjs` (test build resolve stub) doesn't exist; inert today.

## Strong hints

- After editing anything under `.ημ/`, either wait ~1 min for the daemon or run `shadow-cljs release opencode-plugin` yourself; then **restart OpenCode** — plugins load at startup.
- The generated `src/gen/eta_mu/gen/opencode_plugin.cljs` is the source of truth for what's actually compiled.
- Never edit `.opencode/opencode.json` by hand — it's a build artifact.
- Tool `:tags` become adapter `:permissions` (unioned at plugin level); permissions fragments match host (underscore) names, not DSL ids.
- The handler `ctx` map is read-only; return plain CLJS data (`{:output ...}` for structured ToolResult, string passthrough, anything else JSON-stringified).

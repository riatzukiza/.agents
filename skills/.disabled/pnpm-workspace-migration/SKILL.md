---
name: pnpm-workspace-migration
description: "Handle packages migrating between pnpm workspaces. Clean stale root copies, update workspace configs, fix broken workspace:* references."
triggers:
  - "package moved"
  - "workspace migration"
  - "WORKSPACE_PKG_NOT_FOUND"
  - "pnpm-workspace.yaml"
  - "workspace:*"
---

# pnpm Workspace Migration Protocol

## Problem
When packages move between pnpm workspaces (e.g., from a root monorepo into a sub-monorepo), stale references accumulate in three places:
1. The old workspace's `pnpm-workspace.yaml` (explicit package paths)
2. Root `package.json` dependencies (`workspace:*` references)
3. Other packages' `package.json` that depend on the moved package via `workspace:*`

## Steps

### 1. Identify Overlap
Find packages that exist in both locations:
```bash
comm -12 <(ls packages/ | sort) <(ls orgs/open-hax/openplanner/packages/ | sort)
```

### 2. Check References
Find who depends on the moved packages:
```bash
grep -r '"@scope/package-name"' --include='package.json' . | grep 'workspace:*'
```

### 3. Remove Stale Root Copies
- If the old location was a git submodule: `git submodule deinit -f packages/old-name`
- Remove the directory: `rm -rf packages/old-name`
- Remove from `pnpm-workspace.yaml` if explicitly listed

### 4. Update Consumers
- Root `package.json` scripts: update `-C packages/old-name` paths to new location
- External packages: ensure the new workspace is listed in root `pnpm-workspace.yaml`

### 5. Workspace Discovery
- pnpm does NOT auto-merge nested workspaces. If package X lives in `orgs/repo/packages/X` and the root workspace only lists `orgs/repo`, pnpm will NOT find X.
- Fix by explicitly adding `orgs/repo/packages/*` to the root `pnpm-workspace.yaml`.

### 6. Broken Internal Deps
If the destination workspace has its own broken `workspace:*` deps, use `--filter` to install only the packages you need:
```bash
pnpm install --filter @scope/my-package...
```
The `...` includes all dependencies and dependents of the filtered package.

## Anti-Patterns
- Running `sudo rm -rf node_modules` as a first step (indicates root-owned file contamination)
- Adding symlinks to make packages appear in multiple workspace locations
- Using `shamefully-hoist=true` to work around resolution issues

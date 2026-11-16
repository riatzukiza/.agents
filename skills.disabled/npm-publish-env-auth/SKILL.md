---
name: npm-publish-env-auth
description: Publish npm packages using the NPM_TOKEN environment variable as the canonical auth source, verify registry write access first, and only update downstream consumers after a successful publish.
license: LGPL-3.0-or-later
compatibility:
  - opencode
  - codex
metadata:
  owner: local
  version: 1
---

# Skill: NPM Publish via Environment Auth

## Goal
Publish npm packages using `NPM_TOKEN` as the permanent source of npm auth, then update downstream consumers only after the publish succeeds.

## Use This Skill When
- The user says "publish the npm package" or similar.
- You need to release a workspace package to npm.
- A downstream repo must be updated to consume a freshly published package.
- npm auth or registry write access might be part of the workflow.

## Do Not Use This Skill When
- The package is private and not intended for npm publication.
- The task is only local linking, workspace overrides, or a one-off tarball install.
- The user explicitly asks for a local/manual override instead of a published package.

## Inputs
- Package path and package name.
- Desired semver bump.
- `NPM_TOKEN` from the environment.
- Any downstream repos that should be updated after publish.

## Steps
1. Treat `NPM_TOKEN` as the canonical auth source.
2. Ensure `~/.npmrc` uses environment expansion instead of a literal token value:
   - `//registry.npmjs.org/:_authToken=${NPM_TOKEN}`
   - `always-auth=true`
   - add `@<scope>:registry=https://registry.npmjs.org/` when publishing a scoped package.
3. Verify auth before changing downstream consumers:
   - `npm whoami`
   - `npm view <package-name> version`
4. Build and validate the package before publish:
   - typecheck
   - tests
   - production build
   - optional `npm pack --dry-run`
5. Publish the package to npm.
6. Only after publish succeeds, update downstream repos to the published version.
7. Reinstall/rebuild downstream projects and recreate services as requested.
8. If publish fails, stop and report the exact registry/auth/scope failure. Do not paper over it with local linking or hardcoded fallback edits.

## Output
- Published npm package version.
- Any npm auth config changes needed to keep `NPM_TOKEN` as the source of truth.
- Downstream consumer updated to the published version only after success.
- Verification notes for build/recreate steps.

## References
- Related skill: `skill-authoring`
- Related skill: `publish-blocked-downstream-integration-guard`

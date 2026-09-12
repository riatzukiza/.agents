---
name: promethean-host-slotting
description: Choose staging and production Promethean host slots, subdomains, runtime paths, and compose-project names from the allowed base-host pool.
license: GPL-3.0
compatibility:
  - opencode
  - codex
---

# Skill: Promethean Host Slotting

## Goal
Pick predictable staging/production placement for a Promethean service without inventing hostnames or hiding transport/runtime constraints.

## Use This Skill When
- A service needs `staging.<service>.promethean.rest` and `<service>.promethean.rest` placement.
- You need to choose among the allowed base hosts.
- You need deploy paths, compose-project names, and GitHub env-var values before wiring CI/CD.
- SSH transport may need to use direct IPs while public traffic still uses DNS hostnames.

## Do Not Use This Skill When
- The service is not deploying into the Promethean host fleet.
- The host choice is already fixed and documented in the repo or task context.
- The task is only to edit unrelated CI checks with no deployment placement changes.

## Inputs
- Service slug/name.
- Optional explicit staging/prod host overrides.
- Allowed base hosts:
  - `ussy.promethean.rest`
  - `ussy2.promethean.rest`
  - `ussy3.promethean.rest`
  - `big.ussy.promethean.rest`
  - `knoxx.promethean.rest` (preserve its existing ingress/runtime placement)
- SSH reachability, DNS reachability, and any discovered existing live placement.

## Steps
1. Normalize the service slug.
   - Use a lowercase hyphenated service name for paths, compose projects, and subdomains.
2. Derive the public hostnames.
   - Staging: `staging.<slug>.promethean.rest`
   - Production: `<slug>.promethean.rest`
3. Prefer existing truth over new guesses.
   - If the service already has a live host, compose project, or runtime root, preserve it unless the user explicitly wants migration.
4. Probe the allowed base hosts.
   - Check public DNS resolution.
   - Check SSH reachability.
   - Note any transport-vs-public-host differences.
5. Use this default host preference when no stronger signal exists.
   - Production: `ussy.promethean.rest`, then `big.ussy.promethean.rest`, then `ussy2.promethean.rest`, then `ussy3.promethean.rest`.
   - Staging: `ussy3.promethean.rest`, then `ussy2.promethean.rest`, then `big.ussy.promethean.rest`, then `ussy.promethean.rest`.
6. Prefer separate staging and production hosts when a safe reachable pair exists.
   - If separation is not practical, reuse one host but isolate by path, compose project, and public hostname.
7. Emit deterministic runtime conventions.
   - Production path: `~/devel/services/<slug>`
   - Staging path: `~/devel/services/<slug>-staging`
   - Production compose project: `<slug>` unless an existing stack proves otherwise.
   - Staging compose project: `<slug>-staging` unless an existing stack proves otherwise.
8. Emit GitHub environment-variable values.
   - `STAGING_SSH_HOST`, `STAGING_PUBLIC_HOST`, `STAGING_DEPLOY_PATH`, `STAGING_COMPOSE_PROJECT_NAME`, `STAGING_BASE_URL`
   - `PRODUCTION_SSH_HOST`, `PRODUCTION_PUBLIC_HOST`, `PRODUCTION_DEPLOY_PATH`, `PRODUCTION_COMPOSE_PROJECT_NAME`, `PRODUCTION_BASE_URL`
   - include `*_VERIFY_RESOLVE_ADDRESS` when HTTPS validation must preserve the public hostname but runner DNS is flaky.

## Output
- Selected staging and production base hosts.
- Public staging and production hostnames.
- Runtime paths and compose-project names.
- A GitHub vars checklist future deploy workflows can consume directly.

## Notes
- Keep SSH transport address and public hostname separate when necessary.
- Do not invent hosts outside the allowed base-host pool.
- If multiple safe options remain, state the default heuristic and any uncertainty explicitly.

## Nested Knoxx names and TLS
- An explicit hostname such as `staging.knoxx.promethean.rest` overrides the default slug convention.
  Do not flatten it into `staging-knoxx` or select another base host.
- Knoxx transport was verified as `err@knoxx.promethean.rest` on 2026-09-12. Its existing
  production root is `/srv/open-hax/services`, with Caddy owning ports 80/443; preserve this
  instead of creating a competing ingress under the generic `~/devel/services` convention.
- `testing.knoxx`, `stealth.knoxx`, `yoga.knoxx`, and `staging.knoxx` currently identify
  HTTPS placeholders on Knoxx, not separate deployed apps or the devices named by their labels.
- Use [promethean-rest-dns](../promethean-rest-dns/SKILL.md) with `--core knoxx`, then configure
  the exact Caddy site and verify its full hostname with normal certificate validation.
  DNS success, TLS success, and application health are three separate checks.
- The parent wildcard `*.promethean.rest` does not cover these names. Keep exact-host ACME
  on stock Caddy unless a DNS-01 wildcard rollout is explicitly chosen; validate Cloudflare
  edge coverage separately before proxying nested DNS records.
- A real dev upstream must retain the existing Caddy auth guard and firewall boundary.
  A successful placeholder 404 is not staging deployment success.

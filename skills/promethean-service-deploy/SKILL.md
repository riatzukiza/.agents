---
name: promethean-service-deploy
description: Interpret "Deploy X" as bootstrapping or repairing a full local -> PR -> staging -> PR -> prod Promethean deployment flow with DNS, GitHub automation, and live validation.
license: GPL-3.0
compatibility:
  - opencode
  - codex
---

# Skill: Promethean Service Deploy

## Goal
Turn requests like `Deploy X` into a complete Promethean service delivery setup, not a one-off manual deploy.

## Use This Skill When
- The user says `Deploy X` or asks to deploy a service under `*.promethean.rest`.
- A repo lacks a real local -> PR -> staging -> PR -> prod promotion path.
- A service needs Promethean DNS, host placement, GitHub Actions, environments, and protected-branch follow-through.
- A deployment pipeline exists only partially and needs to be completed or repaired.

## Do Not Use This Skill When
- The service is not meant to live under `*.promethean.rest`.
- The user only wants a one-off manual deploy with no reusable workflow.
- The repo already has a healthy local -> PR -> staging -> PR -> prod flow and the task is only to run it unchanged.

## Inputs
- Service repo path and GitHub repo.
- Service slug/name.
- Real build/test/start/deploy surface for the service.
- GitHub auth plus any required deploy secrets.
- Cloudflare DNS token via `CLOUD_FLARE_PROMETHEAN_DOT_REST_DNS_ZONE_TOKEN`.
- Optional explicit host preference; otherwise select from the allowed base hosts.

## Conventions
- Staging hostname: `staging.<service-name>.promethean.rest`
- Production hostname: `<service-name>.promethean.rest`
- Allowed base hosts:
  - `ussy.promethean.rest`
  - `ussy2.promethean.rest`
  - `ussy3.promethean.rest`
  - `big.ussy.promethean.rest`
  - `knoxx.promethean.rest` (preserve its existing ingress/runtime placement)
- Default runtime roots:
  - production: `~/devel/services/<service-name>`
  - staging: `~/devel/services/<service-name>-staging`
- GitHub flow:
  - local work on feature branch
  - PR into `staging`
  - push to `staging` deploys staging and runs live checks
  - PR from `staging` into `main`
  - push to `main` deploys production and verifies live health

## Steps
1. Inventory the current state.
   - Check whether the repo already has local run docs, PR gates, deploy workflows, staging/prod URLs, and GitHub environment configuration.
   - Separate what already exists from what is missing.
2. Create a spec and receipts for non-trivial work.
   - Record the deployment contract, required secrets, host decisions, and validation plan.
3. Normalize the service slug and derive the public hostnames.
   - Staging: `staging.<slug>.promethean.rest`
   - Production: `<slug>.promethean.rest`
4. Use `promethean-host-slotting` to choose base hosts, runtime paths, and compose-project names.
   - Keep SSH transport host/IP separate from public host if runner DNS is flaky.
5. Use `promethean-rest-dns` to create or update DNS.
   - Use `CLOUD_FLARE_PROMETHEAN_DOT_REST_DNS_ZONE_TOKEN`.
   - Dry-run first, then apply.
6. Use `pr-promotion-workflows` to stand up the GitHub delivery path.
   - Lightweight PR checks for `staging`.
   - Heavy PR checks for `main`.
   - Push-to-`staging` deploy with live staging verification.
   - Push-to-`main` deploy with live production verification.
7. Automate GitHub follow-through with `gh` when possible.
   - Environments
   - vars
   - secrets
   - branch protection
   - required status checks
8. Validate honestly.
   - Run the exact local commands used by CI.
   - Parse workflow YAML.
   - Verify staging live.
   - Verify production live.
   - Do not stop at “the YAML looks right.”
9. Report only real blockers.
   - Missing secrets, missing auth, unreachable hosts, or broken runtime parity.
   - Say what remains manual only when it truly cannot be automated from current access.

## Output
- A working or clearly-blocked local -> PR -> staging -> PR -> prod deployment flow.
- Public staging and production URLs.
- Selected base hosts, runtime paths, and compose-project names.
- GitHub workflow/env/branch-protection summary.
- Live verification results for staging and production.

## Checks
- DNS exists for both staging and production hostnames.
- Branch model is PR-based, not direct-to-main.
- `staging` push deploys staging and runs live validation.
- `main` push deploys production and verifies health.
- Required GitHub vars/secrets are explicit and named predictably.
- Future agents can re-run or extend the flow without guessing the contract.

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

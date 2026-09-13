---
name: promethean-rest-dns
description: Create Cloudflare DNS records for promethean.rest, including nested Knoxx names, then configure and verify exact-host or wildcard TLS separately.
license: GPL-3.0
compatibility:
  - opencode
  - codex
---

# Skill: Promethean.rest DNS

## Goal
Make requested Promethean hostnames resolve to the intended host and, when HTTPS is requested, verify their certificate and route independently.

## Use This Skill When
- Creating or updating any depth of subdomain under `promethean.rest`.
- Targeting `knoxx`, `ussy`, `ussy2`, `ussy3`, `big.ussy`, or an explicit IPv4 address.
- Preparing DNS and TLS for names such as `testing.knoxx.promethean.rest`.

## Do Not Use This Skill When
- Buying/transferring domains or automating another registrar/zone.
- Cloudflare does not manage the zone, or its zone token is unavailable.
- Only inspecting runtime placement: use `promethean-host-runtime-inventory`.

## Environment contract
- Public names are `<env>.<service-name>.promethean.rest`; use lowercase hyphenated service names.
- Environments: `stealth`, `yoga`, `testing`, `staging`, `production`. Preserve existing production aliases such as `knoxx.promethean.rest` until explicitly migrated.
- Stealth is this device (`err-Stealth-16-AI-Studio-A1VGG`, observed LAN `192.168.12.128`). Yoga is `err@192.168.12.68` (`err-Yoga-7-16ARP8`). Recheck addresses and hostname before mutation.
- The public Knoxx ingress is `err@knoxx.promethean.rest`, IPv4 `157.245.125.134`. A service hostname is an application route, not an SSH target.
- A code owner can claim testing by applying `testing` to an open PR targeting `main`. Read the latest label-event timestamp, not `updated_at`. Any other open testing label aged two hours or less blocks it; older claims expire. Reapplying a label resets its lease.
- Read CODEOWNERS from main and verify the label actor. Missing CODEOWNERS refuses admission; the initial app files name the existing repository admins. Never trust owner changes in the candidate PR.
- Repeat admission inside a serialized per-service testing deployment slot immediately before deployment, and require the built commit to equal the admitted head. A denied PR needs a fresh label event after the blocker expires.
- A successful PR merge into `main` deploys its exact merge commit to staging. Main does not deploy production.
- Production requires the same immutable revision to pass integration, e2e, and four disjoint mutation batches with at least 250 evaluated mutations each. A failing baseline, no tests, compilation failure, timeout, survivor, duplicate mutation, or stale evidence rejects promotion.
- Build PR code on ephemeral runners with no deployment credentials. Deployment controllers come from trusted, pinned Services code; deployment credentials enter only a fresh deployment job. Do not run candidate scripts in privileged pull_request_target jobs.
- Distinguish policy authored, workflow merged, GitHub environment configured, event exercised, and live deployment verified. A draft workflow is not an active gate.

## Inputs and helper
- Relative label (`testing.knoxx`) or full hostname; a wildcard must be quoted (`'*.knoxx'`).
- Target core, expected application upstream or explicit placeholder intent, and proxy mode.
- Helper: `~/.agents/skills/promethean-rest-dns/scripts/promethean_rest_dns.py`.
  `~/devel/tools/promethean_rest_dns.py` is the compatibility copy; keep both identical when editing.
- Environment: `CLOUDFLARE_API_TOKEN` or `CLOUD_FLARE_PROMETHEAN_DOT_REST_DNS_ZONE_TOKEN`;
  optional `CLOUDFLARE_ZONE_NAME` (default `promethean.rest`) and `CLOUDFLARE_ZONE_ID`.
- Optional `--env-file PATH` before the subcommand reads only these four settings as literal dotenv values;
  it never executes shell code, and existing environment values win.
- On Knoxx, the existing zone token was found in `~/.secrets/.env` on 2026-09-12.
  Run DNS operations on that host when useful; never print or copy the token into reports, commands, or Git.

## DNS workflow
1. Inspect the target core's live A/AAAA/CNAME records and whether Cloudflare proxies it.
   The helper's `show-cores` reports public resolver answers. Do not copy Cloudflare anycast IPs
   from a proxied base hostname: inspect its stored origin A record through the API and use `--ip`.
   Legacy cores have labeled last-known fallbacks; verify those before applying. Knoxx has no stale fallback.
2. Dry-run, inspect the exact host and all mutations, then apply within the user's authorized scope:
   ```bash
   dns_helper="$HOME/.agents/skills/promethean-rest-dns/scripts/promethean_rest_dns.py"
   python3 "$dns_helper" --env-file "$HOME/.secrets/.env" ensure testing.knoxx --core knoxx --dry-run
   python3 "$dns_helper" --env-file "$HOME/.secrets/.env" ensure testing.knoxx --core knoxx
   ```
   Repeat for `stealth.knoxx`, `yoga.knoxx`, `staging.knoxx`, or the requested label.
   Omit `--env-file` if credentials are already in the environment.
3. Default is DNS-only (`proxied: false`). For nested names, use `--proxied` only after verifying
   an active Cloudflare **edge** certificate covers the full name. Universal SSL on a full zone
   normally covers the apex and first-level names only. Origin TLS alone cannot fix missing edge coverage.
4. Verify public A and AAAA resolution, then re-run the dry run: expect only `keep`/`preserve`.
   A successful DNS update does **not** provision a certificate or an application route.

## HTTPS on Knoxx: exact hosts by default
Verified 2026-09-12: `err@knoxx.promethean.rest` → `157.245.125.134`, Docker Caddy
`caddy-caddy-1` (`caddy:2.8-alpine`, v2.8.4). Live config is
`/srv/open-hax/services/caddy/Caddyfile`; Compose is alongside it. Persistent ACME state is
`/srv/open-hax/state/caddy/data`. Source is `open-hax/services`,
`digitalocean/services/caddy/Caddyfile`. Recheck placement before edits.

1. Create DNS first. For an exact nested hostname, stock Caddy automatically obtains and renews
   a public certificate using HTTP-01 or TLS-ALPN-01. No Cloudflare plugin is needed.
2. Add an explicit Caddy site. If the user wants only HTTPS preparation, use a static placeholder:
   ```caddyfile
   testing.knoxx.promethean.rest {
       import common
       header Cache-Control "no-store"
       respond "Service not configured." 404
   }
   ```
   A real application needs an explicitly chosen upstream. For host dev services, retain
   `dev_guard`, `dev_upstream`, and existing source-scoped firewall rules; never infer that a device
   label authorizes exposing that device's shell, agent, or development server.
3. Preserve unrelated sites and authentication. Back up the live file on the host with restricted
   permissions, validate a candidate using the **running container's environment**, then reload:
   ```bash
   docker exec caddy-caddy-1 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
   docker exec caddy-caddy-1 caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
   ```
   Do not rename-replace a single-file bind mount and expect the container to see a new inode.
   Preserve the inode for a live edit or deliberately recreate with the existing Compose configuration.
   On failure restore the backup, validate, and reload. Never delete persistent ACME state.
4. Keep the deployment source in sync; a host-only edit is overwritten by the next deployment.
   Publish a reviewable source patch/PR and report whether it has actually merged.
5. Verify with public DNS and normal trust/hostname checking:
   ```bash
   curl --max-time 20 -sS -D - https://testing.knoxx.promethean.rest/
   curl --max-time 20 -sS -o /dev/null -D - http://testing.knoxx.promethean.rest/
   # Diagnose origin separately while retaining SNI and certificate validation:
   curl --resolve testing.knoxx.promethean.rest:443:157.245.125.134 -sS -D - https://testing.knoxx.promethean.rest/
   ```
   Do not use `-k` as TLS proof. Record SANs, issuer, expiry, redirect, and expected HTTP status.
   Placeholder 404 is expected; it is not an application-health success. Recheck existing production routes.

## Wildcard alternative
- `*.promethean.rest` covers `knoxx.promethean.rest`, **not** `testing.knoxx.promethean.rest`.
- `*.knoxx.promethean.rest` covers the four requested names, but neither `knoxx.promethean.rest`
  nor `api.testing.knoxx.promethean.rest`. Add exact SANs or the relevant deeper wildcard as needed.
- Wildcard DNS and wildcard certificates are separate. DNS wildcard synthesis also depends on
  existing names/records and is not the same as TLS's one-label match rule.
- Public ACME wildcard certificates require DNS-01. Stock Knoxx Caddy has no DNS provider module.
  A wildcard rollout must provide a maintained Caddy build with `dns.providers.cloudflare`, inject a
  zone-scoped token through the existing secret mechanism, configure `tls { dns cloudflare {env.CLOUDFLARE_API_TOKEN} }`
  as a multiline block, persist ACME storage, and verify issuance and renewal configuration before claiming coverage.
  Minimum practical token access: DNS Edit and Zone Read for this zone (Zone Read is also needed for helper auto-discovery).
- Do not configure wildcard automation on stock Caddy or use `tls internal` for public trust.
  Never assume a CNAME changes the requested SNI/certificate name.

## Output and safety
- JSON DNS plan/result includes desired/existing records, scoped create/update/delete actions, and TLS hints.
- Only the requested name's conflicting A/AAAA/CNAME/HTTPS/SVCB records are replaced;
  non-conflicting records and unrelated names are preserved. Review deletions before applying.
- Inventory/report separates DNS, edge TLS, origin TLS, route type, and application status.
- Use append-only Receipt River evidence; never include private keys, token values, or credential-bearing config dumps.

## References
- [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)
- [Caddy TLS/DNS challenge configuration](https://caddyserver.com/docs/caddyfile/directives/tls)
- [Cloudflare Universal SSL limitations](https://developers.cloudflare.com/ssl/edge-certificates/universal-ssl/limitations/)

## Installed environment runtime (2026-09-13)

- Stealth is `192.168.12.128`; Yoga is `192.168.12.68`. Both have isolated Axxium and Knoxx compose projects under `~/.local/share/promethean/services/`. Stealth Tailscale is logged out; use LAN SSH where applicable.
- Knoxx ingress owns public TLS. Host applications reach its private relays through user-systemd SSH forwards. `testing` and `staging` slots live at `/srv/open-hax/environments/<env>/<service>`; their separate forced-command keys accept only admitted image archives.
- The source-controlled controller is in `open-hax/services`; app callers activate after review and merge to main. A configured hostname with a 503 placeholder is not a deployed application.
- For a new service, add a reviewed build recipe, fixed compose template, restricted receiver slot and health probe before connecting its DNS/TLS route. Never let PR code supply the host compose file or deployment script.
- Local translation requires an explicitly configured model provider and embedding model/dimensions. Yoga's verified provider is local Ollama: `gemma4:e4b`, with `nomic-embed-text:latest` embeddings at 768 dimensions. Background event runtimes can remain disabled while manual publication translation dispatch runs.
- CMS now uses `/api/cms/documents`, organization-scoped local content and generated publication resources. New documents enter review; publication intent is separate from the immutable translated candidate and its review history. Do not route CMS document saves through the retired ingestion proxy.
- Identity-offline proof stops only the source Axxium application container, then uses a fresh recipient login and actual browser content/review/translation actions. Record whether the source was restored; it is intentionally stopped for the current acceptance run.

## Clio CMS storage (2026-09-13)

- Knoxx on Stealth and Yoga consumes `eta-mu/packages/document-history` pinned to `a2f428afd7623dcd188d535512525ee521a5ba61`. The shared package calls Clio directly; Knoxx owns organization authorization and publication policy.
- CMS history lives at `/state/content/.ημ/cms/<organization>/`: retain `ledgers/*.edn` and `schemas/`; `snapshots/<document>/<hash>/metadata.edn`, `document.md` and `snapshot.edn` are disposable projections. Preserve stable `seeds/*.lock` and `operations/*.lock` inodes. Hidden pending ledger files are unaccepted interrupted writes.
- Each save records full EDN metadata and Markdown, authenticated actor, timestamp and editor-observed parents. Distinct physical ledgers replay as one logical history. Timestamps do not replace causal parents; concurrent heads remain until explicit reconciliation.
- PATCH `/api/cms/documents/:id` requires `parents`; never fetch current heads and substitute them for a stale editor's observed revision. Read history at `/api/cms/documents/:id/history`. Publication and translation refuse unresolved conflicts and stale immutable source paths. CMS publication intent writes and saves share a short synchronous Clio operation lock; never await while holding it.
- Legacy JSON/Markdown originals remain untouched after a one-time Clio import. Back up schemas and accepted ledgers together; regenerate snapshots instead of treating them as authority. Existing publication intents and approved translations are retained. Historical public/archived visibility is retained as EDN metadata; it does not grant publication. Logical workspace paths identify one organization-scoped history separately from its immutable snapshot paths.
- Install/rebuild the pinned native `fs-ext-extra-prebuilt` 2.2.9 dependency for the image's Node ABI. The current isolated runtime is `knoxx-backend:clio-2644fc6` / `knoxx-frontend:clio-2644fc6`, source revision `2644fc6c51bbbcda599964a5a674415b58344c3b`.
- Rheos still has Markdown-first task mutations and incomplete audit events. Its canonical-fold and Markdown-sync work owns adopting this shared package; do not describe Rheos as migrated.

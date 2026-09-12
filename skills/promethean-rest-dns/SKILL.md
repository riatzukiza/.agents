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

---
name: promethean-host-runtime-inventory
description: Inventory Promethean hosts over SSH, map nested public names to live runtimes or placeholders, and verify DNS, certificate coverage, and HTTP behavior separately.
license: GPL-3.0
compatibility:
  - opencode
  - codex
---

# Skill: Promethean Host Runtime Inventory

## Goal
Produce an evidence-based fleet snapshot distinguishing container routes, host processes,
proxy placeholders, references, and unreachable or ambiguous hosts, including nested TLS coverage.

## Use This Skill When
- Inventorying Promethean hosts, runtimes, public subdomains, or certificates.
- Checking what actually serves `testing.knoxx.promethean.rest` or another nested name.
- Preparing durable JSON and Markdown runtime/route records.

## Do Not Use This Skill When
- Only creating DNS: use `promethean-rest-dns` (DNS alone does not configure TLS).
- Only checking a single HTTP endpoint without runtime inspection.
- No SSH or alternative source of runtime evidence exists, or hosts are outside this fleet.

## Inputs
- Explicit requested hosts take precedence; do not scan the fleet for a Knoxx-only request.
- Fleet pool: `knoxx.promethean.rest`, `ussy.promethean.rest`, `ussy2.promethean.rest`,
  `ussy3.promethean.rest`, `big.ussy.promethean.rest`.
- SSH user/host, actual runtime/proxy source paths, public names, and report timestamp.
- Known Knoxx evidence (2026-09-12): `err@knoxx.promethean.rest`, public IPv4 `157.245.125.134`,
  host identity `open-hax-services-production`; revalidate before reuse.

## Steps
1. Reuse specifications, receipts, and deployment notes for SSH users and runtime roots.
   A service hostname is not necessarily an SSH host, and a device label is not runtime placement.
2. Verify SSH with batch mode and a bounded connection timeout. Inventory Docker first,
   then Podman, systemd, or Proxmox/LXC/QEMU when applicable. Do not install a runtime just to inventory it.
3. Discover the owner of ports 80/443 and actual mounts/Compose labels. On Knoxx:
   - Container: `caddy-caddy-1`, Caddy 2.8.4, image `caddy:2.8-alpine`.
   - Config: `/srv/open-hax/services/caddy/Caddyfile` and `compose.yaml`.
   - Persistent certificate storage: `/srv/open-hax/state/caddy/data`.
   - Deployment source: `open-hax/services/digitalocean/services/caddy/Caddyfile`.
   - Read named public-host env values only; never dump all container environment or `.env` files.
4. Read the live route configuration and, where available, active adapted config. Redact credentials,
   password hashes, private keys, and authorization headers before any tool output/artifact.
   Inspect public certificate files or TLS handshakes; private-key reads are unnecessary.
5. Classify each declared route using live evidence:
   - `publicContainerRoutes`: actual running application container upstream.
   - `hostProcessRoutes`: verified host listener/process upstream.
   - `proxyOnlyRoutes`: static response, redirect-only, or placeholder with no application upstream.
   - `referencesOnly`: hostname mentioned in config/env but not served here.
   Record unverified upstreams explicitly instead of guessing a class from a hostname.
6. Verify **each full hostname**, including nested names, independently:
   - Public A/AAAA/CNAME answers, expected origin, and Cloudflare proxy mode if API access exists.
   - Public TLS handshake with SNI equal to that hostname, normal CA trust, and hostname validation.
   - Certificate SANs, issuer, expiry, and verification result. If DNS fails, record that separately;
     an optional direct-origin probe must keep the public SNI/Host and be labeled as an origin probe.
   - HTTP→HTTPS redirect and HTTPS status. Test expected auth rejection without credentials.
   - A `404` placeholder can have fully valid TLS; do not report it as a healthy application.
     A `401` on a guarded dev route can be expected; a `502` means the upstream needs investigation.
7. Verify certificate automation configuration:
   - Exact Caddy sites use automatic ACME issuance/renewal when challenges can reach the host.
   - `*.promethean.rest` does not cover `testing.knoxx.promethean.rest`.
   - `*.knoxx.promethean.rest` covers one label below Knoxx, not Knoxx itself or another deeper level.
   - Wildcard ACME requires DNS-01, an installed provider plugin, usable scoped credentials, and durable storage.
   - Cloudflare edge and origin certificates are separate; public success through a proxy is not origin proof.
   - Distinguish “renewal configured” from an actually observed successful renewal event.
8. Write dated JSON and Markdown under `docs/reports/inventory/` in the active repository.
   In a projectless Codex task, use its `outputs/` directory instead. Record unreachable hosts,
   DNS failures, unverified origin/edge modes, and ambiguous upstreams without smoothing them over.
9. Append start, decisions, verification, and handoff to the project's existing Receipt River
   (`.ημ/receipts.edn` or `receipts.edn`); preserve prior entries and do not introduce new `receipts.log` files.

## JSON record shape
```json
{
  "generatedAt": "ISO-8601",
  "records": [{
    "host": "knoxx.promethean.rest",
    "sshTarget": "err@knoxx.promethean.rest",
    "runtime": {
      "docker": {"available": true, "runningCount": 7},
      "podman": {"available": false},
      "systemd": {"available": true},
      "proxmox": {"available": false}
    },
    "publicContainerRoutes": [],
    "hostProcessRoutes": [],
    "proxyOnlyRoutes": [{
      "hostname": "testing.knoxx.promethean.rest",
      "kind": "placeholder",
      "upstream": null,
      "dns": {"ipv4": ["157.245.125.134"], "proxied": false},
      "tls": {
        "probe": "public", "verified": true,
        "sans": ["testing.knoxx.promethean.rest"],
        "issuer": "record from handshake", "notAfter": "record from handshake",
        "automation": "Caddy exact-host ACME; persistent storage",
        "renewalObserved": false
      },
      "http": {"status": 308, "httpsStatus": 404, "expected": true}
    }],
    "referencesOnly": [],
    "containers": [],
    "notes": []
  }]
}
```
The shape is illustrative, not a new scan result. Use `null` plus an explanation when a field was not checked.

## Output
- JSON inventory and Markdown report, with evidence timestamp and host boundary.
- Summary separating public TLS validity, routing, application health, and unresolved checks.
- For Proxmox hosts such as `big.ussy`, include systemd and `/etc/pve` evidence even without Docker.

## References
- [Promethean DNS and nested TLS workflow](../promethean-rest-dns/SKILL.md)
- [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)
- [Cloudflare Universal SSL limitations](https://developers.cloudflare.com/ssl/edge-certificates/universal-ssl/limitations/)

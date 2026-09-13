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
- Preparing durable EDN and Markdown runtime/route records.

## Do Not Use This Skill When
- Only creating DNS: use `promethean-rest-dns` (DNS alone does not configure TLS).
- Only checking a single HTTP endpoint without runtime inspection.
- No SSH or alternative source of runtime evidence exists, or hosts are outside this fleet.

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
8. Write dated EDN and Markdown under `docs/reports/inventory/` in the active repository.
   In a projectless Codex task, use its `outputs/` directory instead. Record unreachable hosts,
   DNS failures, unverified origin/edge modes, and ambiguous upstreams without smoothing them over.
9. Append start, decisions, verification, and handoff to the project's existing Receipt River
   (`.ημ/receipts.edn` or `receipts.edn`); preserve prior entries and do not introduce new `receipts.log` files.

## EDN record shape
```clojure
{:generated-at "ISO-8601" :records [{:host "knoxx.promethean.rest" :ssh-target "err@knoxx.promethean.rest" :runtime {:docker {:available true :running-count 7} :podman {:available false} :systemd {:available true} :proxmox {:available false}} :public-container-routes [] :host-process-routes [] :proxy-only-routes [{:hostname "testing.knoxx.promethean.rest" :kind "placeholder" :upstream nil :dns {:ipv4 ["157.245.125.134"] :proxied false} :tls {:probe "public" :verified true :sans ["testing.knoxx.promethean.rest"] :issuer "record from handshake" :not-after "record from handshake" :automation "Caddy exact-host ACME; persistent storage" :renewal-observed false} :http {:status 308 :https-status 404 :expected true}}] :references-only [] :containers [] :notes []}]}
```
The shape is illustrative, not a new scan result. Use `nil` plus an explanation when a field was not checked.

## Output
- EDN inventory and Markdown report, with evidence timestamp and host boundary.
- Summary separating public TLS validity, routing, application health, and unresolved checks.
- For Proxmox hosts such as `big.ussy`, include systemd and `/etc/pve` evidence even without Docker.

## References
- [Promethean DNS and nested TLS workflow](../promethean-rest-dns/SKILL.md)
- [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)
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

- Knoxx on Stealth and Yoga consumes `eta-mu/packages/document-history` pinned to `2a7ca613a179520f3fd693c34e3cf4b19c79c8c9`. The shared package calls Clio directly; Knoxx owns organization authorization and publication policy.
- CMS history lives at `/state/content/.ημ/cms/<organization>/`: retain `ledgers/*.edn` and `schemas/`; `snapshots/<document>/<hash>/metadata.edn`, `document.md` and `snapshot.edn` are disposable projections. Preserve stable `seeds/*.lock` and `operations/*.lock` inodes. Hidden pending ledger files are unaccepted interrupted writes.
- Each save records full EDN metadata and Markdown, authenticated actor, timestamp and editor-observed parents. Distinct physical ledgers replay as one logical history. Timestamps do not replace causal parents; concurrent heads remain until explicit reconciliation.
- PATCH `/api/cms/documents/:id` requires `parents`; never fetch current heads and substitute them for a stale editor's observed revision. Read history at `/api/cms/documents/:id/history`. Publication and translation refuse unresolved conflicts and stale immutable source paths. CMS publication intent writes and saves share a short synchronous Clio operation lock; never await while holding it.
- Legacy JSON/Markdown originals remain untouched after a one-time Clio import. Back up schemas and accepted ledgers together; regenerate snapshots instead of treating them as authority. Existing publication intents and approved translations are retained. Historical public/archived visibility is retained as EDN metadata; it does not grant publication. Logical workspace paths identify one organization-scoped history separately from its immutable snapshot paths.
- Install/rebuild the pinned native `fs-ext-extra-prebuilt` 2.2.9 dependency for the image's Node ABI. The current isolated runtime is `knoxx-backend:clio-91f9902` / `knoxx-frontend:clio-91f9902`, source revision `91f9902a8ce0685819d76925da402a9ac47aa7d1`.
- Rheos still has Markdown-first task mutations and incomplete audit events. Its canonical-fold and Markdown-sync work owns adopting this shared package; do not describe Rheos as migrated.

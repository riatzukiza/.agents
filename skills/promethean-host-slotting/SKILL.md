---
name: promethean-host-slotting
description: Choose per-service environment hosts, public names, private ingress paths, persistent state, and isolated runtime project names.
license: GPL-3.0-or-later
---

# Promethean Host Slotting

## Use this skill when
Selecting or extending runtime placement for a Promethean service environment.

## Do not use when
Only changing DNS or validating an existing single endpoint.

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

## Placement procedure
1. Honor the explicit host choice and inventory it. The fleet pool also includes ussy, ussy2, ussy3 and big.ussy; do not scan or relocate to them for a Stealth/Yoga-only request.
2. Separate public ingress placement from application placement. On Knoxx, Caddy owns 80/443 under `/srv/open-hax/services/caddy`; retain that owner.
3. Local device runtime roots are `~/.local/share/promethean/services/<service>-<env>`, Compose projects `<service>-<env>`, private application binds on loopback, and environment-specific persistent state.
4. Reuse a verified private transport. Current host routes use user-systemd reverse SSH forwards to Knoxx loopback, then Caddy-only bridge relays. Do not assume Stealth has Tailscale connectivity: it was logged out during the 2026-09-13 setup.
5. Keep application, database, identity/session secrets, volumes and resource budgets isolated. Never give an application the host Docker socket.
6. Allocate and check ports before installation. Record local bind, remote forward and Caddy relay separately. Restrict relay ingress to the actual Caddy source address and verify a connection from the running proxy container.
7. Emit the SSH target, exact public name, state root, Compose project, image digest, upstream route, proxy source and verification commands.

## Current private port allocation (2026-09-13; revalidate)
| Service/environment | Application loopback | Knoxx SSH loopback | Caddy bridge relay |
|---|---:|---:|---:|
| Axxium/Stealth | 18877 | 19777 | 172.31.255.1:19077 |
| Axxium/Yoga | 18877 | 19778 | 172.31.255.1:19078 |
| Knoxx/Stealth | 18880 | 19780 | 172.31.255.1:19080 |
| Knoxx/Yoga | 18880 | 19781 | 172.31.255.1:19081 |

Caddy's source on the dedicated bridge is `172.31.255.2`. Port reuse on different application hosts is intentional. An installed route is not proof its upstream is running; inventory actual state.

## Installed environment runtime (2026-09-13)

- Stealth is `192.168.12.128`; Yoga is `192.168.12.68`. Both have isolated Axxium and Knoxx compose projects under `~/.local/share/promethean/services/`. Stealth Tailscale is logged out; use LAN SSH where applicable.
- Knoxx ingress owns public TLS. Host applications reach its private relays through user-systemd SSH forwards. `testing` and `staging` slots live at `/srv/open-hax/environments/<env>/<service>`; their separate forced-command keys accept only admitted image archives.
- The source-controlled controller is in `open-hax/services`; app callers activate after review and merge to main. A configured hostname with a 503 placeholder is not a deployed application.
- For a new service, add a reviewed build recipe, fixed compose template, restricted receiver slot and health probe before connecting its DNS/TLS route. Never let PR code supply the host compose file or deployment script.
- Local translation requires an explicitly configured model provider and embedding model/dimensions. Yoga's verified provider is local Ollama: `gemma4:e4b`, with `nomic-embed-text:latest` embeddings at 768 dimensions. Background event runtimes can remain disabled while manual publication translation dispatch runs.
- CMS now uses `/api/cms/documents`, organization-scoped local content and generated publication resources. New documents enter review; publication intent is separate from the immutable translated candidate and its review history. Do not route CMS document saves through the retired ingestion proxy.
- Identity-offline proof stops only the source Axxium application container, then uses a fresh recipient login and actual browser content/review/translation actions. Record whether the source was restored; it is intentionally stopped for the current acceptance run.

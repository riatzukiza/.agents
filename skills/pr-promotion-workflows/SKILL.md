---
name: pr-promotion-workflows
description: Implement code-owner testing-label leases, successful main merges to staging, and integration/e2e/mutation-qualified production promotion.
license: GPL-3.0-or-later
---

# PR Promotion Workflows

## Use this skill when
Creating or revising PR environment admission and production qualification.

## Do not use when
The task only runs an already-correct pipeline unchanged or only provisions DNS.

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

## Procedure
1. Inspect existing workflows, branch rules and environment protection. Remove active main-to-production and staging-branch assumptions when implementing this policy; do not leave a manual bypass around qualification.
2. Fetch paginated current PRs, label timeline events, changed files and trusted ownership rules. Fail closed on missing timestamps, ambiguous ownership, changed heads or unavailable evidence.
3. Keep validation and host mutation separate. Per-service testing concurrency covers the final lease recheck and complete deploy, not unrelated no-op events.
4. Run production qualification in independent ephemeral jobs. Establish a passing baseline in each mutation batch, partition stable mutation fingerprints, require hundreds per batch and aggregate all four against one SHA.
5. Required checks must run nonempty relevant suites and inspect actual failure counters. Do not use allow-failure, skipped jobs, planned mutation manifests, or compile errors as passing proof.
6. Validate workflow syntax and admission race/expiry cases. Exercise actual events only after the reviewed workflow is on the authoritative branch and destination environments are configured.
7. Record protected-environment requirements and pinned controller version. Report configuration and observed enforcement separately.

## Installed environment runtime (2026-09-13)

- Stealth is `192.168.12.128`; Yoga is `192.168.12.68`. Both have isolated Axxium and Knoxx compose projects under `~/.local/share/promethean/services/`. Stealth Tailscale is logged out; use LAN SSH where applicable.
- Knoxx ingress owns public TLS. Host applications reach its private relays through user-systemd SSH forwards. `testing` and `staging` slots live at `/srv/open-hax/environments/<env>/<service>`; their separate forced-command keys accept only admitted image archives.
- The source-controlled controller is in `open-hax/services`; app callers activate after review and merge to main. A configured hostname with a 503 placeholder is not a deployed application.
- For a new service, add a reviewed build recipe, fixed compose template, restricted receiver slot and health probe before connecting its DNS/TLS route. Never let PR code supply the host compose file or deployment script.
- Local translation requires an explicitly configured model provider and embedding model/dimensions. Yoga's verified provider is local Ollama: `gemma4:e4b`, with `nomic-embed-text:latest` embeddings at 768 dimensions. Background event runtimes can remain disabled while manual publication translation dispatch runs.
- CMS now uses `/api/cms/documents`, organization-scoped local content and generated publication resources. New documents enter review; publication intent is separate from the immutable translated candidate and its review history. Do not route CMS document saves through the retired ingestion proxy.
- Identity-offline proof stops only the source Axxium application container, then uses a fresh recipient login and actual browser content/review/translation actions. Record whether the source was restored; it is intentionally stopped for the current acceptance run.

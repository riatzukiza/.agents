---
name: promethean-service-deploy
description: Deploy Promethean services through per-service host environments, code-owner testing leases, main-to-staging merges, and evidence-gated production promotion.
license: GPL-3.0-or-later
---

# Promethean Service Deployment

## Use this skill when
A user asks to deploy a service, repair its deployment pipeline, or add a public environment.

## Do not use when
Only a DNS record or read-only inventory is needed; use the linked specialized skill.

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
1. Read the service and Services repository contracts; inventory existing hosts and source/live revision before changing them. Preserve device branches and unrelated runtimes.
2. Use [host slotting](../promethean-host-slotting/SKILL.md) to select application host, ingress, private upstream, state path and Compose project independently.
3. Build the application, run relevant tests and package a non-root runtime. Keep databases and signing keys separate for each environment; never copy production secrets into testing.
4. Use [DNS](../promethean-rest-dns/SKILL.md) to plan/apply DNS-only records and provision exact-host Caddy HTTPS. Preserve existing port owners, auth guards and durable ACME state. Use placeholders only when that is the requested outcome.
5. Use [PR promotion](../pr-promotion-workflows/SKILL.md) for label admission, merge deployment and production qualification. Bind image digests, test evidence and deployment to the same source SHA.
6. Deploy with distinct state and project names. Validate the candidate proxy config before reload and keep a rollback file. Update source configuration and runtime instructions with the live change.
7. Verify public DNS, TLS, HTTP redirect, anonymous auth rejection, application readiness and user behavior separately. Recheck unrelated production routes after ingress changes.
8. For cross-host identity acceptance, register and copy through the browser, verify the same subject at the recipient, sign out, stop only the authorized source service, then prove a fresh recipient login and real content/review/translation workflows. Capture screenshots; API-only checks are not browser evidence.
9. Record the exact stopped service and restoration state. Report remaining failures or review/merge steps honestly; do not claim a placeholder or pending workflow is deployed.

## Output
Source changes, deployment/rollback instructions, host inventory, test receipts, public URLs, browser evidence and explicit live-versus-pending status.

## Installed environment runtime (2026-09-13)

- Stealth is `192.168.12.128`; Yoga is `192.168.12.68`. Both have isolated Axxium and Knoxx compose projects under `~/.local/share/promethean/services/`. Stealth Tailscale is logged out; use LAN SSH where applicable.
- Knoxx ingress owns public TLS. Host applications reach its private relays through user-systemd SSH forwards. `testing` and `staging` slots live at `/srv/open-hax/environments/<env>/<service>`; their separate forced-command keys accept only admitted image archives.
- The source-controlled controller is in `open-hax/services`; app callers activate after review and merge to main. A configured hostname with a 503 placeholder is not a deployed application.
- For a new service, add a reviewed build recipe, fixed compose template, restricted receiver slot and health probe before connecting its DNS/TLS route. Never let PR code supply the host compose file or deployment script.
- Local translation requires an explicitly configured model provider and embedding model/dimensions. Yoga's verified provider is local Ollama: `gemma4:e4b`, with `nomic-embed-text:latest` embeddings at 768 dimensions. Background event runtimes can remain disabled while manual publication translation dispatch runs.
- CMS now uses `/api/cms/documents`, organization-scoped local content and generated publication resources. New documents enter review; publication intent is separate from the immutable translated candidate and its review history. Do not route CMS document saves through the retired ingestion proxy.
- Identity-offline proof stops only the source Axxium application container, then uses a fresh recipient login and actual browser content/review/translation actions. Record whether the source was restored; it is intentionally stopped for the current acceptance run.

## Clio CMS storage (2026-09-13)

- Knoxx on Stealth and Yoga consumes `eta-mu/packages/document-history` pinned to `61a60f0e9d19768475ef5abd5251bbdccf1d3015`. The shared package calls Clio directly; Knoxx owns organization authorization and publication policy.
- CMS history lives at `/state/content/.ημ/cms/<organization>/`: retain `ledgers/*.edn` and `schemas/`; `snapshots/<document>/<hash>/metadata.edn`, `document.md` and `snapshot.edn` are disposable projections. Preserve stable `seeds/*.lock` inodes. Hidden pending ledger files are unaccepted interrupted writes.
- Each save records full EDN metadata and Markdown, authenticated actor, timestamp and editor-observed parents. Distinct physical ledgers replay as one logical history. Timestamps do not replace causal parents; concurrent heads remain until explicit reconciliation.
- PATCH `/api/cms/documents/:id` requires `parents`; never fetch current heads and substitute them for a stale editor's observed revision. Read history at `/api/cms/documents/:id/history`. Publication and translation refuse unresolved conflicts.
- Legacy JSON/Markdown originals remain untouched after a one-time Clio import. Back up schemas and accepted ledgers together; regenerate snapshots instead of treating them as authority. Existing publication intents and approved translations are retained.
- Install/rebuild the pinned native `fs-ext-extra-prebuilt` 2.2.9 dependency for the image's Node ABI. The current isolated runtime is `knoxx-backend:clio-fd7b5ae` / `knoxx-frontend:clio-fd7b5ae`, source revision `fd7b5ae9591b94187ad04445f6ab7904b70135f1`.
- Rheos still has Markdown-first task mutations and incomplete audit events. Its canonical-fold and Markdown-sync work owns adopting this shared package; do not describe Rheos as migrated.

---
name: cloudflare-tunnel-ops
version: 2
description: End-to-end Cloudflare Tunnel automation for publishing services behind NAT, including API management, tunnel token retrieval, cloudflared discovery, installation, config generation, and service setup.
---

# Cloudflare Tunnel Ops

This skill automates both the Cloudflare control plane and the `cloudflared` runtime plane so you can publish a service behind NAT without digging through the dashboard.

## Inputs

Environment variables:

- `CF_API_TOKEN` — required for Cloudflare API operations.
- `CF_ACCOUNT_ID` — required for account-scoped tunnel APIs.
- `CF_ZONE_ID` — required for DNS record operations.
- `CF_API_BASE` — optional; override API base URL for testing.
- `CF_TUNNEL_TOKEN` — optional if already known; otherwise retrieve it via API.

Parameters:

- `tunnel_name`
- `hostname`
- `service_url` — for example `http://localhost:3000`
- `runtime` — `systemd`, `docker`, `manual`
- `mode` — `token` or `config`
- `tunnel_id` — optional if using an existing tunnel

## Permissions

Required minimum:

- Account → `Cloudflare Tunnel Write`

Optional depending on behavior:

- Account → `Cloudflare Tunnel Read`
- Zone → `DNS Write`
- Zone → `Zone Read`

Cloudflare documents that a remotely managed tunnel can be created through the API and that the response includes both the tunnel `id` and a `token` used to run `cloudflared` . DNS publishing uses the standard DNS record API .

## Responsibilities

1. Verify the API token.
2. Create, inspect, list, and delete tunnel objects.
3. Retrieve a tunnel token.
4. Create and delete DNS routes pointing a hostname to `<tunnel-id>.cfargotunnel.com`.
5. Create a published application route in API terms by combining DNS + local service config.
6. Detect whether `cloudflared` is already installed.
7. Install `cloudflared` on supported Linux systems when requested.
8. Generate `cloudflared` config files with ingress rules.
9. Run `cloudflared` using token mode or config mode.
10. Install and manage a `systemd` service when appropriate.
11. Print the exact next command to run when full automation is not possible.

## Files in this bundle

- `cf-tunnel-fetch.cljs` — working NBB script for Cloudflare API tunnel, token, and DNS management.
- `cloudflared-setup.sh` — shell script for discovery, installation, config generation, service setup, and one-shot orchestration.
- `cloudflared-config.example.yml` — config template.
- `package.json` — NBB runtime dependency and script entrypoint.

## Typical automation flow

### Fast path: token mode

1. `create-tunnel`
2. `get-tunnel-token`
3. `create-dns-route`
4. `cloudflared-setup.sh install`
5. `cloudflared-setup.sh install-service`

### Config mode

1. `create-tunnel`
2. `create-dns-route`
3. `cloudflared-setup.sh write-config`
4. `cloudflared-setup.sh install-service`

## Notes

- `cloudflared` must run on a machine with outbound internet access.
- If you use config mode, ingress rules must include a catch-all rule at the end .
- Linux service mode is usually the best default for always-on hosts .

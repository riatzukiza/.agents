---
name: cloudflare-tunnel-ops
version: 3
description: "End-to-end Cloudflare Tunnel automation for publishing one or many services behind NAT, including API management, multi-hostname tunnel config, cloudflared setup, and SSL caveats for nested subdomains."
---

# Cloudflare Tunnel Ops

This skill automates both the Cloudflare control plane and the `cloudflared` runtime plane so you can publish services behind NAT without dashboard clicking. It supports single-hostname and multi-hostname tunnels, and warns about nested-subdomain SSL certificate requirements.

## Use This Skill When

- You need to expose local HTTP(S) services through a Cloudflare Tunnel.
- You want one tunnel with multiple hostnames mapping to different local ports.
- You want to script tunnel creation, DNS records, config generation, and systemd service setup.

## Do Not Use This Skill When

- You need non-Cloudflare DNS or tunnel providers.
- You are not authorized to create DNS records in the target zone.
- The local services are not yet listening; the tunnel will start but return 502s.

## Inputs

Environment variables:

- `CF_API_TOKEN` — required for Cloudflare API operations.
- `CF_ACCOUNT_ID` — required for account-scoped tunnel APIs.
- `CF_ZONE_ID` — required for DNS record operations.
- `CF_ZONE_NAME` — optional; defaults to `promethean.rest`.
- `CF_API_BASE` — optional; override API base URL for testing.
- `CF_TUNNEL_TOKEN` — optional; retrieved via API if omitted.

Parameters:

- `tunnel_name` — name for the tunnel object.
- `hostname` / `service_url` — single route; for example `app.example.com` and `http://localhost:3000`.
- `INGRESS_RULES` — multi-line `host=service` pairs for multi-hostname tunnels.
- `runtime` — `systemd`, `docker`, or `manual`.
- `mode` — `token` or `config`.
- `tunnel_id` — optional if reusing an existing tunnel.

## Permissions

Required minimum:

- Account → `Cloudflare Tunnel Write`
- Zone → `DNS Write`

Optional depending on behavior:

- Account → `Cloudflare Tunnel Read`
- Zone → `Zone Read`

## Files in this bundle

- `cf-tunnel-fetch.mjs` — Node.js script for Cloudflare API tunnel, token, and DNS management (preferred).
- `cf-tunnel-fetch.cljs` — NBB script for the same operations (kept for legacy environments with a working NBB).
- `create-tunnel-stack.mjs` — one-shot orchestration: tunnel + DNS + config + service.
- `cloudflared-setup.sh` — discovery, installation, config generation, service setup.
- `cloudflared-config.example.yml` — config template.
- `package.json` — script entrypoints.

## Typical automation flow

### One-shot multi-hostname stack (recommended)

```bash
node create-tunnel-stack.mjs err-stealth \
  knoxx.stealth.promethean.rest=8000 \
  sol.stealth.promethean.rest=8001 \
  rheos.stealth.promethean.rest=8792 \
  proxx.stealth.promethean.rest=8789 \
  radar.stealth.promethean.rest=7878
```

This creates the tunnel, CNAME records, config, credentials, installs `cloudflared`, and starts the systemd service.

### Manual multi-hostname flow

1. Create tunnel:
   ```bash
   node cf-tunnel-fetch.mjs create-tunnel err-stealth
   ```
2. Note the returned `id` and `credentials_file.TunnelSecret`.
3. Create DNS routes:
   ```bash
   TUNNEL_ID="<tunnel-id>"
   node cf-tunnel-fetch.mjs create-route-for-tunnel promethean.rest knoxx.stealth "$TUNNEL_ID"
   node cf-tunnel-fetch.mjs create-route-for-tunnel promethean.rest sol.stealth "$TUNNEL_ID"
   # ... repeat for each host
   ```
4. Install `cloudflared`:
   ```bash
   bash cloudflared-setup.sh install
   ```
5. Write credentials and config:
   ```bash
   export TUNNEL_ID="<tunnel-id>"
   export TUNNEL_NAME="err-stealth"
   export TUNNEL_SECRET="<tunnel-secret>"
   export CF_ACCOUNT_ID="$CF_ACCOUNT_ID"
   export INGRESS_RULES="knoxx.stealth.promethean.rest=http://localhost:8000
   sol.stealth.promethean.rest=http://localhost:8001
   rheos.stealth.promethean.rest=http://localhost:8792
   proxx.stealth.promethean.rest=http://localhost:8789
   radar.stealth.promethean.rest=http://localhost:7878"
   bash cloudflared-setup.sh write-credentials
   bash cloudflared-setup.sh write-config
   ```
6. Install service:
   ```bash
   bash cloudflared-setup.sh install-service
   ```

### Legacy single-hostname flow

```bash
export HOSTNAME_ROUTE=app.example.com
export SERVICE_URL=http://localhost:3000
bash cloudflared-setup.sh automate
```

## SSL certificate warning for nested subdomains

Cloudflare Universal SSL covers `*.example.com`, but **not** `*.stealth.example.com`. If you create CNAME records like `knoxx.stealth.example.com`, visitors will get an SSL handshake error unless you have one of:

- **Advanced Certificate Manager** with a custom hostname pattern covering `*.stealth.example.com`.
- **Total TLS** enabled and a valid certificate authority configured.
- A flat hostname scheme like `knoxx-stealth.example.com` instead.

To check the current certificate hosts:

```bash
curl -sS -H "Authorization: Bearer $CF_API_TOKEN" \
  "https://api.cloudflare.com/client/v4/zones/$CF_ZONE_ID/ssl/certificate_packs" \
  | python3 -m json.tool
```

## Notes

- `cloudflared` must run on a machine with outbound internet access.
- Ingress rules must include a catch-all rule at the end; `cloudflared-setup.sh` adds `http_status:404` automatically.
- Linux service mode is usually the best default for always-on hosts.
- The Node.js scripts use native `fetch` and work with Node 18+. The NBB script uses `promesa.core` and requires a working NBB installation.
- If `verify-token` returns 401 but zone-scoped calls succeed, the token lacks the `user` permission but has the required zone/account permissions.

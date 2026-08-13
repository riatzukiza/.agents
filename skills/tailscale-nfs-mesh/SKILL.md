---
name: tailscale-nfs-mesh
description: "Provision machines into a Tailscale VPN mesh with NFS filesystem mounts and optional Cloudflare Tunnels. Installs Tailscale, NFS, and cloudflared; configures exports, mounts, host resolution, and systemd services. Sends desktop notifications and opens browser for auth URLs."
---

# Tailscale NFS Mesh

Automates provisioning new machines into the workspace's Tailscale + NFS mesh network.

## Use This Skill When

- Adding a new machine to the network
- Re-provisioning an existing machine after reinstall
- Setting up NFS mounts between machines over Tailscale
- Need to install cloudflared and create tunnels for a new host

## Do Not Use This Skill When

- Machines are already provisioned (check `tailscale status` first)
- You only need Tailscale without NFS (just run `tailscale up` manually)
- You only need Cloudflare Tunnels without NFS (use `cloudflare-tunnel-ops` skill)

## Inputs

A JSON config file passed as the first argument, or interactive prompts. Example config:

```json
{
  "hosts": [
    {
      "name": "knoxx",
      "ssh": "157.245.125.134",
      "user": "root",
      "ts_ip": "100.103.156.0",
      "exports": ["/home/err/spaces"]
    },
    {
      "name": "yoga",
      "ssh": "192.168.12.68",
      "user": "err",
      "ts_ip": "100.105.116.31",
      "exports": ["/home/err/devel"]
    }
  ],
  "local_host": "stealth",
  "local_ts_ip": "100.77.244.9",
  "zone_name": "promethean.rest",
  "cloudflare": true
}
```

## Environment Variables

- `CF_API_TOKEN` — required if `cloudflare` is true
- `CF_ACCOUNT_ID` — required if `cloudflare` is true
- `CF_ZONE_ID` — required if `cloudflare` is true

## Steps

1. Install Tailscale on each remote host
2. Generate Tailscale auth URLs and open browser with delay
3. Wait for user approval (poll `tailscale status`)
4. Add `/etc/hosts` entries for `*-ts` hostnames on all machines
5. Install NFS server and client on each host
6. Create `~/networks/tailscale/<machine-name>` directories
7. Configure `/etc/exports` on each host
8. Add fstab entries and mount all shares
9. (Optional) Install cloudflared and create tunnels
10. Send desktop notification when complete

## Output

- All machines joined to Tailscale mesh
- NFS mounts active at `~/networks/tailscale/<machine-name>`
- `/etc/hosts` entries for Tailscale hostname resolution
- fstab entries for boot-time mounting
- Cloudflare tunnels running (if enabled)

## Files in this bundle

- `scripts/mesh-setup.cljs` — Main nbb script for one-shot provisioning
- `scripts/mesh-monitor.bb` — Babashka script for polling Tailscale status and recording changes

## Mesh monitoring

The `mesh-monitor.bb` script polls Tailscale status across all mesh members and records changes to an append-only EDN ledger at `/home/err/networks/tailscale/ledger/tailscale-mesh.edn` on knoxx.

### Event types

- `:snapshot` — Full mesh state (all members, IPs, online status)
- `:member-join` — New member appeared on the mesh
- `:member-leave` — Member disappeared from the mesh
- `:member-change` — Member's online/offline status changed

### Usage

```bash
# Take a snapshot (run manually or via systemd timer)
bb ~/.agents/skills/tailscale-nfs-mesh/scripts/mesh-monitor.bb snapshot

# View last 20 ledger entries
bb ~/.agents/skills/tailscale-nfs-mesh/scripts/mesh-monitor.bb log

# View current known members
bb ~/.agents/skills/tailscale-nfs-mesh/scripts/mesh-monitor.bb members

# View ledger statistics
bb ~/.agents/skills/tailscale-nfs-mesh/scripts/mesh-monitor.bb status
```

### Systemd timer (on knoxx)

A systemd timer runs `mesh-monitor.bb snapshot` every 5 minutes on knoxx:
- Service: `mesh-monitor.service`
- Timer: `mesh-monitor.timer`
- Log: `journalctl -u mesh-monitor.service`

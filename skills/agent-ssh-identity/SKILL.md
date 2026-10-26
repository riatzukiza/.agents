---
name: agent-ssh-identity
description: Use distinct harness SSH accounts on knoxx.promethean.rest, stealth and yoga, and preserve Linux audit login identity through privileged operations.
license: GPL-3.0-or-later
---

# Agent SSH identity

Use when a named harness performs SSH work on any provisioned host (knoxx.promethean.rest, stealth or yoga as destination) or when provisioning a new host. Do not use to select a different harness identity, provision unrelated hosts, or expand task authorization.

Read `contexts/<harness>.md` before connecting. Accounts are codex, grokbot, opencode, hermes, pi, eta-mu, knoxx, claude, and agy. Choose the actual executing harness, not the model provider, fictional persona, repository name, or task author. Subagents within one harness share that harness identity; record task/session IDs separately.

## Multi-device mesh (2026-10-26)

The nine accounts and the UID map (1002-1010) are provisioned on stealth, yoga
and knoxx. `identities.tsv` carries the knoxx transport enrollment (keys
generated on stealth); `identities-stealth.tsv` and `identities-yoga.tsv` map
each device's own mesh keys. Each device holds its private keys under
`~/.ssh/agent-identities/<device>/<harness>` and connects with its own device
key; peer devices hold only the public halves.

Per-device aliases: `<device>-agent-<harness>` (loopback login, needed for
attribution of locally running harnesses, because privileged work under
another account does not establish a loginuid), `<peer>-agent-<harness>`
(cross-device), and `knoxx-agent-<harness>` (to knoxx; on stealth that alias is
defined twice and the legacy transport keys in identities.tsv remain
first-alias and win). Cross-device connections use the peer's LAN address,
never a 100.x Tailscale address: Tailscale SSH intercepts port 22 on the
tailscale interface, replaces the OpenSSH session, and the audit loginuid is
then unset (observed 4294967295). Loopback and knoxx.promethean.rest reach
real sshd.

Provisioning a new host: run `provision-host.sh` there as root (users,
sudoers, auditd, persistent 70/75 rules); `provision-forwarder-env.sh`,
`install-audit-shipper-credential.py` and `enroll-agent-keys.sh` complete the
shipping and enrollment parts. `gen-ssh-mesh.bb` regenerates the ssh_config
blocks. Off-host: `linux-audit-forwarder` services on stealth/yoga ship raw
records to the shared Atlas `linux_audit.events`; the central projector
`central-audit-graph` on knoxx projects ALL hosts' scoped records into the
OpenPlanner graph (requires the read-only central reader credential at
`/etc/audit-central-projector/config.json`, installed by
`audit-central-projector/install-central-credential.py`).

## Atlas management

Use [atlas-cli](../atlas-cli/SKILL.md) for Atlas users, roles and cluster
administration. Prefer an existing CLI login and native commands over walking
the user through browser forms. Browser interaction is an authentication or
capability fallback. Never sync CLI credentials with this skill repository;
service database credentials remain separately scoped and installed.

## Connection contract

On stealth, SSH aliases are `knoxx-agent-<harness>` and private keys live at `/home/err/.ssh/agent-identities/knoxx/<harness>`. Use explicit key selection, `IdentitiesOnly=yes`, and no SSH agent forwarding. Never copy private keys into repositories, prompts, logs, remote homes, or another agent's configuration. These unencrypted automation keys are mode 0600 inside a mode 0700 directory, accessible to local err; they distinguish remote login identity, not local harness security boundaries. A different source device needs a separately generated key, a public-key enrollment by an authorized administrator, and a new source-device manifest entry.

```bash
ssh knoxx-agent-codex 'id; cat /proc/self/loginuid; sudo -n id'
```

Replace codex with your harness. Verify username and loginuid against `identities.tsv` before mutation. Stop on mismatch. Never fall back to err, root SSH, or a peer's account. Missing credentials are a blocker to report. Do not use `sudo -u <peer>` or `su` to impersonate another harness.

## Deployment account and required capabilities

Verified on 2026-10-22: the deployment account is `deploy` (UID 1000), owning `/srv/open-hax/services`, with membership in the host Docker group. From your own direct SSH login use `sudo -n -u deploy -H <command>` for deployment-account operations. This is an authorized service-account transition, not permission to impersonate another harness. A live Codex probe confirmed loginuid remains 1002 through this transition. Docker daemon work may run with a different loginuid; correlate client audit records with container IDs and receipts.

The user selected full Docker/Compose functionality and package installation. Retain the current broad sudo grant pending an explicit replacement policy. Full rootful Docker access remains root-equivalent, including through deploy. Off-host records with restricted ingestion credentials are the selected evidence strategy. See [ATLAS-AUDIT.md](ATLAS-AUDIT.md) for installation, verification and the separate existing Atlas administrator credential boundary. Do not claim local logs are protected from root.

## Work and privilege

Connect directly as your account so PAM sets the audit loginuid. Use sudo only for the privileged part of the authorized task; accounts have `NOPASSWD: ALL`. This capability does not authorize additional work. Respect repository instructions and ownership; use your home or an authorized checkout. Do not solve shared-checkout access errors with recursive chmod/chown. No provider credentials or Git identity are automatically installed in these homes.

Long-running workers must start from the matching account's login session. A job delegated to systemd, a container runtime, cron, or an existing daemon can lose loginuid attribution: retain the requesting account, task ID, unit/container ID and operation in a receipt, and verify the actual execution audit record before claiming attribution. A service named knoxx does not automatically use the knoxx harness identity.

## Audit verification and limits

The server uses PAM pam_loginuid, auditd and persistent `/etc/audit/rules.d/70-agent-identities.rules` rules for execve/execveat with each agent auid, including sudo descendants. `/etc/sudoers.d/90-agent-identities` grants sudo. Query:

```bash
sudo ausearch -if /var/log/audit/audit.log -k agent_exec -ua "$(id -u)" -ts recent -i
sudo auditctl -s
```

Match `auid` (login identity), `uid/euid` (execution identity), session, executable and command arguments. SSH authentication and sudo also appear in the system journal. Audit execution records do not capture every shell builtin, file read, or application-side action. Arguments can contain sensitive data: do not pass secrets on command lines. No sudo terminal/input-output recording is enabled by this skill.

All accounts can become root and therefore alter local logs or use each other's keys. This is operational accountability, not tamper-proof isolation. Off-host insert-only Atlas shipping is active; the backend credential is denied the raw audit database. Do not disable auditing, clear history, or alter loginuid to hide attribution. Check auditd health and lost-record count during incident review.

## Integrity audit coverage

Persistent `/etc/audit/rules.d/75-agent-integrity.rules` watches audit, sudo, SSH and account configuration writes/attribute changes. Agent-origin writes and attribute changes under `/var/log` use key `agent_log_change`, restricted to login UIDs 1002–1010. These detect changes; they do not deny them or guarantee Docker-daemon attribution. Both creation and deletion of an empty test file were observed with auid=codex and euid=root; lost=0 afterward. Audit configuration and loginuid are not locked immutable.

## Lifecycle

For rotation generate a fresh source-device key, enroll its public key, verify a new login and sudo/audit attribution, then revoke the old public key. For revocation remove authorized keys and stop authorized active sessions; password locking alone does not revoke SSH keys. Preserve UID mappings and audit evidence; never recycle an old agent UID without an explicit retention decision.

## Knowledge graph

See [AUDIT-GRAPH.md](AUDIT-GRAPH.md) for continuous, acknowledged-delivery projection into the existing OpenPlanner graph. Raw evidence remains protected in linux_audit.events; graph nodes/edges are derived projections, and command arguments are excluded. The existing graph REST/actor query route has a separately documented pre-existing failure.

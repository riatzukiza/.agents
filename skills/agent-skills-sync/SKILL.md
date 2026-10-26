---
name: agent-skills-sync
description: Inspect, operate, and repair two-way global skill synchronization between Stealth and Yoga using Unison, preserving device Git branches, local credentials, parked skills, and unresolved conflicts.
license: GPL-3.0-or-later
---

# Global skill synchronization

Use this skill to check or repair the Stealth/Yoga `~/.agents` shared catalog,
or to propagate an authorized skill change. Do not use it to sync credentials,
whole home directories, Git metadata, or repository board state.

## Ownership and scope

`~/.agents` remains the canonical skill root on each machine. Device Git
branches remain independent: sync transfers working files, not commits. The
Stealth user timer `agents-skills-sync.timer` initiates one two-way Unison run
every two minutes while both machines are reachable. It uses
`~/.unison/agents-skills.prf`, installed from [ops](ops/agents-skills.prf).
Unison is installed through Ubuntu APT on both hosts.

The shared paths are `skills/`, `skills.disabled/`, and `docs/notes/`.
Parked skills stay parked; syncing them does not activate them. `skills/synced`
is managed by its external provider and excluded, as are `.trash`, Git trees,
dependency directories, bytecode, runtime state, editor locks, and secret files.
Root README/AGENTS documentation, Receipt River ledgers, mycology state, Git
indexes/worktrees, SSH keys, and Atlas login/config remain local.

The automatic job propagates nonconflicting additions and edits in either
direction. It never chooses by timestamp and never automatically merges text
conflicts. Deletions are blocked on both roots; coordinate intentional removals
explicitly. Conflicts/offline failures are visible in systemd and the Unison
log; preserve both versions, reconcile with provenance, then rerun. No automatic
Git commit or push is performed by the timer. Commit/push owned changes through
the ordinary repository workflow and preserve concurrent edits.

## Operations

On Stealth, as the checkout owner:

```sh
systemctl --user status agents-skills-sync.timer
systemctl --user start agents-skills-sync.service
journalctl --user -u agents-skills-sync.service -n 30 --no-pager
unison agents-skills -batch -terse
```

Use [agent-ssh-identity](../agent-ssh-identity/SKILL.md) for remote diagnosis.
The coordinator connects as `yoga-agent-codex`, asserts the Codex loginuid at
installation, and runs only the Unison server as the checkout owner `err` via
sudo. The recurrent systemd job is infrastructure, not proof of the initiating
harness identity for each copied file; retain unit/task receipts.

Before repair, inspect both dirty trees and the initial reconciliation backups
under `~/.local/state/agents-sync/`. Do not reset either branch, copy `.git`,
force one replica, or erase Unison archives to hide conflicts. For new hosts,
review the desired catalog and transport before enrolling them.

After changes, compare file hashes on both machines and rerun once to verify
idempotence. Verify actual harness discovery; copying a file does not prove a
running agent has reloaded its catalog. Already-running sessions may need their
normal refresh/restart.

## Atlas

Use [atlas-cli](../atlas-cli/SKILL.md). Binary installation and authentication
are host-local prerequisites; never synchronize the Atlas token/config.

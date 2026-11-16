---
name: atlas-cli
description: Manage MongoDB Atlas projects, database users, custom roles, and clusters with the official Atlas CLI; use browser authentication only when the local CLI needs login.
license: GPL-3.0-or-later
---

# Atlas administration

Use this skill for Atlas management: database users, collection-scoped custom
roles, cluster metadata, and project settings. For reading or writing database
records, use an authorized MongoDB client or connector with separate database
credentials. This skill does not grant permission to expand access or change
unrelated projects.

## Default workflow

1. Use the official `atlas` CLI first. Check `atlas --version` and
   `atlas auth whoami`; do not print credential files or tokens.
2. Resolve the intended project before mutation. Existing Project 0 is
   `6a6d28b8c775136a11684778`, organization `6a6d28b7c775136a11684708`.
   Confirm defaults with a bounded read such as `atlas dbusers list`.
3. Use native commands and their installed `--help` for the requested action:

   ```sh
   atlas dbusers list --projectId 6a6d28b8c775136a11684778
   atlas customDbRoles list --projectId 6a6d28b8c775136a11684778
   atlas clusters list --projectId 6a6d28b8c775136a11684778
   atlas customDbRoles describe central_audit_find_only
   ```

4. For an authorized new custom role, the native syntax is, for example:

   ```sh
   atlas customDbRoles create central_audit_find_only \
     --privilege FIND@linux_audit.events \
     --projectId 6a6d28b8c775136a11684778
   ```

   Inspect first; do not recreate existing roles. Use `atlas dbusers create
   --help` for database users. Keep passwords out of command arguments,
   transcripts, audit EXECVE records, and repository files. Prefer an available
   connector with server-generated passwords or a protected API request body
   when the native CLI only offers a password argument. Provision service
   credentials through a hidden-input installer, not chat.
5. Verify the resulting user roles, cluster scope, and actual database
   authorization independently. CLI success does not prove runtime delivery.
6. Record the command boundary and result through Receipt River. Never log
   credentials. Keep permissions limited to the authorized project and task.

## Login and installation

A valid CLI login replaces repeated browser walkthroughs. Only open the browser
when authentication expires or a capability is unavailable through CLI/API.
For human-account authentication, run:

```sh
atlas auth login --authType UserAccount --noBrowser \
  --orgId 6a6d28b7c775136a11684708 \
  --projectId 6a6d28b8c775136a11684778 --output json
```

Use the verification URL and fresh code printed by that invocation. Follow the
active harness's confirmation rules. Verify `atlas auth whoami` afterward.
An installed binary does not establish a login on another machine. Never copy
CLI token/config files between hosts or into `~/.agents` synchronization.

For installation use the official `mongodb/mongodb-atlas-cli` release or the
official MongoDB package repository. Match OS/architecture and verify published
checksums. Prefer `~/.local/bin/atlas`; do not replace a working binary blindly.
Stealth and Yoga were verified with CLI 1.59.3 and separate UserAccount logins
on 2026-10-26. On these devices the authenticated profile belongs to `err`.
Local harness processes running as `err` can call `atlas` directly. From a
harness-specific SSH login, use the existing authorized owner context rather
than starting another browser login or copying tokens:

```sh
# From an audited Yoga harness login, for the user's authorized Atlas task:
sudo -n -u err -H /usr/local/bin/atlas auth whoami
sudo -n -u err -H /usr/local/bin/atlas dbusers list
```

On Stealth the owner-installed binary is `/home/err/.local/bin/atlas`.
This owner-context invocation preserves the SSH loginuid; it does not authorize
unrelated account administration or grant the runtime services admin access.
Never sign in a peer harness account or inspect the owner's credential store.

For unattended administration, a separately authorized Atlas service account
supports client ID/secret authentication. Do not introduce one merely to avoid
an interactive login. Administrative credentials manage Atlas configuration;
audit shipping and projection retain separate limited database credentials.

## Audit integration

See [agent-ssh-identity](../agent-ssh-identity/SKILL.md) before host operations.
The reader `central_audit_reader` uses `central_audit_find_only` (only `find` on
`linux_audit.events`, Cluster0). The shipper remains insert-only. A runtime
service must never receive Atlas administrative credentials.

## Official references

- https://www.mongodb.com/docs/atlas/cli/current/command/atlas-auth-login/
- https://www.mongodb.com/docs/atlas/cli/current/command/atlas-dbusers-create/
- https://www.mongodb.com/docs/atlas/cli/current/command/atlas-customdbroles-create/
- https://www.mongodb.com/docs/atlas/configure-api-access/

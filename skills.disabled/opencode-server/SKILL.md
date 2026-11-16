---
name: opencode-server
description: "Use the local OpenCode server running as a user systemd service to dispatch asynchronous agent sessions."
---

# Skill: OpenCode Server

## Goal
Start, inspect, connect to, and dispatch asynchronous agents through a headless OpenCode server that runs as a user systemd service.

## Use This Skill When
- You need to run an agent session without blocking the current session.
- You need to schedule periodic agent work.
- You need to connect to the local OpenCode server on this machine or another machine the user operates.
- Another skill (e.g., `skill-spore-review`) tells you to dispatch an async agent.

## Do Not Use This Skill When
- You are already inside an interactive OpenCode session and can finish the work inline.
- The target machine is not one the user operates or you have no access path to it.

## Service facts (this machine)
- Unit name: `opencode-server.service`
- Scope: `--user`
- Default command: `opencode serve --hostname 0.0.0.0 --port 8097`
- The password is stored in the unit file's `Environment=OPENCODE_SERVER_PASSWORD=...` directive.

## Verify the server
```bash
systemctl --user status opencode-server.service --no-pager
```

If it is not running:
```bash
systemctl --user start opencode-server.service
```

To restart after config changes:
```bash
systemctl --user daemon-reload
systemctl --user restart opencode-server.service
```

## Read the password safely
1. Read the unit file: `systemctl --user cat opencode-server.service`
2. Extract `OPENCODE_SERVER_PASSWORD` from the `Environment=` line.
3. Export it in the shell that will talk to the server:
   ```bash
   export OPENCODE_SERVER_PASSWORD="<password-from-unit-file>"
   ```
4. Never print the password, write it to a new file, or commit it.

## Coordinate with .eta-mu/actors/

For long-lived agents, use the `eta-mu-actor-agent` skill instead of raw dispatch.
The server is still the transport, but `.eta-mu/actors/` is the mailbox/coordinator.

- Create an actor: `~/.agents/skills/eta-mu-actor-agent/scripts/create-actor.sh`
- Dispatch an actor: `~/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh`
- Actor status: `~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh`

Every dispatched actor records its OpenCode session id under
`.eta-mu/actors/<actor-id>/sessions/<ts-uuid>/session.edn` so other actors can
find it and send messages back.

## Dispatch an asynchronous agent session (raw)

The simplest harness-agnostic method is the `opencode` CLI:

```bash
export OPENCODE_SERVER_PASSWORD="<password-from-unit-file>"
opencode run --attach http://127.0.0.1:8097 "<agent instructions as a single message>"
```

If you need a specific agent:

```bash
opencode run --attach http://127.0.0.1:8097 --agent <name> "<agent instructions>"
```

If you need the result as JSON:

```bash
opencode run --attach http://127.0.0.1:8097 --json "<agent instructions>"
```

## SDK method (for scripts)
1. Create a client pointing at the server base URL.
2. Add HTTP Basic Auth header `Authorization: Basic <base64(opencode:password)>`.
3. Create a session and call `session.promptAsync` with the instructions.
4. Subscribe to events and wait for completion.

Example using Bun/Node with `@opencode-ai/sdk`:

```typescript
import { createOpencodeClient } from "@opencode-ai/sdk"

const password = process.env.OPENCODE_SERVER_PASSWORD
const token = Buffer.from(`opencode:${password}`, "utf8").toString("base64")

const client = createOpencodeClient({
  baseUrl: "http://127.0.0.1:8097",
  headers: { Authorization: `Basic ${token}` },
})

const session = await client.session.create({
  body: { title: "async task" },
  query: { directory: process.cwd() },
})

await client.session.promptAsync({
  path: { id: session.data.id },
  body: {
    parts: [{ type: "text", text: "<agent instructions>" }],
  },
  query: { directory: process.cwd() },
})
```

## Other machines the user operates
1. SSH into the target machine.
2. Check the equivalent user systemd unit:
   ```bash
   systemctl --user cat opencode-server.service
   ```
3. Read the password from the unit file, export it, and dispatch with `opencode run --attach http://127.0.0.1:<port>`.
4. If the server is bound to `0.0.0.0`, you may also attach from the current machine using the target's LAN IP or Tailscale IP.

## Security rules
- Never commit the password.
- Never print the password in logs or assistant output.
- Never write the password to a new file unless explicitly asked and that file is in `.gitignore`.
- Prefer reading the password from the unit file at dispatch time rather than hardcoding it.

## Output
- A running server connection.
- A dispatched async session that completes independently.
- For `--json`, structured output containing the session result.

## Troubleshooting
- **Connection refused**: `systemctl --user start opencode-server.service`
- **401 Unauthorized**: `OPENCODE_SERVER_PASSWORD` is missing or wrong; re-read the unit file.
- **Port conflict**: Check the unit file for the actual `--port`; the default in this setup is `8097`.

## References
- `eta-mu-actor-agent` skill for the actor-model wrapper around this server.
- `skill-spore-review` skill for the periodic review actor that uses this server.
- OpenCode server docs: https://opencode.ai/docs/server/

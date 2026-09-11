---
name: eta-mu-actor-agent
description: "Dispatch and coordinate long-lived agent actors through the .eta-mu/actors/ mailbox. Use when the user says 'async agent', 'agent actor', or 'actor agent'."
triggers:
  - "async agent"
  - "agent actor"
  - "actor agent"
  - "eta-mu actor"
---

# Skill: ημ Actor Agent

## Goal
Treat background OpenCode agents as **equal actors** in an actor model. Each actor has a durable home under `.eta-mu/actors/<actor-id>/` that any agent can inspect, message, and continue. This skill does **not** replace normal sub-agents (the `task` tool); it is for a different shape of work.

## Actor vs. sub-agent

Use a **normal sub-agent** (`task` tool) when:
- The work fits inside the current interactive session.
- You want to isolate context or get a second opinion on a bounded problem.
- You need a direct result back in this session.

Use an **actor** when:
- The user says "async agent", "agent actor", or "actor agent".
- The work is large enough to need many parallel workers, long-running processing, or periodic scheduling.
- The agent must outlive this turn and be discoverable later.
- You want to fan out work to dozens or hundreds of background agents and have a deterministic way to know when they all finish.

Example actor use cases: digest a huge folder of markdown documents, run a long-running review pipeline, keep a periodic watcher alive, coordinate a swarm of file analyzers.

## Core idea
- An **actor** is a role with a purpose, prompt parts, and a mailbox.
- A **session** is one activation of that actor through OpenCode.
- One actor can have many sessions over time.
- Actors are **peers**. There is no master/sub-agent hierarchy.
- `.eta-mu/actors/` is the shared coordinator and mailbox root.

## Actor directory layout

Every actor lives at `.eta-mu/actors/<kebab-case-name>/`:

```
.eta-mu/actors/<actor>/
  actor.edn              # identity, purpose, runtime pointer
  AGENT.md               # compiled system prompt for this actor
  goals/                 # *.md prompt fragments: what the actor wants
  methods/               # *.md prompt fragments: how it operates
  responsibilities/      # *.md prompt fragments: constraints it must honor
  schedules/             # *.md trigger/schedule definitions
  triggers/              # *.md invocation conditions
  runtime/               # how this actor is backgrounded
    runner.sh            # one-shot dispatch
    poll-inbox.sh        # long-lived inbox watcher
    tmux-start.sh        # start a tmux-backed long-lived actor
    tmux-attach.sh       # attach to that tmux session
    systemd.service      # optional
    systemd.timer        # optional
    cron.example         # optional
  sessions/              # one folder per activation
    <iso-ts>-<uuid>/
      session.edn        # opencode session id, status, artifacts
      turn-001-in.md
      opencode-run.log
  inbox/                 # messages waiting for this actor
  outbox/                # messages this actor has emitted
```

### actor.edn (required)

```edn
{:actor/id "spore-reviewer"
 :actor/name "Skill Spore Reviewer"
 :actor/purpose "Periodically review incubated skill spores and promote the worthy ones."
 :actor/created-at "2026-06-26T00:00:00Z"
 :actor/runtime {:type :systemd-timer
                  :unit "skill-spore-reviewer.service"
                  :timer "skill-spore-review.timer"
                  :runner "~/.eta-mu/actors/spore-reviewer/runtime/runner.sh"
                  :manual "~/.eta-mu/actors/spore-reviewer/runtime/tmux-start.sh"
                  :automated {:type :systemd-timer
                              :timer "skill-spore-review.timer"
                              :service "skill-spore-reviewer.service"
                              :interval "6h"
                              :install "systemctl --user enable skill-spore-review.timer && systemctl --user start skill-spore-review.timer"}}
 :actor/inbox-path ".eta-mu/actors/spore-reviewer/inbox"
 :actor/outbox-path ".eta-mu/actors/spore-reviewer/outbox"
 :actor/sessions-path ".eta-mu/actors/spore-reviewer/sessions"}
```

### Prompt compilation

`AGENT.md` is **compiled**, not hand-written. It concatenates `actor.edn`, `goals/`, `methods/`, `responsibilities/`, `schedules/`, and `triggers/`.

Run:

```bash
~/.agents/skills/eta-mu-actor-agent/scripts/compile-prompt.sh <actor-id>
```

## Scripts (thin wrappers)

All scripts live in `~/.agents/skills/eta-mu-actor-agent/scripts/`:

| Script | Purpose |
|--------|---------|
| `create-actor.sh <actor-id> <purpose>` | Create a new actor folder skeleton. |
| `compile-prompt.sh <actor-id>` | Compile `AGENT.md` from prompt parts. |
| `dispatch-actor.sh <actor-id> [message]` | Start a new OpenCode session for this actor and record it. Returns immediately. |
| `poll-inbox.sh <actor-id> <session-dir>` | Watch the inbox and dispatch continuation turns for new messages. |
| `actor-status.sh [actor-id]` | Show sessions, inbox count, outbox count, runtime. |
| `send-to-inbox.sh <actor-id> <message-file>` | Drop a message into the actor's inbox. |
| `actor-tmux-attach.sh <actor-id>` | Attach to a tmux-backed actor (detach with Ctrl-b d). |

## Dispatch flow

1. Ensure the actor exists under `.eta-mu/actors/<actor-id>/`.
2. Run `compile-prompt.sh <actor-id>` so `AGENT.md` is current.
3. Run `dispatch-actor.sh <actor-id>`.
   - Creates a session folder under `sessions/`.
   - Installs `AGENT.md` as an OpenCode agent at `~/.config/opencode/agent/<actor-id>.md`.
   - Reads the password from `opencode-server.service`.
   - Calls `opencode run --attach http://127.0.0.1:8097 --agent <actor-id>`.
   - Returns immediately; the OpenCode server owns the session.
   - Writes the OpenCode session id to `sessions/<ts-uuid>/session.edn`.
4. If the actor should be long-lived, start `runtime/poll-inbox.sh` (in tmux, systemd, pm2, etc.).
5. The actor writes outgoing messages to its `outbox/`.
6. Other actors can drop messages into its `inbox/`; the watcher will dispatch continuation turns.

## Long-lived actors

An actor can be:

- **One-shot**: `dispatch-actor.sh` starts it, it does one job, and it exits. Good for a scheduled review pass.
- **Long-lived**: `runtime/poll-inbox.sh` runs forever, dispatching a continuation turn for each new inbox message. Good for a mailbox-style worker.
- **Swarm coordinator**: one actor fans out many one-shot sub-tasks and records their session ids so it (or another actor) can check completion.

To start a long-lived tmux actor:

```bash
~/.eta-mu/actors/<actor-id>/runtime/tmux-start.sh
```

To attach:

```bash
~/.agents/skills/eta-mu-actor-agent/scripts/actor-tmux-attach.sh <actor-id>
```

## Actor-to-actor messaging

To send a message from actor A to actor B:

```bash
~/.agents/skills/eta-mu-actor-agent/scripts/send-to-inbox.sh \
  <actor-b-id> \
  <message-file.md>
```

A message file should have this frontmatter:

```markdown
---
from: <actor-a-id>
to: <actor-b-id>
session: <opencode-session-id or none>
kind: request | response | event | command
reply-to: <path in actor-a outbox or none>
---

<body>
```

## Swarm / bulk work pattern

For "very large requests" (e.g. digest a huge folder of markdown):

1. Create one actor per document, or one actor per batch.
2. Dispatch each actor with a message pointing at its assigned work.
3. Each actor writes its result to its own `outbox/` and, optionally, sends a completion message to a coordinator actor's `inbox/`.
4. The coordinator actor (or a deterministic script) reads all session statuses and outboxes and reports: "N finished, M failed, results are here."

## Runtime options

The skill does not enforce a single backgrounding mechanism. The actor's `runtime/` folder documents the actual mechanism. Common patterns:

- **systemd timer**: periodic one-shot review passes.
- **systemd path**: file watcher that triggers on changes.
- **tmux**: manual long-lived worker.
- **pm2**: managed long-lived worker.
- **cron**: periodic one-shot.

## Automated dispatch contracts

Every actor that should run without human invocation MUST declare its automated dispatch contract in `:actor/runtime :automated`. The contract names the trigger, the install command, and the unit files. Do not rely on manual dispatch for scheduled or file-driven work.

### systemd path unit

Use when the actor should react to filesystem changes.

`actor.edn`:

```edn
{:actor/runtime {:type :long-lived
                  :automated {:type :systemd-path
                              :path "notes-organizer.path"
                              :service "notes-organizer.service"
                              :watched "~/docs/notes"
                              :install "systemctl --user enable notes-organizer.path && systemctl --user start notes-organizer.path"}}}
```

`runtime/systemd.path`:

```ini
[Unit]
Description=Watch docs/notes for notes-organizer

[Path]
PathChanged=%h/docs/notes
PathModified=%h/docs/notes

[Install]
WantedBy=default.target
```

`runtime/systemd.service`:

```ini
[Unit]
Description=Dispatch notes-organizer on docs/notes change
After=network-online.target opencode-server.service

[Service]
Type=oneshot
ExecStart=%h/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh notes-organizer "File change detected under docs/notes. Organize new timestamped notes and review large notes."
Environment="HOME=%h"
Environment="USER=%u"
Environment="PATH=%h/.bun/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

[Install]
WantedBy=default.target
```

Install:

```bash
systemctl --user enable notes-organizer.path
systemctl --user start notes-organizer.path
```

### systemd timer

Use when the actor should run on a schedule.

`actor.edn`:

```edn
{:actor/runtime {:type :long-lived
                  :automated {:type :systemd-timer
                              :timer "receipt-river-tender.timer"
                              :service "receipt-river-tender.service"
                              :interval "30m"
                              :cron "runtime/cron.example"
                              :install "systemctl --user enable receipt-river-tender.timer && systemctl --user start receipt-river-tender.timer"}}}
```

`runtime/systemd.timer`:

```ini
[Unit]
Description=Run receipt river tender every 30 minutes

[Timer]
OnBootSec=5min
OnUnitActiveSec=30min
Persistent=true

[Install]
WantedBy=timers.target
```

`runtime/systemd.service`:

```ini
[Unit]
Description=Receipt river tender
After=network-online.target opencode-server.service

[Service]
Type=oneshot
ExecStart=%h/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh receipt-river-tender "Scan for missing receipts and nag the responsible actors."
Environment="HOME=%h"
Environment="USER=%u"
Environment="PATH=%h/.bun/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

[Install]
WantedBy=default.target
```

Install:

```bash
systemctl --user enable receipt-river-tender.timer
systemctl --user start receipt-river-tender.timer
```

### cron fallback

For hosts without systemd user services, provide `runtime/cron.example`:

```text
# Cron example for receipt-river-tender
# Add to crontab with `crontab -e`:
# */30 * * * * /home/err/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh receipt-river-tender "Scan for missing receipts and nag the responsible actors."
```

### Common rules

- All automated units depend on `opencode-server.service` or equivalent.
- Use `Type=oneshot` and `dispatch-actor.sh`; never block the unit.
- Pass environment variables `HOME`, `USER`, and `PATH` explicitly.
- Keep unit file names identical to the actor id so `actor-status.sh` can parse them.

Whatever the mechanism, `actor.edn` must point to it, and a human or another actor must be able to discover it from the folder.

## Rules

- Never call an actor a "sub-agent".
- Never delete another actor's sessions, inbox, or outbox without explicit approval.
- Always record the OpenCode session id when dispatching.
- Always compile `AGENT.md` before dispatch so the actor runs from current prompt parts.
- Keep secrets (passwords, tokens) out of actor folders. Read them from the unit file or environment at dispatch time.

## Output
- A new or updated actor folder under `.eta-mu/actors/<actor-id>/`.
- A recorded session under `.eta-mu/actors/<actor-id>/sessions/<ts-uuid>/`.
- Optional inbox/outbox messages.

## End-to-end example: bulk document digest

Create an actor:

```bash
~/.agents/skills/eta-mu-actor-agent/scripts/create-actor.sh \
  doc-digest \
  "Read a directory of markdown files and emit a concise summary per file"
```

Add prompt fragments to `.eta-mu/actors/doc-digest/goals/` and `.eta-mu/actors/doc-digest/methods/`.

Compile:

```bash
~/.agents/skills/eta-mu-actor-agent/scripts/compile-prompt.sh doc-digest
```

Dispatch for each batch:

```bash
for batch in docs/batch-*; do
  ~/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh \
    doc-digest \
    "Summarize every markdown file under $batch and write one summary file per input to .eta-mu/actors/doc-digest/outbox/"
done
```

Check progress:

```bash
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh doc-digest
```

Each session folder records the OpenCode session id, so you can later inspect results in `outbox/` and `sessions/`.

## References
- `opencode-server` skill for the headless server and password handling.
- `skill-spore-review` skill for the first concrete actor (`spore-reviewer`).
- `receipt-river` skill for append-only execution logs.

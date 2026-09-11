#!/usr/bin/env bash
set -euo pipefail

# create-actor.sh <actor-id> <one-line-purpose>
# Creates a new actor skeleton under .eta-mu/actors/<actor-id>/.

ACTOR_ID="${1:-}"
PURPOSE="${2:-}"

if [[ -z "$ACTOR_ID" || -z "$PURPOSE" ]]; then
  echo "Usage: $0 <actor-id> <one-line-purpose>" >&2
  exit 1
fi

# Resolve workspace root by walking up from current directory.
WORKSPACE_ROOT="$(pwd)"
while [[ "$WORKSPACE_ROOT" != "/" && ! -d "$WORKSPACE_ROOT/.eta-mu/actors" ]]; do
  WORKSPACE_ROOT="$(dirname "$WORKSPACE_ROOT")"
done
if [[ "$WORKSPACE_ROOT" == "/" ]]; then
  # If not inside a workspace, use current directory and create the tree there.
  WORKSPACE_ROOT="$(pwd)"
fi

ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
if [[ -d "$ACTOR_DIR" ]]; then
  echo "Actor already exists: $ACTOR_DIR" >&2
  exit 1
fi

TS=$(date -u +%Y-%m-%dT%H:%M:%SZ)
mkdir -p "$ACTOR_DIR"/{goals,methods,responsibilities,schedules,triggers,runtime,sessions,inbox,outbox}

SKILL_SCRIPTS="$WORKSPACE_ROOT/.agents/skills/eta-mu-actor-agent/scripts"
if [[ ! -d "$SKILL_SCRIPTS" ]]; then
  # Fallback to the script's own directory.
  SKILL_SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fi

cat > "$ACTOR_DIR/actor.edn" <<EOF
{:actor/id "$ACTOR_ID"
 :actor/name "$ACTOR_ID"
 :actor/purpose "$PURPOSE"
 :actor/created-at "$TS"
 :actor/runtime {:type :one-shot
                 :command "$SKILL_SCRIPTS/dispatch-actor.sh $ACTOR_ID"}
 :actor/inbox-path ".eta-mu/actors/$ACTOR_ID/inbox"
 :actor/outbox-path ".eta-mu/actors/$ACTOR_ID/outbox"
 :actor/sessions-path ".eta-mu/actors/$ACTOR_ID/sessions"}
EOF

cat > "$ACTOR_DIR/goals/README.md" <<EOF
# Goals for $ACTOR_ID

Add one goal per file. Each file should be a short prompt fragment describing something this actor wants to achieve.
EOF

cat > "$ACTOR_DIR/methods/README.md" <<EOF
# Methods for $ACTOR_ID

Add one method per file. Each file should be a short prompt fragment describing how this actor operates.
EOF

cat > "$ACTOR_DIR/responsibilities/README.md" <<EOF
# Responsibilities for $ACTOR_ID

Add one responsibility per file. Each file should be a constraint or invariant this actor must honor.
EOF

cat > "$ACTOR_DIR/schedules/README.md" <<EOF
# Schedules for $ACTOR_ID

Add schedule definitions here, e.g. \`every-6h.md\`, \`on-boot.md\`.
EOF

cat > "$ACTOR_DIR/triggers/README.md" <<EOF
# Triggers for $ACTOR_ID

Add trigger definitions here, e.g. \`user-request.md\`, \`timer-fired.md\`.
EOF

cat > "$ACTOR_DIR/runtime/runner.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
# Default runner: dispatch one session and exit.
# Replace this with tmux/pm2/systemd/cron wiring as needed.
exec "$SKILL_SCRIPTS/dispatch-actor.sh" "$ACTOR_ID" "\$@"
EOF
chmod +x "$ACTOR_DIR/runtime/runner.sh"

cat > "$ACTOR_DIR/runtime/poll-inbox.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
# Polls inbox for this actor. Run this in tmux/screen/systemd for a long-lived actor.
# Usage: $ACTOR_DIR/runtime/poll-inbox.sh <session-dir>
exec "$SKILL_SCRIPTS/poll-inbox.sh" "$ACTOR_ID" "${1:-}"
EOF
chmod +x "$ACTOR_DIR/runtime/poll-inbox.sh"

cat > "$ACTOR_DIR/runtime/tmux-start.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
# Starts a long-lived tmux session for $ACTOR_ID that polls its inbox.
SESSION="$ACTOR_ID"
if tmux has-session -t "\$SESSION" 2>/dev/null; then
  echo "tmux session '\$SESSION' already exists."
  exit 0
fi
# First dispatch creates the session folder and OpenCode session.
"$SKILL_SCRIPTS/dispatch-actor.sh" "$ACTOR_ID" "Start polling your inbox for work."
# Find the most recent session folder.
LATEST_SESSION=\$(find "$ACTOR_DIR/sessions" -maxdepth 1 -mindepth 1 -type d | sort | tail -n1)
if [[ -z "\$LATEST_SESSION" ]]; then
  echo "No session folder found." >&2
  exit 1
fi
# Attach a watcher to that session.
tmux new-session -d -s "\$SESSION" -n main "$ACTOR_DIR/runtime/poll-inbox.sh" "\$LATEST_SESSION"
echo "Started tmux session: \$SESSION"
EOF
chmod +x "$ACTOR_DIR/runtime/tmux-start.sh"

cat > "$ACTOR_DIR/runtime/tmux-attach.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
exec "$SKILL_SCRIPTS/actor-tmux-attach.sh" "$ACTOR_ID"
EOF
chmod +x "$ACTOR_DIR/runtime/tmux-attach.sh"

echo "Created actor: $ACTOR_DIR"

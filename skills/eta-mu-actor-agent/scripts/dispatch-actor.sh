#!/usr/bin/env bash
set -euo pipefail

# dispatch-actor.sh <actor-id> [message]
# Starts a new OpenCode session for the actor and records it under sessions/.
# The actor process itself decides whether to exit after one job or poll its inbox.

ACTOR_ID="${1:-}"
MESSAGE="${2:-}"

if [[ -z "$ACTOR_ID" ]]; then
  echo "Usage: $0 <actor-id> [message]" >&2
  exit 1
fi

# Resolve workspace root. The script can be run from anywhere inside the workspace.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Find workspace root by walking up until we see .eta-mu/actors.
WORKSPACE_ROOT="$(pwd)"
while [[ "$WORKSPACE_ROOT" != "/" && ! -d "$WORKSPACE_ROOT/.eta-mu/actors" ]]; do
  WORKSPACE_ROOT="$(dirname "$WORKSPACE_ROOT")"
done
if [[ "$WORKSPACE_ROOT" == "/" ]]; then
  echo "Could not find workspace root containing .eta-mu/actors/" >&2
  exit 1
fi
cd "$WORKSPACE_ROOT"

ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
if [[ ! -d "$ACTOR_DIR" ]]; then
  echo "Actor not found: $ACTOR_DIR" >&2
  exit 1
fi

"$SCRIPT_DIR/compile-prompt.sh" "$ACTOR_ID"

SESSION_UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
TS=$(date -u +%Y-%m-%dT%H-%M-%S)
SESSION_DIR="$ACTOR_DIR/sessions/$TS-$SESSION_UUID"
mkdir -p "$SESSION_DIR"

# Capture environment and invocation metadata.
PAYLOAD_MSG_FILE=""
if [[ -n "$MESSAGE" ]]; then
  PAYLOAD_MSG_FILE="$SESSION_DIR/turn-001-in.md"
  cat > "$PAYLOAD_MSG_FILE" <<EOF
---
from: user
to: $ACTOR_ID
session: $SESSION_UUID
kind: command
---

$MESSAGE
EOF
  # Put the message in the inbox as well so the actor can find it later.
  cp "$PAYLOAD_MSG_FILE" "$ACTOR_DIR/inbox/$TS-$SESSION_UUID.md"
fi

cat > "$SESSION_DIR/session.edn" <<EOF
{:session/id "$SESSION_UUID"
 :session/created-at "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
 :session/actor-id "$ACTOR_ID"
 :session/status :running
 :session/opencode-session-id nil
 :session/directory "$WORKSPACE_ROOT"
 :session/message-file "${PAYLOAD_MSG_FILE:-}"}
EOF

# Install the compiled prompt as an OpenCode agent so --agent can load it.
AGENT_INSTALL_DIR="$HOME/.config/opencode/agent"
mkdir -p "$AGENT_INSTALL_DIR"
cp "$ACTOR_DIR/AGENT.md" "$AGENT_INSTALL_DIR/$ACTOR_ID.md"

# Read password from the systemd unit file via the helper.
PASSWORD=$("$SCRIPT_DIR/read-server-password.sh" opencode-server.service)
export OPENCODE_SERVER_PASSWORD="$PASSWORD"

# Build the instruction payload.
if [[ -n "$MESSAGE" ]]; then
  PAYLOAD="Your session folder is $SESSION_DIR. Your actor inbox is at $ACTOR_DIR/inbox/. There is a command message waiting at $ACTOR_DIR/inbox/$TS-$SESSION_UUID.md . $MESSAGE"
else
  PAYLOAD="Your session folder is $SESSION_DIR. Your actor inbox is at $ACTOR_DIR/inbox/. Check for unprocessed messages. Decide whether to handle one job and exit, or to keep polling for more work, based on your actor runtime contract."
fi

export OPENCODE_SERVER_PASSWORD="$PASSWORD"

# Dispatch. We do NOT wait here. The OpenCode server owns the session.
OPENCODE_OUT="$SESSION_DIR/opencode-run.log"
nohup opencode run \
  --attach http://127.0.0.1:8097 \
  --agent "$ACTOR_ID" \
  "$PAYLOAD" > "$OPENCODE_OUT" 2>&1 &
DISPATCH_PID=$!

# Give the server a moment to create the session, then try to capture its id.
sleep 1
OC_SESSION_ID=""
for _ in {1..10}; do
  OC_SESSION_ID=$(grep -oE '[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}' "$OPENCODE_OUT" 2>/dev/null | head -n1 || true)
  if [[ -n "$OC_SESSION_ID" ]]; then
    break
  fi
  sleep 1
done
OC_SESSION_ID="${OC_SESSION_ID:-unknown}"

# Update session.edn with the opencode session id.
{
  echo "{:session/id \"$SESSION_UUID\""
  echo " :session/created-at \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\""
  echo " :session/actor-id \"$ACTOR_ID\""
  echo " :session/status :running"
  echo " :session/opencode-session-id \"$OC_SESSION_ID\""
  echo " :session/directory \"$WORKSPACE_ROOT\""
  echo " :session/message-file \"${PAYLOAD_MSG_FILE:-}\""
  echo " :session/dispatch-pid $DISPATCH_PID"
  echo " :session/opencode-run-log \"$OPENCODE_OUT\"}"
} > "$SESSION_DIR/session.edn"

echo "Dispatched actor: $ACTOR_ID"
echo "Session folder:   $SESSION_DIR"
echo "OpenCode session: $OC_SESSION_ID"
echo "Dispatch PID:     $DISPATCH_PID"

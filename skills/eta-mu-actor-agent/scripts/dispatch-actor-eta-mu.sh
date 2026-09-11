#!/usr/bin/env bash
set -euo pipefail

# dispatch-actor-eta-mu.sh <actor-id> [message]
# Dispatches an actor using eta-mu (pi) in non-interactive print mode.
# Records the session under the actor's sessions/ directory.

ACTOR_ID="${1:-}"
MESSAGE="${2:-}"

if [[ -z "$ACTOR_ID" ]]; then
  echo "Usage: $0 <actor-id> [message]" >&2
  exit 1
fi

# Resolve workspace root.
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

# Compile the AGENT.md from prompt fragments.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$SCRIPT_DIR/compile-prompt.sh" "$ACTOR_ID"

# Create session directory.
SESSION_UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
TS=$(date -u +%Y-%m-%dT%H-%M-%S)
SESSION_DIR="$ACTOR_DIR/sessions/$TS-$SESSION_UUID"
mkdir -p "$SESSION_DIR"

# Record incoming message if provided.
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
  cp "$PAYLOAD_MSG_FILE" "$ACTOR_DIR/inbox/$TS-$SESSION_UUID.md"
fi

# Build session metadata.
cat > "$SESSION_DIR/session.edn" <<EOF
{:session/id "$SESSION_UUID"
 :session/created-at "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
 :session/actor-id "$ACTOR_ID"
 :session/status :running
 :session/dispatch :eta-mu
 :session/directory "$WORKSPACE_ROOT"
 :session/message-file "${PAYLOAD_MSG_FILE:-}"}
EOF

# Build the prompt: actor identity + message.
PROMPT="Your session folder is $SESSION_DIR. Your actor inbox is at $ACTOR_DIR/inbox/."
if [[ -n "$MESSAGE" ]]; then
  PROMPT="$PROMPT There is a command message at $ACTOR_DIR/inbox/$TS-$SESSION_UUID.md. $MESSAGE"
else
  PROMPT="$PROMPT Check for unprocessed messages. Decide whether to handle one job and exit, or keep polling for more work, based on your actor runtime contract."
fi

# Dispatch via eta-mu in print (non-interactive) mode.
# Source workspace env if present (API keys).
if [[ -f "$WORKSPACE_ROOT/.eta-mu/.env" ]]; then
  while IFS='=' read -r key val; do
    [[ -z "$key" || "$key" == \#* ]] && continue
    export "$key=$val"
  done < "$WORKSPACE_ROOT/.eta-mu/.env"
fi

ETA_MU_OUT="$SESSION_DIR/eta-mu-run.log"

nohup eta-mu \
  -p \
  --no-session \
  ${ETA_MU_FLAGS:-} \
  --append-system-prompt "$ACTOR_DIR/AGENT.md" \
  "$PROMPT" > "$ETA_MU_OUT" 2>&1 &
DISPATCH_PID=$!

# Update session.edn with dispatch pid.
cat > "$SESSION_DIR/session.edn" <<EOF
{:session/id "$SESSION_UUID"
 :session/created-at "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
 :session/actor-id "$ACTOR_ID"
 :session/status :running
 :session/dispatch :eta-mu
 :session/dispatch-pid $DISPATCH_PID
 :session/directory "$WORKSPACE_ROOT"
 :session/message-file "${PAYLOAD_MSG_FILE:-}"
 :session/eta-mu-run-log "$ETA_MU_OUT"}
EOF

echo "Dispatched actor: $ACTOR_ID (eta-mu)"
echo "Session folder:   $SESSION_DIR"
echo "Dispatch PID:     $DISPATCH_PID"

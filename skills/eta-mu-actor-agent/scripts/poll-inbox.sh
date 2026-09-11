#!/usr/bin/env bash
set -euo pipefail

# poll-inbox.sh <actor-id> <session-dir>
# Polls the actor inbox and dispatches an OpenCode continuation for each message.
# This is a lightweight watcher. For production use, prefer inotify/systemd socket activation.

ACTOR_ID="${1:-}"
SESSION_DIR="${2:-}"

if [[ -z "$ACTOR_ID" || -z "$SESSION_DIR" ]]; then
  echo "Usage: $0 <actor-id> <session-dir>" >&2
  exit 1
fi

ACTOR_DIR="$(cd "$(dirname "$SESSION_DIR")/.." && pwd)"
INBOX="$ACTOR_DIR/inbox"
OUTBOX="$ACTOR_DIR/outbox"
mkdir -p "$INBOX" "$OUTBOX"

# Directory where we move messages we have handed off.
SEEN_DIR="$SESSION_DIR/seen-inbox"
mkdir -p "$SEEN_DIR"

# Read password once.
PASSWORD=$("$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/read-server-password.sh" opencode-server.service)
export OPENCODE_SERVER_PASSWORD="$PASSWORD"

while true; do
  # Process inbox messages in timestamp order.
  find "$INBOX" -maxdepth 1 -type f -name '*.md' | sort | while read -r msg; do
    msg_id=$(basename "$msg")
    if [[ -f "$SEEN_DIR/$msg_id" ]]; then
      continue
    fi

    # Mark as seen immediately to avoid duplicate dispatch.
    touch "$SEEN_DIR/$msg_id"

    # Dispatch a continuation turn in the same OpenCode session.
    # We use --continue and the opencode session id if available.
    oc_session=$(grep -E ':session/opencode-session-id' "$SESSION_DIR/session.edn" | sed 's/.*:session\/opencode-session-id "\([^"]*\)".*/\1/' || true)
    if [[ -n "$oc_session" && "$oc_session" != "unknown" ]]; then
      nohup opencode run \
        --attach http://127.0.0.1:8097 \
        --agent "$ACTOR_ID" \
        --session "$oc_session" \
        "New message arrived in your inbox at $msg . Process it." > "$SESSION_DIR/opencode-run-$msg_id.log" 2>&1 &
    else
      nohup opencode run \
        --attach http://127.0.0.1:8097 \
        --agent "$ACTOR_ID" \
        "New message arrived in your inbox at $msg . Process it." > "$SESSION_DIR/opencode-run-$msg_id.log" 2>&1 &
    fi
  done

  sleep 5
done

#!/usr/bin/env bash
set -euo pipefail

# actor-tmux-attach.sh <actor-id>
# Attaches to a tmux-backed actor session whose name matches the actor id.

ACTOR_ID="${1:-}"
if [[ -z "$ACTOR_ID" ]]; then
  echo "Usage: $0 <actor-id>" >&2
  exit 1
fi

WORKSPACE_ROOT="$(pwd)"
while [[ "$WORKSPACE_ROOT" != "/" && ! -d "$WORKSPACE_ROOT/.eta-mu/actors" ]]; do
  WORKSPACE_ROOT="$(dirname "$WORKSPACE_ROOT")"
done
if [[ "$WORKSPACE_ROOT" == "/" ]]; then
  echo "Could not find workspace root containing .eta-mu/actors/" >&2
  exit 1
fi

ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
if [[ ! -d "$ACTOR_DIR" ]]; then
  echo "Actor not found: $ACTOR_DIR" >&2
  exit 1
fi

# The tmux session name is the actor id by convention.
SESSION="$ACTOR_ID"

if ! tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "No tmux session named '$SESSION' is running. Start it with: $ACTOR_DIR/runtime/tmux-start.sh" >&2
  exit 1
fi

# Note: exec replaces this process with tmux, so the agent can interact.
exec tmux attach -t "$SESSION"

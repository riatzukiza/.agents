#!/usr/bin/env bash
set -euo pipefail

# read-server-password.sh
# Reads OPENCODE_SERVER_PASSWORD from the user systemd unit or its EnvironmentFile.
# Prints the password on stdout. Caller should export it.

UNIT_NAME="${1:-opencode-server.service}"

UNIT_FILE=$(systemctl --user cat "$UNIT_NAME" 2>/dev/null || true)
if [[ -z "$UNIT_FILE" ]]; then
  echo "Could not read unit file: $UNIT_NAME" >&2
  exit 1
fi

PASSWORD=""

# Try direct Environment= lines.
PASSWORD=$(echo "$UNIT_FILE" | sed -n "s/^[[:space:]]*Environment=[\"']*OPENCODE_SERVER_PASSWORD=[\"']*//p" | sed "s/[\"'[:space:]].*$//" | tail -n1)

# Try EnvironmentFile=.
if [[ -z "$PASSWORD" ]]; then
  ENV_FILE=$(echo "$UNIT_FILE" | sed -n 's/^[[:space:]]*EnvironmentFile=-*//p' | tail -n1)
  # Resolve systemd specifiers: %h -> $HOME, %u -> $USER
  ENV_FILE="${ENV_FILE//%h/$HOME}"
  ENV_FILE="${ENV_FILE//%u/$USER}"
  if [[ -n "$ENV_FILE" && -f "$ENV_FILE" ]]; then
    PASSWORD=$(grep -E '^OPENCODE_SERVER_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | tail -n1 | sed "s/^[[:space:]]*[\"']*//;s/[\"']*[[:space:]]*$//")
  fi
fi

if [[ -z "$PASSWORD" ]]; then
  echo "Could not read OPENCODE_SERVER_PASSWORD from $UNIT_NAME" >&2
  exit 1
fi

echo "$PASSWORD"

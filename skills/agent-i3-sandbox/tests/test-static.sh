#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
bash -n scripts/agentctl scripts/agent-entrypoint scripts/smoke-i3 scripts/test-i3-regression scripts/test-espanso-regression scripts/install.sh
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
docker compose config >"$tmp/compose.yml"
if grep -RnE 'container_name:|image:|/home/err|/tmp/.X11-unix|/run/user|DBUS' docker-compose*.yml scripts; then
  echo 'unexpected global identity or host resource reference' >&2
  exit 1
fi
grep -nE '^USER agent:agent$' Dockerfile
grep -RnE 'TARGETARCH.*amd64|read_only: true|EMACS_DAEMON_NAME|espanso --help' Dockerfile docker-compose.optional.yml scripts
[[ -f emacs/init.el && -f spacemacs/private/agent-sandbox/packages.el ]]
! grep -Eq '^  - trigger: ":sandbox-unicode-[^"]+"$' espanso/match/base.yml
grep -Fq ':sandbox-keys' espanso/match/base.yml
echo 'static sandbox checks passed'
